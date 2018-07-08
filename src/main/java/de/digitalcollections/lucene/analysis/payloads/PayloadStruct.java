package de.digitalcollections.lucene.analysis.payloads;

import com.google.common.collect.Sets;
import com.google.common.math.IntMath;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PayloadStruct {
  private final PayloadSchema schema;
  private final Map<String, Integer> intFields;
  private final Map<String, Double> floatFields;
  private final Map<String, Boolean> boolFields;
  private final Map<String, Set<String>> bitsetFields;

  public PayloadStruct(PayloadSchema schema) {
    this.schema = schema;
    intFields = new HashMap<>();
    floatFields = new HashMap<>();
    boolFields = new HashMap<>();
    bitsetFields = new HashMap<>();
  }

  public PayloadSchema getSchema() {
    return schema;
  }

  public NamedList<Object> toNamedList() {
    NamedList<Object> namedList = new SimpleOrderedMap<>();
    for (Map.Entry<String, FieldDefinition> entry : schema.getFields().entrySet()) {
      String fieldName = entry.getKey();
      if (intFields.containsKey(fieldName)) {
        namedList.add(fieldName, intFields.get(fieldName));
      } else if (floatFields.containsKey(fieldName)) {
        namedList.add(fieldName, floatFields.get(fieldName));
      } else if (boolFields.containsKey(fieldName)) {
        namedList.add(fieldName, boolFields.get(fieldName));
      } else if (bitsetFields.containsKey(fieldName)) {
        namedList.add(fieldName, bitsetFields.get(fieldName).toArray());
      }
    }
    return namedList;
  }

  public int getInt(String fieldName) {
    if (!this.intFields.containsKey(fieldName)) {
      throw new IllegalArgumentException("Unknown integer field: " + fieldName);
    }
    return intFields.get(fieldName);
  }

  public void setInt(String fieldName, int value) {
    if (!this.schema.getFields().containsKey(fieldName)) {
      throw new IllegalArgumentException("Unknown field: " + fieldName);
    }
    FieldDefinition fieldDef = schema.getFields().get(fieldName);
    if (!(fieldDef instanceof NumberFieldDefinition)) {
      throw new IllegalArgumentException("Not an integer field: " + fieldName);
    }
    NumberFieldDefinition numberFieldDef = (NumberFieldDefinition) fieldDef;
    if (numberFieldDef.isFloatingPoint()) {
      throw new IllegalArgumentException("Not an integer field, use setFloat: " + fieldName);
    }
    if (numberFieldDef.isSigned() && value < 0) {
      throw new IllegalArgumentException(String.format(
          "Field is not supposed to hold signed values (was: %d): %s", value, fieldName));
    }
    if (Math.abs(value) >= IntMath.pow(2, numberFieldDef.getNumBits())) {
      throw new IllegalArgumentException(String.format(
          "Value exceeds configured number of bits (%d, value was %d)", numberFieldDef.getNumBits(), value));
    }
    this.intFields.put(fieldName, value);
  }

  public double getFloat(String fieldName) {
    if (!this.floatFields.containsKey(fieldName)) {
      throw new IllegalArgumentException("Unknown floating point field: " + fieldName);
    }
    return floatFields.get(fieldName);
  }

  public void setFloat(String fieldName, double value) {
    if (!this.schema.getFields().containsKey(fieldName)) {
      throw new IllegalArgumentException("Unknown field: " + fieldName);
    }
    FieldDefinition fieldDef = schema.getFields().get(fieldName);
    if (!(fieldDef instanceof NumberFieldDefinition)) {
      throw new IllegalArgumentException("Not floating point field: " + fieldName);
    }
    NumberFieldDefinition numberFieldDef = (NumberFieldDefinition) fieldDef;
    if (numberFieldDef.isFloatingPoint()) {
      throw new IllegalArgumentException("Not an floating point field, use setInt: " + fieldName);
    }
    if (numberFieldDef.isSigned() && value < 0) {
      throw new IllegalArgumentException(String.format(
          "Field is not supposed to hold signed values (was: %.4f): %s", value, fieldName));
    }
    this.floatFields.put(fieldName, value);
  }

  public boolean getBool(String fieldName) {
    if (!this.boolFields.containsKey(fieldName)) {
      throw new IllegalArgumentException("Unknown boolean field: " + fieldName);
    }
    return boolFields.get(fieldName);
  }

  public void setBool(String fieldName, boolean value) {
    if (!this.schema.getFields().containsKey(fieldName)) {
      throw new IllegalArgumentException("Unknown field: " + fieldName);
    }
    FieldDefinition fieldDef = schema.getFields().get(fieldName);
    if (!(fieldDef instanceof BoolFieldDefinition)) {
      throw new IllegalArgumentException("Not a boolean field: " + fieldName);
    }
    this.boolFields.put(fieldName, value);
  }

  public Set<String> getSet(String fieldName) {
    if (!this.bitsetFields.containsKey(fieldName)) {
      throw new IllegalArgumentException("Unknown set field: " + fieldName);
    }
    return bitsetFields.get(fieldName);
  }

  public void setSet(String fieldName, Set<String> value) {
    if (!this.schema.getFields().containsKey(fieldName)) {
      throw new IllegalArgumentException("Unknown field: " + fieldName);
    }
    FieldDefinition fieldDef = schema.getFields().get(fieldName);
    if (!(fieldDef instanceof BitSetFieldDefinition)) {
      throw new IllegalArgumentException("Not a set field: " + fieldName);
    }
    BitSetFieldDefinition setFieldDef = (BitSetFieldDefinition) fieldDef;
    Sets.SetView<String> diff = Sets.difference(value, setFieldDef.getValues());
    if (diff.size() > 0) {
      throw new IllegalArgumentException(String.format(
          "Input set contains unknown values: {%s}", diff.stream().collect(Collectors.joining(", "))));
    }
    this.bitsetFields.put(fieldName, value);
  }
}
