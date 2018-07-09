package de.digitalcollections.lucene.analysis.payloads;

import com.google.common.math.IntMath;
import de.digitalcollections.lucene.analysis.payloads.fields.*;
import de.digitalcollections.lucene.analysis.util.Half;
import org.apache.lucene.util.BytesRef;

import java.math.BigInteger;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StructParser {
  private static final Pattern PAYLOAD_PAT = Pattern.compile(
      "(.+?):([^,\\[\\]]+|\\[.+?\\]),?");

  private final PayloadSchema schema;

  public StructParser(PayloadSchema schema) {
    this.schema = schema;
  }

  public PayloadStruct fromString(String payload) {
    Matcher m = PAYLOAD_PAT.matcher(payload);
    Map<String, String> params = new HashMap<>();
    while (m.find()) {
      String key = m.group(1);
      String val = m.group(2);
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
      String val = params.get(key);
      if (def instanceof IntegerFieldDefinition) {
        struct.setInt(field, Integer.parseInt(val));
      } else if (def instanceof FloatFieldDefinition) {
        struct.setFloat(field, Double.parseDouble(val));
      } else if (def instanceof PercentageFieldDefinition) {
        struct.setPercentage(field, Double.parseDouble(val));
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

  public PayloadStruct fromBytes(BytesRef data) {
    int numBytes = (int) Math.ceil((double) schema.getSize() / 8.0);
    if (data.length != schema.getSize()) {
      throw new IllegalArgumentException(String.format(
          "Bad payload size, expected %d bytes, but got %d bytes", schema.getSize(), numBytes));
    }

    PayloadStruct struct = new PayloadStruct(schema);
    BigInteger encoded = new BigInteger(Arrays.copyOfRange(data.bytes, data.offset, data.offset + data.length));
    int shift = 0;
    for (String fieldName: schema.getFieldNames()) {
      FieldDefinition def = schema.getField(fieldName);
      BigInteger mask = BigInteger.valueOf(IntMath.pow(2, def.getNumBits()));
      BigInteger codedVal = encoded.shiftRight(shift).and(mask);
      if (def instanceof IntegerFieldDefinition) {
        IntegerFieldDefinition intDef = (IntegerFieldDefinition) def;
        struct.setInt(fieldName, parseInt(codedVal, intDef.isSigned()));
      } else if (def instanceof FloatFieldDefinition) {
        if (def.getNumBits() == 16) {
          struct.setFloat(fieldName, Half.valueOf(codedVal.shortValue()).doubleValue());
        } else if (def.getNumBits() == 32) {
          struct.setFloat(fieldName, Float.intBitsToFloat(codedVal.intValue()));
        } else {
          struct.setFloat(fieldName, Double.longBitsToDouble(codedVal.longValue()));
        }
      } else if (def instanceof PercentageFieldDefinition) {
        struct.setPercentage(fieldName, parsePercentage(codedVal));
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

  private static int parseInt(BigInteger value, boolean isSigned) {
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

  private static double parsePercentage(BigInteger value) {
    return (float) (value.longValue() / Math.pow(2, value.bitLength()));
  }

  private static Set<String> parseSet(BigInteger value, List<String> values) {
    Set<String> out = new LinkedHashSet<>();
    for (int i=0; i < values.size(); i++) {
      if (value.testBit(i)) {
        out.add(values.get(i));
      }
    }
    return out;
  }
}
