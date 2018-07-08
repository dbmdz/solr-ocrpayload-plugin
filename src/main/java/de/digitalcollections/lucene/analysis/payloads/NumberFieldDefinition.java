package de.digitalcollections.lucene.analysis.payloads;

public class NumberFieldDefinition extends FieldDefinition {
  private final boolean floatingPoint;
  private final boolean signed;

  public NumberFieldDefinition(String key, int numBits, boolean isSigned, boolean isFloat) {
    super(key, numBits);
    this.signed = isSigned;
    this.floatingPoint = isFloat;
  }

  public boolean isFloatingPoint() {
    return floatingPoint;
  }

  public boolean isSigned() {
    return signed;
  }
}
