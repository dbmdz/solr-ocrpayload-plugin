package de.digitalcollections.lucene.analysis.payloads;

import at.favre.lib.bytes.Bytes;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.math.LongMath;
import de.digitalcollections.lucene.analysis.payloads.fields.*;
import de.digitalcollections.lucene.analysis.util.Half;
import org.apache.lucene.util.BytesRef;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StructParser {
  private static final Pattern PAYLOAD_PAT = Pattern.compile(
      "(.+?):([^,\\[\\]]+|\\[.+?\\]),?");
  private static final Set<String> TRUTHY_VALUES = ImmutableSet.of("y", "yes", "true", "1", "on");
  private static final Set<String> FALSY_VALUES = ImmutableSet.of("n", "no", "false", "0", "off");

  private final PayloadSchema schema;

  public StructParser(PayloadSchema schema) {
    this.schema = schema;
  }

  public PayloadStruct fromString(String payload) {
    Matcher m = PAYLOAD_PAT.matcher(payload);
    Map<String, String> params = new HashMap<>();
    while (m.find()) {
      String key = m.group(1).trim();
      String val = m.group(2).trim();
      if (params.containsKey(key)) {
        throw new IllegalArgumentException(String.format(
            "Invalid payload %s: duplicate key '%s'", payload, key));
      }
      params.put(key, val);
    }

    PayloadStruct struct = new PayloadStruct(schema);
    for (String field: schema.getFieldNames()) {
      FieldDefinition def = schema.getField(field);
      String key = def.getKey();
      if (!params.containsKey(key)) {
        throw new IllegalArgumentException(String.format(
            "Key '%s' is missing from payload string '%s'", key, payload));
      }
      String val = params.get(key).toLowerCase();
      if (def instanceof IntegerFieldDefinition) {
        struct.setInt(field, Integer.parseInt(val));
      } else if (def instanceof FloatFieldDefinition) {
        struct.setFloat(field, Double.parseDouble(val));
      } else if (def instanceof PercentageFieldDefinition) {
        struct.setPercentage(field, Double.parseDouble(val));
      } else if (def instanceof BoolFieldDefinition) {
        if (!TRUTHY_VALUES.contains(val) && !FALSY_VALUES.contains(val)) {
          String legalValues = Stream.concat(TRUTHY_VALUES.stream(), FALSY_VALUES.stream())
              .collect(Collectors.joining(","));
          throw new IllegalArgumentException(String.format(
              "Value '%s' for field '%s' is not a valid boolean, must be one of [%s]",
              val.toLowerCase(), field, legalValues));
        }
        struct.setBool(field, TRUTHY_VALUES.contains(val));
      } else if (def instanceof BitSetFieldDefinition) {
        if (!val.startsWith("[") || !val.endsWith("]")) {
          throw new IllegalArgumentException("Value is not a valid set: " + val);
        }
        BitSetFieldDefinition setDef = (BitSetFieldDefinition) def;
        Set<String> vals = new HashSet<>();
        Set<String> legalValues = setDef.getValues();
        for (String v : val.substring(1, val.length() - 1).split(",")) {
          v = v.trim();
          if (!legalValues.contains(v)) {
            throw new IllegalArgumentException(String.format(
                "Unknown value '%s' in set field '%s', legal values are [%s]",
                v, field, legalValues.stream().collect(Collectors.joining(","))));
          }
          vals.add(v);
        }
        struct.setSet(field, vals);
      }
    }
    return struct;
  }

  public PayloadStruct fromBytes(BytesRef data) {
    int numBytes = (int) Math.ceil((double) schema.getSize() / 8.0);
    if ((data.length - data.offset) > numBytes) {
      throw new IllegalArgumentException(String.format(
          "Bad payload size, expected at most %d bytes, but got %d bytes", numBytes, data.length - data.offset));
    }

    PayloadStruct struct = new PayloadStruct(schema);
    Bytes encoded = Bytes.wrap(Arrays.copyOfRange(data.bytes, data.offset, data.offset + data.length));
    //int shift = encoded.lengthBit() - schema.getSize();
    int shift = 0;
    for (String fieldName: Lists.reverse(schema.getFieldNames())) {
      FieldDefinition def = schema.getField(fieldName);
      Bytes mask = Bytes.from(LongMath.pow(2, def.getNumBits()) - 1).resize(encoded.length());
      Bytes codedVal = encoded.rightShift(shift).and(mask);
      if (def instanceof IntegerFieldDefinition) {
        IntegerFieldDefinition intDef = (IntegerFieldDefinition) def;
        struct.setInt(fieldName, parseInt(codedVal, intDef.isSigned(), intDef.getNumBits()));
      } else if (def instanceof FloatFieldDefinition) {
        if (def.getNumBits() == 16) {
          struct.setFloat(fieldName, Half.valueOf(codedVal.resize(2).toShort()).doubleValue());
        } else if (def.getNumBits() == 32) {
          struct.setFloat(fieldName, Float.intBitsToFloat(codedVal.resize(4).toInt()));
        } else {
          struct.setFloat(fieldName, Double.longBitsToDouble(codedVal.resize(8).toLong()));
        }
      } else if (def instanceof PercentageFieldDefinition) {
        struct.setPercentage(fieldName, parsePercentage(codedVal, def.getNumBits()));
      } else if (def instanceof BoolFieldDefinition) {
        struct.setBool(fieldName, codedVal.bitAt(0));
      } else if (def instanceof BitSetFieldDefinition) {
        BitSetFieldDefinition setDef = (BitSetFieldDefinition) def;
        struct.setSet(fieldName, parseSet(codedVal, new ArrayList<>(setDef.getValues())));
      }
      shift += def.getNumBits();
    }
    return struct;
  }

  private static long parseInt(Bytes value, boolean isSigned, int numBits) {
    value = value.resize(8);
    if (isSigned) {
      if (value.bitAt(numBits - 1)) {
        Bytes mask = Bytes.allocate(8, (byte) 0xff).xor(Bytes.from(LongMath.pow(2, numBits) - 1));
        value = value.xor(mask);
      }
      return value.resize(8).toLong();
    } else {
      return value.resize(8).toBigInteger().longValue();
    }
  }

  private static double parsePercentage(Bytes value, int numBits) {
    return (float) (value.resize(8).toLong() / Math.pow(2, numBits)) * 100;
  }

  private static Set<String> parseSet(Bytes value, List<String> values) {
    Set<String> out = new LinkedHashSet<>();
    for (int i=0; i < values.size(); i++) {
      if (value.bitAt(i)) {
        out.add(values.get(i));
      }
    }
    return out;
  }
}
