package de.digitalcollections.lucene.analysis.payloads.fields;

import java.util.LinkedHashSet;

public class BitSetFieldDefinition extends FieldDefinition {
  private final LinkedHashSet<String> values;

  public BitSetFieldDefinition(String key, LinkedHashSet<String> values) {
    super(key, values.size());
    this.values = values;
  }

  public LinkedHashSet<String> getValues() {
    return values;
  }
}
