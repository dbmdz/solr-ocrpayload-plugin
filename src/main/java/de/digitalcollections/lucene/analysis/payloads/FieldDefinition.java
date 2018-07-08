package de.digitalcollections.lucene.analysis.payloads;

public abstract class FieldDefinition {
  private final String key;
  private final int numBits;

  public FieldDefinition(String key, int numBits) {
    this.key = key;
    this.numBits = numBits;
  }

  public int getNumBits() {
    return numBits;
  }

  public String getKey() {
    return key;
  }
}
