package de.digitalcollections.lucene.analysis.payloads;

import com.google.common.collect.Sets;
import com.google.common.math.LongMath;
import de.digitalcollections.lucene.analysis.payloads.fields.*;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PayloadStruct {

  private final PayloadSchema schema;
  private final Map<String, Long> intFields;
  private final Map<String, Double> floatFields;
  private final Map<String, Double> percentFields;
  private final Map<String, Boolean> boolFields;
  private final Map<String, Set<String>> bitsetFields;
  private String term;

  static class Builder {
    private PayloadStruct struct;
    public Builder(PayloadSchema schema) {
      this.struct = new PayloadStruct(schema);
    }

    public Builder addInt(String fieldName, long value) {
      struct.setInt(fieldName, value);
      return this;
    }

    public Builder addFloat(String fieldName, double value) {
      struct.setFloat(fieldName, value);
      return this;
    }

    public Builder addPercentage(String fieldName, double percentage) {
      struct.setPercentage(fieldName, percentage);
      return this;
    }

    public Builder addBool(String fieldName, boolean value) {
      struct.setBool(fieldName, value);
      return this;
    }

    public Builder addSet(String fieldName, Set<String> value) {
      struct.setSet(fieldName, value);
      return this;
    }

    public PayloadStruct build() {
      return this.struct;
    }
  }

  public static Builder builder(PayloadSchema schema) {
    return new Builder(schema);
  }

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
    if (term != null) {
      namedList.add("term", term);
    }
    return namedList;
  }

  public long getInt(String fieldName) {
    return intFields.get(fieldName);
  }

  public void setInt(String fieldName, long value) {
    IntegerFieldDefinition intDef = (IntegerFieldDefinition) schema.getField(fieldName);
    if (!intDef.isSigned() && value < 0) {
      throw new IllegalArgumentException(String.format(
          "Field is not supposed to hold signed values (was: %d): %s", value, fieldName));
    }
    if (Math.abs(value) >= LongMath.pow(2, intDef.getNumBits())) {
      String legalRange;
      long maxVal = LongMath.pow(2, intDef.getNumBits()) - 1;
      if (intDef.isSigned()) {
        legalRange = String.format("[-%d, %d]", maxVal, maxVal);
      } else {
        legalRange = String.format("[0, %d]", maxVal);
      }
      throw new IllegalArgumentException(String.format(
          "Value '%d' for field '%s' exceeds configured number of bits (%d, legal range is %s)",
          value, fieldName, intDef.getNumBits(), legalRange));
    }
    this.intFields.put(fieldName, value);
  }

  public double getFloat(String fieldName) {
    return floatFields.get(fieldName);
  }

  public void setFloat(String fieldName, double value) {
    this.floatFields.put(fieldName, value);
  }

  public double getPercentage(String fieldName) {
    return percentFields.get(fieldName);
  }

  public void setPercentage(String fieldName, double percentage) {
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
    FieldDefinition fieldDef = schema.getField(fieldName);
    this.boolFields.put(fieldName, value);
  }

  public Set<String> getSet(String fieldName) {
    if (!this.bitsetFields.containsKey(fieldName)) {
      throw new IllegalArgumentException("Unknown set field: " + fieldName);
    }
    return bitsetFields.get(fieldName);
  }

  public void setSet(String fieldName, Set<String> value) {
    FieldDefinition fieldDef = schema.getField(fieldName);
    BitSetFieldDefinition setFieldDef = (BitSetFieldDefinition) fieldDef;
    Sets.SetView<String> diff = Sets.difference(value, setFieldDef.getValues());
    if (diff.size() > 0) {
      throw new IllegalArgumentException(String.format(
          "Input set contains unknown values: {%s}", diff.stream().collect(Collectors.joining(", "))));
    }
    this.bitsetFields.put(fieldName, value);
  }

  public String getTerm() {
    return term;
  }

  public void setTerm(String term) {
    this.term = term;
  }
}
