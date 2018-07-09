package de.digitalcollections.lucene.analysis.payloads.fields;

public class IntegerFieldDefinition extends FieldDefinition {
  private final boolean signed;

  public IntegerFieldDefinition(String key, int numBits, boolean isSigned) {
    super(key, numBits);
    this.signed = isSigned;
  }

  public boolean isSigned() {
    return signed;
  }
}
