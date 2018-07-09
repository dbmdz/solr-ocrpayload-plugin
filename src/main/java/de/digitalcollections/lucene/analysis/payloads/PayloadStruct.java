package de.digitalcollections.lucene.analysis.payloads;

import com.google.common.collect.Sets;
import com.google.common.math.IntMath;
import de.digitalcollections.lucene.analysis.payloads.fields.BitSetFieldDefinition;
import de.digitalcollections.lucene.analysis.payloads.fields.BoolFieldDefinition;
import de.digitalcollections.lucene.analysis.payloads.fields.FieldDefinition;
import de.digitalcollections.lucene.analysis.payloads.fields.FloatFieldDefinition;
import de.digitalcollections.lucene.analysis.payloads.fields.IntegerFieldDefinition;
import de.digitalcollections.lucene.analysis.payloads.fields.PercentageFieldDefinition;
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
  private final Map<String, Double> percentFields;
  private final Map<String, Boolean> boolFields;
  private final Map<String, Set<String>> bitsetFields;

  public PayloadStruct(PayloadSchema schema) {
    this.schema = schema;
    intFields = new HashMap<>();
    floatFields = new HashMap<>();
    boolFields = new HashMap<>();
    bitsetFields = new HashMap<>();
    percentFields = new HashMap<>();
  }

  public PayloadSchema getSchema() {
    return schema;
  }

  public NamedList<Object> toNamedList() {
    NamedList<Object> namedList = new SimpleOrderedMap<>();
    for (String fieldName: this.schema.getFieldNames()) {
      if (intFields.containsKey(fieldName)) {
        namedList.add(fieldName, intFields.get(fieldName));
      } else if (floatFields.containsKey(fieldName)) {
        namedList.add(fieldName, floatFields.get(fieldName));
      } else if (percentFields.containsKey(fieldName)) {
        namedList.add(fieldName, percentFields.get(fieldName));
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
    if (!this.schema.getFieldNames().contains(fieldName)) {
      throw new IllegalArgumentException("Unknown field: " + fieldName);
    }
    FieldDefinition fieldDef = schema.getField(fieldName);
    if (!(fieldDef instanceof IntegerFieldDefinition)) {
      throw new IllegalArgumentException("Not an integer field: " + fieldName);
    }
    IntegerFieldDefinition intDef = (IntegerFieldDefinition) fieldDef;
    if (intDef.isSigned() && value < 0) {
      throw new IllegalArgumentException(String.format(
          "Field is not supposed to hold signed values (was: %d): %s", value, fieldName));
    }
    if (Math.abs(value) >= IntMath.pow(2, intDef.getNumBits())) {
      throw new IllegalArgumentException(String.format(
          "Value for field '%s' exceeds configured number of bits (%d, value was %d)",
          fieldName, intDef.getNumBits(), value));
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
    if (!this.schema.getFieldNames().contains(fieldName)) {
      throw new IllegalArgumentException("Unknown field: " + fieldName);
    }
    FieldDefinition fieldDef = schema.getField(fieldName);
    if (!(fieldDef instanceof FloatFieldDefinition)) {
      throw new IllegalArgumentException("Not a a floating point field: " + fieldName);
    }
    this.floatFields.put(fieldName, value);
  }

  public double getPercentage(String fieldName) {
    if (!this.percentFields.containsKey(fieldName)) {
      throw new IllegalArgumentException("Unknown percentage field: " + fieldName);
    }
    return percentFields.get(fieldName);
  }

  public void setPercentage(String fieldName, double percentage) {
    if (!this.schema.getFieldNames().contains(fieldName)) {
      throw new IllegalArgumentException("Unknown field: " + fieldName);
    }
    FieldDefinition fieldDef = schema.getField(fieldName);
    if (!(fieldDef instanceof PercentageFieldDefinition)) {
      throw new IllegalArgumentException("Not a a percentage field: " + fieldName);
    }
    if (percentage < 0 || percentage > 100) {
      throw new IllegalArgumentException(String.format(
          "Percentage value for field '%s' must be between 0 and 100 (was: %.4f)", fieldName, percentage));
    }
    this.percentFields.put(fieldName, percentage);
  }

  public boolean getBool(String fieldName) {
    if (!this.boolFields.containsKey(fieldName)) {
      throw new IllegalArgumentException("Unknown boolean field: " + fieldName);
    }
    return boolFields.get(fieldName);
  }

  public void setBool(String fieldName, boolean value) {
    if (!this.schema.getFieldNames().contains(fieldName)) {
      throw new IllegalArgumentException("Unknown field: " + fieldName);
    }
    FieldDefinition fieldDef = schema.getField(fieldName);
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
    if (!this.schema.getFieldNames().contains(fieldName)) {
      throw new IllegalArgumentException("Unknown field: " + fieldName);
    }
    FieldDefinition fieldDef = schema.getField(fieldName);
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
