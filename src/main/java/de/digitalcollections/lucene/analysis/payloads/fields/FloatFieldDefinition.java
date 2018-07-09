package de.digitalcollections.lucene.analysis.payloads.fields;

public class FloatFieldDefinition extends FieldDefinition {
  public FloatFieldDefinition(String key, int numBits) {
    super(key, numBits);
    if (numBits != 16 && numBits != 32 && numBits != 64) {
      throw new IllegalArgumentException(
          "Floating point values must be 16 (half precision), 32 (single precision) or 64 bits (double precision)");
    }
  }
}
