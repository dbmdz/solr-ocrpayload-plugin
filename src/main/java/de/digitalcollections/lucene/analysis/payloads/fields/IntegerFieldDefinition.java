package de.digitalcollections.lucene.analysis.payloads.fields;

public class IntegerFieldDefinition extends FieldDefinition {
  private final boolean signed;

  public IntegerFieldDefinition(String key, int numBits, boolean isSigned) {
    super(key, numBits);
    if (numBits > 64) {
      throw new IllegalArgumentException("Integers are only supported up to 64 bits.");
    }
    this.signed = isSigned;
  }

  public boolean isSigned() {
    return signed;
  }
}
