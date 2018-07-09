package de.digitalcollections.lucene.analysis.payloads;

import com.google.common.collect.ImmutableSet;
import de.digitalcollections.lucene.analysis.payloads.fields.*;
import org.apache.lucene.analysis.util.ResourceLoader;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class PayloadSchema {
  private final LinkedHashMap<String, FieldDefinition> fields;

  public static class Builder implements Cloneable {
    private final LinkedHashMap<String, FieldDefinition> fields;

    Builder() {
      this.fields = new LinkedHashMap<>();
    }

    private Builder(LinkedHashMap<String, FieldDefinition> fields) {
      this.fields = fields;
    }

    public Builder addIntegerField(String fieldName, String key, int numBits, boolean isSigned) {
      fields.put(fieldName, new IntegerFieldDefinition(key, numBits, isSigned));
      return this;
    }

    public Builder addFloatField(String fieldName, String key, int numBits) {
      fields.put(fieldName, new FloatFieldDefinition(key, numBits));
      return this;
    }

    public Builder addPercentageField(String fieldName, String key, int numBits) {
      fields.put(fieldName, new PercentageFieldDefinition(key, numBits));
      return this;
    }

    public Builder addBooleanField(String fieldName, String key) {
      fields.put(fieldName, new BoolFieldDefinition(key));
      return this;
    }

    public Builder addSetField(String fieldName, String key, LinkedHashSet<String> values) {
      fields.put(fieldName, new BitSetFieldDefinition(key, values));
      return this;
    }

    public PayloadSchema build() {
      return new PayloadSchema(fields);
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
      try {
        return super.clone();
      } catch (CloneNotSupportedException e) {
        return new Builder(new LinkedHashMap<>(fields));
      }
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static PayloadSchema load(ResourceLoader loader, String schemaFileName) throws IOException {
    Yaml yml = new Yaml(new SafeConstructor());
    LinkedHashMap<String, FieldDefinition> fields = new LinkedHashMap<>();
    LinkedHashMap<String, LinkedHashMap<String, Object>> schema = yml.load(loader.openResource(schemaFileName));
    for (Map.Entry<String, LinkedHashMap<String, Object>> entry : schema.entrySet()) {
      String fieldName = entry.getKey();
      String key = (String) entry.getValue().get("key");
      if (key == null) {
        key = fieldName;
      }
      if (!(entry.getValue().containsKey("type"))) {
        throw new RuntimeException(String.format("Configuration for field '%s' does not have a 'type'!", fieldName));
      }
      FieldDefinition fieldDef;
      String type = (String) entry.getValue().get("type");
      if (ImmutableSet.of("int", "float", "percentage").contains(type)) {
        int numBits = (int) entry.getValue().get("bits");
        if ("int".equals(type)) {
          boolean isSigned = (boolean) entry.getValue().getOrDefault("signed", false);
          fieldDef = new IntegerFieldDefinition(key, numBits, isSigned);
        } else if ("float".equals(type)) {
          fieldDef = new FloatFieldDefinition(key, numBits);
        } else {
          fieldDef = new PercentageFieldDefinition(key, numBits);
        }
      } else if ("bool".equals(type)) {
        fieldDef = new BoolFieldDefinition(key);
      } else if ("set".equals(type)) {
        ArrayList<String> values = (ArrayList<String>) entry.getValue().get("values");
        fieldDef = new BitSetFieldDefinition(key, new LinkedHashSet<>(values));
      } else {
        throw new RuntimeException(String.format("Unknown field type '%s'.", type));
      }
      fields.put(fieldName, fieldDef);
    }
    return new PayloadSchema(fields);
  }

  private PayloadSchema(LinkedHashMap<String, FieldDefinition> fields) {
    this.fields = fields;
  }

  public List<String> getFieldNames() {
    return new ArrayList<>(fields.keySet());
  }

  public void addField(String fieldName, FieldDefinition definition) {
    this.fields.put(fieldName, definition);
  }

  public FieldDefinition getField(String fieldName) {
    return this.fields.get(fieldName);
  }

  public int getSize() {
    return this.fields.values().stream()
        .mapToInt(FieldDefinition::getNumBits)
        .sum();
  }
}