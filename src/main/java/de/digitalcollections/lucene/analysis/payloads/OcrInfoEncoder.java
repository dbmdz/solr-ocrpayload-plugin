package de.digitalcollections.lucene.analysis.payloads;

import org.apache.lucene.analysis.payloads.AbstractEncoder;
import org.apache.lucene.analysis.payloads.DelimitedPayloadTokenFilterFactory;
import org.apache.lucene.analysis.payloads.PayloadEncoder;
import org.apache.lucene.util.BytesRef;

/**
 * Encode an OCR information string as a {@link BytesRef}.
 *
 * Not intended to be used directly with {@link DelimitedPayloadTokenFilterFactory}.
 * Use {@link de.digitalcollections.lucene.analysis.util.DelimitedOcrInfoPayloadTokenFilterFactory} instead.
 * For information on the expected format of the payload string, see {@link OcrInfo#parse(char[], int, int, int, int, int)}
 *
 * To use it, configure the {@link de.digitalcollections.lucene.analysis.util.DelimitedOcrInfoPayloadTokenFilterFactory}:
 *
 * ```xml
 * <filter class="org.apache.lucene.analysis.util.DelimitedOcrInfoPayloadTokenFilterFactory"
 *         coordBits="12" wordBits="9" lineBits="11" pageBits="12" />
 * ```
 */
public class OcrInfoEncoder extends AbstractEncoder implements PayloadEncoder {

  private final int coordBits;
  private final int wordBits;
  private final int lineBits;
  private final int pageBits;

  /**
   * Configure a new OcrInfoEncoder.
   *
   * The sum of coordBits*4, wordBits, lineBits and pageBits should be divisible by 8, as not to waste any space in the
   * index.
   *
   * @param coordBits       Number of bits to use for storing the OCR coordinates in the index, must be an even number.
   * @param wordBits        Number of bits to use for storing the word index (0 to disable)
   * @param lineBits        Number of bits to use for storing the line index (0 to disable)
   * @param pageBits        Number of bits to use for storing the page index (0 to disable)
   */
  public OcrInfoEncoder(int coordBits, int wordBits, int lineBits, int pageBits) {
    this.coordBits = coordBits;
    this.wordBits = wordBits;
    this.lineBits = lineBits;
    this.pageBits = pageBits;
  }

  /**
   * Default constructor that encodes with 12bit for the coordinates and doesn't store any indices.
   */
  public OcrInfoEncoder() {
    this(12, 0, 0, 0);
  }

  /**
   * Encode the OCR payload (see {@link OcrInfo#parse(char[], int, int, int, int, int)}
   * be formatted) to a space-efficient binary representation.
   */
  @Override
  public BytesRef encode(char[] chars, int offset, int length) {
    OcrInfo info = OcrInfo.parse(chars, offset, length, wordBits, lineBits, pageBits);
    byte[] data = OcrPayloadHelper.encodeOcrInfo(info, coordBits, wordBits, lineBits, pageBits);
    return new BytesRef(data);
  }
}
