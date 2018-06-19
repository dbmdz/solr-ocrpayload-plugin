package de.digitalcollections.lucene.analysis.payloads;

import com.google.common.math.IntMath;
import org.apache.lucene.util.BytesRef;

import java.math.BigInteger;
import java.util.Arrays;


/** Helper class to decode and encode OCR information from/into an efficient binary representation. **/
public class OcrPayloadHelper {
  private OcrPayloadHelper() {
    // Cannot be instantiated, is only here for the static methods
  }

  /**
   * Encode a {@link OcrInfo} object into a byte array.
   *
   * If the coordinates are set to be stored as relative (i.e. percentage values), we first scale the bounding box
   * coordinates according to `precision`. We then pack the complete information into `coordBits * 4` bits. The bit
   * packing is done to save as much space as possible, while scaling is used t ostill maintain as much precision as
   * possible.
   *
   * Optionally, we also store word, line and page indices if the corresponding option
   * (`wordBits`, `lineBits`, `pageBits`) is non-zero.
   *
   * Here an example with page, line and word indices, relative coordinates and 10 bits per coordinate value:
   *
   * **Input:**
   * ```
   * info = OcrInfo(pageIndex=837, lineIndex=13, wordIndex=20, horizontalOffset=0.136838387,
   *                verticalOffset=0.477909823, width=0.978231258, height=0.532390081),
   * coordBits = 10
   * wordBits  = 9
   * lineBits  = 10
   * pageBits  = 12
   * absoluteCoordinates = false
   * ```
   *
   * Since we are using 10 bits for each of the four coordinates, 9 bits for the word index, 10 for the line index and
   * 12 for the page index, the resulting binary representation will have 72 bits (`4 * 10 + 9 + 11 + 12`) or 9 bytes.
   * This is very space-efficient compared to a string-based encoding, e.g. `x136y478w978h532n20l13p837`, which is
   * 36 bytes.
   *
   *
   * **Output:**
   * ```
   * field     | width  |        scaled value        | binary representation
   * ========================================================================
   * pageIndex | 12bit  |                        837 | 001101000100
   * lineIndex | 11bit  |                         13 |  00000001101
   * wordIndex |  9bit  |                         20 |    000010100
   * x         | 10bit  | 0.136838387 * 2^10 ~>  140 |   0010001100
   * y         | 10bit  | 0.477909823 * 2^10 ~>  489 |   0111101001
   * width     | 10bit  | 0.978231258 * 2^10 ~> 1002 |   1111101010
   * height    | 10bit  | 0.532390081 * 2^10 ~>  545 |   1000100001
   * ````
   *
   * The resulting byte sequence is as follows (bytes are separated by whitespace):
   * ```
   *    pageIndex | lineIndex  | wordIndex|      x     |     y     |     w     |    h
   * 00110100 0100|0000 0001101|0 00010100| 00100011 00|011110 1001|1111 101010|10 00100001
   *     0x34      0x40      0x1A     0x14      0x23      0x1E      0x9F      0xAA     0x21
   * ```
   *
   * @param info                The {@link OcrInfo} to encode
   * @param coordBits The number of bits to encode each OCR coordinate value into
   * @param wordBits  The number of bits to encode the word index into
   * @param lineBits  The number of bits to encode the line index into
   * @param pageBits  The number of bits to encode the page index into
   * @return                    The resulting byte payload
   */
  public static byte[] encodeOcrInfo(OcrInfo info, int coordBits, int wordBits, int lineBits, int pageBits) {
    // To make bit-fiddling easier, we encode all the values into an arbitrary-length BigInteger
    int numBitsTotal = getOutputSize(coordBits, wordBits, lineBits, pageBits);
    int outSize = (int) Math.ceil((double) numBitsTotal / 8.0);
    BigInteger encoded = new BigInteger(new byte[outSize]);

    if (pageBits > 0) {
      encoded = encoded.or(BigInteger.valueOf(info.getPageIndex()));
    }
    if (lineBits > 0) {
      encoded = encoded.shiftLeft(lineBits)
          .or(BigInteger.valueOf(info.getLineIndex()));
    }
    if (wordBits > 0) {
      encoded = encoded.shiftLeft(wordBits)
          .or(BigInteger.valueOf(info.getWordIndex()));
    }
    if (info.getHasAbsoluteCoordinates()) {
      encoded = encoded
          .shiftLeft(coordBits)
          .or(BigInteger.valueOf(verifyAbsoluteValue((int) info.getHorizontalOffset(), coordBits)))
          .shiftLeft(coordBits)
          .or(BigInteger.valueOf(verifyAbsoluteValue((int) info.getVerticalOffset(), coordBits)))
          .shiftLeft(coordBits)
          .or(BigInteger.valueOf(verifyAbsoluteValue((int) info.getWidth(), coordBits)))
          .shiftLeft(coordBits)
          .or(BigInteger.valueOf(verifyAbsoluteValue((int) info.getHeight(), coordBits)));

    } else {
      encoded = encoded
          .shiftLeft(coordBits)
          .or(BigInteger.valueOf(encodeValue(info.getHorizontalOffset(), coordBits)))
          .shiftLeft(coordBits)
          .or(BigInteger.valueOf(encodeValue(info.getVerticalOffset(), coordBits)))
          .shiftLeft(coordBits)
          .or(BigInteger.valueOf(encodeValue(info.getWidth(), coordBits)))
          .shiftLeft(coordBits)
          .or(BigInteger.valueOf(encodeValue(info.getHeight(), coordBits)));
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

  private static int verifyAbsoluteValue(int value, int coordBits) {
    if (value >= IntMath.pow(2, coordBits)) {
      throw new IllegalArgumentException(String.format(
          "Value %d exceeds legal range of %d bits (0 to %d).", value, coordBits, IntMath.pow(2, coordBits)-1));
    }
    return value;
  }

  /** Calculate the size of the payload resulting from the parameters **/
  private static int getOutputSize(int coordBits, int wordBits, int lineBits, int pageBits) {
    int outSize = coordBits * 4;
    if (pageBits > 0) {
      outSize += pageBits;
    }
    if (lineBits > 0) {
      outSize += lineBits;
    }
    if (wordBits > 0) {
      outSize += wordBits;
    }
    return outSize;
  }

  /**
   * Encode a given floating point value (between 0 and 1) to an integer with the given number of bits.
   **/
  private static int encodeValue(float source, int numBits) {
    return (int) Math.round(source * Math.pow(2, numBits));
  }

  /**
   * Decode a given integer (encoded with a certain number of bits) to a floating point value.
   **/
  private static float decodeValue(long source, int numBits) {
    return (float) (source / Math.pow(2, numBits));
  }

  /**
   * Create a bit mask to mask out a given number of bits
   */
  private static BigInteger makeBitMask(int numBits) {
    return BigInteger.valueOf(IntMath.pow(2, numBits) - 1);
  }

  /**
   * Decode an {@link OcrInfo} instance from the encoded byte array.
   *
   * @param data        Buffer with encoded binary OCR information
   * @param coordBits   Number of bits the OCR information was encoded with
   * @param wordBits    Number of bits the word index was encoded with
   * @param lineBits    Number of bits the line index was encoded with
   * @param pageBits    Number of bits the page index was encoded with
   * @return The decoded {@link OcrInfo} instance
   */
  public static OcrInfo decodeOcrInfo(BytesRef data, int coordBits, int wordBits, int lineBits, int pageBits,
                                      boolean absoluteCoordinates) {
    int coordMask = IntMath.pow(2, coordBits) - 1;
    OcrInfo info = new OcrInfo();
    info.setHasAbsoluteCoordinates(absoluteCoordinates);
    BigInteger encoded = new BigInteger(Arrays.copyOfRange(data.bytes, data.offset, data.offset + data.length));

    if (absoluteCoordinates) {
      info.setHeight(encoded.and(BigInteger.valueOf(coordMask)).intValue());
      info.setWidth(encoded.shiftRight(coordBits)
                           .and(BigInteger.valueOf(coordMask)).intValue());
      info.setVerticalOffset(encoded.shiftRight(coordBits*2)
                                    .and(BigInteger.valueOf(coordMask)).intValue());
      info.setHorizontalOffset(encoded.shiftRight(coordBits*3)
                                      .and(BigInteger.valueOf(coordMask)).intValue());

    } else {
      info.setHeight(OcrPayloadHelper.decodeValue(
          encoded.and(BigInteger.valueOf(coordMask)).intValue(), coordBits));
      info.setWidth(OcrPayloadHelper.decodeValue(
          encoded.shiftRight(coordBits)
              .and(BigInteger.valueOf(coordMask)).intValue(), coordBits));
      info.setVerticalOffset(OcrPayloadHelper.decodeValue(
          encoded.shiftRight(coordBits*2)
              .and(BigInteger.valueOf(coordMask)).intValue(), coordBits));
      info.setHorizontalOffset(OcrPayloadHelper.decodeValue(
          encoded.shiftRight(coordBits*3)
              .and(BigInteger.valueOf(coordMask)).intValue(), coordBits));
    }

    int shift = coordBits*4;
    if (wordBits > 0) {
      info.setWordIndex(encoded.shiftRight(shift).and(makeBitMask(wordBits)).intValue());
      shift += wordBits;
    }
    if (lineBits > 0) {
      info.setLineIndex(encoded.shiftRight(shift).and(makeBitMask(lineBits)).intValue());
      shift += lineBits;
    }
    if (pageBits > 0) {
      info.setPageIndex(encoded.shiftRight(shift).intValue());
    }

    return info;
  }
}
