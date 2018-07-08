package de.digitalcollections.lucene.analysis.payloads;

import com.google.common.collect.Lists;
import com.google.common.math.IntMath;
import org.apache.lucene.analysis.payloads.AbstractEncoder;
import org.apache.lucene.analysis.payloads.PayloadEncoder;
import org.apache.lucene.analysis.util.ResourceLoader;
import org.apache.lucene.util.BytesRef;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PayloadSchema extends AbstractEncoder implements PayloadEncoder {
  private static final Pattern PAYLOAD_PAT = Pattern.compile(
      "(.+?):([^,\\[\\]]+?|\\[.+?\\]),?");

  private final LinkedHashMap<String, FieldDefinition> fields;

  public PayloadSchema(ResourceLoader loader, String schemaFileName) throws IOException {
    this.fields = parseSchema(loader.openResource(schemaFileName));
  }

  private LinkedHashMap<String, FieldDefinition> parseSchema(InputStream is) {
    Yaml yml = new Yaml(new SafeConstructor());
    LinkedHashMap<String, FieldDefinition> fields = new LinkedHashMap<>();
    LinkedHashMap<String, LinkedHashMap<String, Object>> schema = yml.load(is);
    for (Map.Entry<String, LinkedHashMap<String, Object>> entry : schema.entrySet()) {
      if (!(entry.getValue().containsKey("key"))) {
        throw new RuntimeException(String.format("Configuration for field '%s' does not have a 'key'!", entry.getKey()));
      }
      String key = (String) entry.getValue().get("key");
      if (!(entry.getValue().containsKey("type"))) {
        throw new RuntimeException(String.format("Configuration for field '%s' does not have a 'type'!", entry.getKey()));
      }
      FieldDefinition fieldDef;
      String type = (String) entry.getValue().get("type");
      if ("int".equals(type) || "float".equals(type)) {
        int numBits = (int) entry.getValue().get("bits");
        boolean isSigned = (boolean) entry.getValue().getOrDefault("signed", false);
        fieldDef = new NumberFieldDefinition(key, numBits, isSigned, "float".equals(type));
      } else if ("bool".equals(type)) {
        fieldDef = new BoolFieldDefinition(key);
      } else if ("set".equals(type)) {
        ArrayList<String> values = (ArrayList<String>) entry.getValue().get("values");
        fieldDef = new BitSetFieldDefinition(key, new LinkedHashSet<>(values));
      } else {
        throw new RuntimeException(String.format("Unknown field type '%s'.", type));
      }
      fields.put(entry.getKey(), fieldDef);
    }
    return fields;
  }

  public PayloadStruct parseStringPayload(String payload) {
    Matcher m = PAYLOAD_PAT.matcher(payload);
    Map<String, String> params = new HashMap<>();
    while (m.find()) {
      String key = m.group(1);
      String val = m.group(2);
      params.put(key, val);
    }

    PayloadStruct struct = new PayloadStruct(this);
    for (Map.Entry<String, FieldDefinition> entry : this.fields.entrySet()) {
      FieldDefinition def = entry.getValue();
      String field = entry.getKey();
      String key = def.getKey();
      if (!params.containsKey(key)) {
        throw new IllegalArgumentException(String.format(
            "Key '%s' is missing from payload string '%s'", key, payload));
      }
      String val = params.get(key);
      if (def instanceof NumberFieldDefinition) {
        NumberFieldDefinition numDef = (NumberFieldDefinition) def;
        if (numDef.isFloatingPoint()) {
          struct.setFloat(field, Double.parseDouble(val));
        } else {
          struct.setInt(field, Integer.parseInt(val));
        }
      } else if (def instanceof BoolFieldDefinition) {
        struct.setBool(
            field,
            (val.equals("true") || val.equals("yes") || val.equals("1")));
      } else if (def instanceof BitSetFieldDefinition) {
        if (!val.startsWith("[") || val.endsWith("]")) {
          throw new IllegalArgumentException("Value is not a valid set: " + val);
        }
        BitSetFieldDefinition setDef = (BitSetFieldDefinition) def;
        Set<String> vals = new HashSet<>();
        Set<String> allVals = setDef.getValues();
        for (String v : val.substring(1, val.length() - 1).split(",")) {
          if (!allVals.contains(v)) {
            throw new IllegalArgumentException(String.format(
                "Unknown value '%s' in set field '%s', legal values are [%s]",
                v, field, allVals.stream().collect(Collectors.joining(","))));
          }
          vals.add(v);
        }
        struct.setSet(field, vals);
      }
    }
    return struct;
  }

  public PayloadStruct parseBytePayload(BytesRef data) {
    int numBytes = (int) Math.ceil((double) this.getSize() / 8.0);
    if (data.length != this.getSize()) {
      throw new IllegalArgumentException(String.format(
          "Bad payload size, expected %d bytes, but got %d bytes", this.getSize(), numBytes));
    }

    PayloadStruct struct = new PayloadStruct(this);
    BigInteger encoded = new BigInteger(Arrays.copyOfRange(data.bytes, data.offset, data.offset + data.length));
    int shift = 0;
    for (Map.Entry<String, FieldDefinition> entry : this.fields.entrySet()) {
      String fieldName = entry.getKey();
      FieldDefinition def = entry.getValue();
      BigInteger mask = BigInteger.valueOf(IntMath.pow(2, def.getNumBits()));
      BigInteger codedVal = encoded.shiftRight(shift).and(mask);
      if (def instanceof NumberFieldDefinition) {
        NumberFieldDefinition numDef = (NumberFieldDefinition) def;
        if (numDef.isFloatingPoint()) {
          struct.setFloat(fieldName, parseFloat(codedVal, numDef.isSigned()));
        } else {
          struct.setInt(fieldName, parseInt(codedVal, numDef.isSigned()));
        }
      } else if (def instanceof BoolFieldDefinition) {
        struct.setBool(fieldName, codedVal.testBit(0));
      } else if (def instanceof BitSetFieldDefinition) {
        BitSetFieldDefinition setDef = (BitSetFieldDefinition) def;
        struct.setSet(fieldName, parseSet(codedVal, new ArrayList<>(setDef.getValues())));
      }
      shift += def.getNumBits();
    }
    return struct;
  }

  private int parseInt(BigInteger value, boolean isSigned) {
    int val;
    if (isSigned) {
      val = value.shiftLeft(1).intValue();
      if (value.testBit(value.bitLength() - 1)) {
        val = -1 * val;
      }
    } else {
      val = value.intValue();
    }
    return val;
  }

  private double parseFloat(BigInteger value, boolean isSigned) {
    int val = parseInt(value, isSigned);
    int numBits = isSigned ? value.bitLength() : value.bitLength() - 1;
    return (float) (val / Math.pow(2, numBits));
  }

  private Set<String> parseSet(BigInteger value, List<String> values) {
    Set<String> out = new LinkedHashSet<>();
    for (int i=0; i < values.size(); i++) {
      if (value.testBit(i)) {
        out.add(values.get(i));
      }
    }
    return out;
  }

  public byte[] serializeStruct(PayloadStruct struct) {
    int outSize = (int) Math.ceil((double) getSize() / 8.0);
    BigInteger encoded = new BigInteger(new byte[outSize]);

    for (String field : Lists.reverse(new ArrayList<>(this.fields.keySet()))) {
      FieldDefinition def = this.fields.get(field);
      if (def instanceof NumberFieldDefinition) {
        NumberFieldDefinition numDef = (NumberFieldDefinition) def;
        if (numDef.isFloatingPoint()) {
          encoded = encoded.or(encodeFloat(struct.getFloat(field), def.getNumBits(), numDef.isSigned()));
        } else {
          encoded = encoded.or(BigInteger.valueOf(struct.getInt(field)));
        }
      } else if (def instanceof BoolFieldDefinition && struct.getBool(field)) {
        encoded = encoded.setBit(0);
      } else if (def instanceof BitSetFieldDefinition) {
        encoded = encodeSet(struct.getSet(field), (BitSetFieldDefinition) def);
      }
    }

    byte[] out = encoded.toByteArray();

    // FIXME: This should only strip as many leading zeroes as out.length - outSize
    // Strip extra leading null-bytes
    if (out.length > outSize) {
      byte[] trimmed = new byte[outSize];
      int trimmedIdx = 0;
      boolean prefix = true;
      for (byte anOut : out) {
        if (anOut != 0 || !prefix) {
          prefix = false;
          trimmed[trimmedIdx] = anOut;
          trimmedIdx += 1;
        }
      }
      out = trimmed;
    }
    return out;
  }

  private BigInteger encodeFloat(double value, int numBits, boolean isSigned) {
    return BigInteger.valueOf(Math.round(value * Math.pow(2, numBits)));
  }

  private BigInteger encodeSet(Set<String> values, BitSetFieldDefinition def) {
    BigInteger encoded = new BigInteger(new byte[def.getValues().size()]);
    int idx = 0;
    for (String v : def.getValues()) {
      if (values.contains(v)) {
        encoded = encoded.setBit(idx);
      }
      idx += 1;
    }
    return encoded;
  }

  public LinkedHashMap<String, FieldDefinition> getFields() {
    return fields;
  }

  public int getSize() {
    return this.fields.values().stream()
        .mapToInt(FieldDefinition::getNumBits)
        .sum();
  }

  @Override
  public BytesRef encode(char[] buffer, int offset, int length) {
    String payload = new String(buffer, offset, length).toLowerCase();
    PayloadStruct struct = this.parseStringPayload(payload);
    byte[] data = this.serializeStruct(struct);
    return new BytesRef(data);
  }
}
