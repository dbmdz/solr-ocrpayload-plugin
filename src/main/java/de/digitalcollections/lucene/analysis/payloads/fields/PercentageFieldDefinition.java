package de.digitalcollections.lucene.analysis.payloads.fields;

public class PercentageFieldDefinition extends FieldDefinition {
  public PercentageFieldDefinition(String key, int numBits) {
    super(key, numBits);
    if (numBits > 64) {
      throw new IllegalArgumentException("Percentage fields are only supported up to 64 bits.");
    }
  }
}
