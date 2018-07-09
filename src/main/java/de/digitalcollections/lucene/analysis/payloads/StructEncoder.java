package de.digitalcollections.lucene.analysis.payloads;

import de.digitalcollections.lucene.analysis.payloads.fields.*;
import de.digitalcollections.lucene.analysis.util.Half;
import org.apache.lucene.analysis.payloads.AbstractEncoder;
import org.apache.lucene.analysis.payloads.PayloadEncoder;
import org.apache.lucene.util.BytesRef;

import java.math.BigInteger;
import java.util.Set;

public class StructEncoder extends AbstractEncoder implements PayloadEncoder {
  private final PayloadSchema schema;
  private final StructParser parser;

  public StructEncoder(PayloadSchema schema) {
    this.schema = schema;
    this.parser = new StructParser(schema);
  }

  public byte[] serializeStruct(PayloadStruct struct) {
    int outSize = (int) Math.ceil((double) schema.getSize() / 8.0);
    BigInteger encoded = new BigInteger(new byte[outSize]);

    boolean shift = false;
    for (String field : this.schema.getFieldNames()) {
      FieldDefinition def = this.schema.getField(field);
      if (shift) {
        encoded = encoded.shiftLeft(def.getNumBits());
      }
      if (def instanceof IntegerFieldDefinition) {
        encoded = encoded.or(BigInteger.valueOf(struct.getInt(field)));
      } else if (def instanceof FloatFieldDefinition) {
        if (def.getNumBits() == 16) {
          encoded = encoded.or(BigInteger.valueOf(new Half(struct.getFloat(field)).halfValue()));
        } else if (def.getNumBits() == 32) {
          encoded = encoded.or(BigInteger.valueOf(Float.floatToIntBits((float) struct.getFloat(field))));
        } else {
          encoded = encoded.or(BigInteger.valueOf(Double.doubleToLongBits(struct.getFloat(field))));
        }
      } else if (def instanceof PercentageFieldDefinition) {
        encoded = encoded.or(encodePercentage(struct.getPercentage(field), def.getNumBits()));
      } else if (def instanceof BoolFieldDefinition && struct.getBool(field)) {
        encoded = encoded.setBit(0);
      } else if (def instanceof BitSetFieldDefinition) {
        encoded = encodeSet(struct.getSet(field), (BitSetFieldDefinition) def);
      }
      shift = true;
    }
    byte[] out = encoded.toByteArray();

    // FIXME: This should only strip as many leading zeroes as out.length - outSize
    // Strip extra leading null-bytes
    if (out.length > outSize) {
      byte[] trimmed = new byte[outSize];
      int trimmedIdx = 0;
      boolean prefix = true;
      for (byte anOut : out) {
        if (anOut != 0 || !prefix) {
          prefix = false;
          trimmed[trimmedIdx] = anOut;
          trimmedIdx += 1;
        }
      }
      out = trimmed;
    }
    return out;
  }

  private BigInteger encodePercentage(double value, int numBits) {
    return BigInteger.valueOf(Math.round((value / 100) * Math.pow(2, numBits)));
  }

  private BigInteger encodeSet(Set<String> values, BitSetFieldDefinition def) {
    BigInteger encoded = new BigInteger(new byte[def.getValues().size()]);
    int idx = 0;
    for (String v : def.getValues()) {
      if (values.contains(v)) {
        encoded = encoded.setBit(idx);
      }
      idx += 1;
    }
    return encoded;
  }

  @Override
  public BytesRef encode(char[] buffer, int offset, int length) {
    String payload = new String(buffer, offset, length).toLowerCase();
    PayloadStruct struct = parser.fromString(payload);
    byte[] data = this.serializeStruct(struct);
    return new BytesRef(data);
  }
}
