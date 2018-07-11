package de.digitalcollections.lucene.analysis.payloads;

import at.favre.lib.bytes.Bytes;
import de.digitalcollections.lucene.analysis.payloads.fields.*;
import de.digitalcollections.lucene.analysis.util.Half;
import org.apache.lucene.analysis.payloads.AbstractEncoder;
import org.apache.lucene.analysis.payloads.PayloadEncoder;
import org.apache.lucene.util.BytesRef;

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
    Bytes encoded = Bytes.allocate(outSize);

    boolean shift = false;
    for (String field : this.schema.getFieldNames()) {
      FieldDefinition def = this.schema.getField(field);
      if (shift) {
        encoded = encoded.leftShift(def.getNumBits());
      }
      if (def instanceof IntegerFieldDefinition) {
        long val = struct.getInt(field);
        Bytes intVal = Bytes.from(val);
        /*
        if (((IntegerFieldDefinition) def).isSigned() && val < 0) {
          Bytes intMask = Bytes.from(LongMath.pow(2, def.getNumBits()) - 1).not();
          intVal = intVal.or(intMask);
        }
        */
        encoded = encoded.or(intVal.resize(encoded.length()).array());
      } else if (def instanceof FloatFieldDefinition) {
        if (def.getNumBits() == 16) {
          encoded = encoded.or(Bytes.from(new Half(struct.getFloat(field)).halfValue()).resize(encoded.length()).array());
        } else if (def.getNumBits() == 32) {
          encoded = encoded.or(Bytes.from(Float.floatToIntBits((float) struct.getFloat(field))).resize(encoded.length()).array());
        } else {
          encoded = encoded.or(Bytes.from(Double.doubleToRawLongBits(struct.getFloat(field))).resize(encoded.length()).array());
        }
      } else if (def instanceof PercentageFieldDefinition) {
        encoded = encoded.or(encodePercentage(struct.getPercentage(field), def.getNumBits()).resize(encoded.length()).array());
      } else if (def instanceof BoolFieldDefinition && struct.getBool(field)) {
        encoded = encoded.switchBit(0, true);
      } else if (def instanceof BitSetFieldDefinition) {
        encoded = encoded.or(encodeSet(struct.getSet(field), (BitSetFieldDefinition) def).resize(encoded.length()).array());
      }
      shift = true;
    }

    Bytes trimmed = Bytes.from(new byte[]{});
    for (Byte b : encoded) {
      if (b == 0) {
        continue;
      }
      trimmed = trimmed.append(b);
    }
    return trimmed.array();
  }

  private Bytes encodePercentage(double value, int numBits) {
    return Bytes.from(Math.round((value / 100) * Math.pow(2, numBits)));
  }

  private Bytes encodeSet(Set<String> values, BitSetFieldDefinition def) {
    int outSize = (int) Math.ceil((double) def.getNumBits() / 8.0);
    Bytes encoded = Bytes.allocate(outSize);
    int idx = 0;
    for (String v : def.getValues()) {
      if (values.contains(v)) {
        encoded = encoded.switchBit(idx, true);
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
