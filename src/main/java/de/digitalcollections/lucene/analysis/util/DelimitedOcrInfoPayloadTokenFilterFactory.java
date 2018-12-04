package de.digitalcollections.lucene.analysis.util;

import de.digitalcollections.lucene.analysis.payloads.OcrInfo;
import de.digitalcollections.lucene.analysis.payloads.OcrInfoEncoder;
import de.digitalcollections.lucene.analysis.payloads.OcrPayloadHelper;
import java.util.Map;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.payloads.DelimitedPayloadTokenFilter;
import org.apache.lucene.analysis.util.TokenFilterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filter factory for space-efficiently encoding OCR information in token payloads.
 *
 * For information on the expected format of the payload string, see
 * {@link OcrInfo#parse(char[], int, int, int, int, int, int, boolean)}
 *
 * Takes the following configuration parameters:
 *
 * `coordinateBits`
 * : Number of bits to use for encoding a coordinate value. 10 bits is recommended (and set by default),
 *   which yields a precision to approximately three decimal places
 *
 * `delimiter`
 * : Delimiting character. If not provided, the pipe symbol (`|`) is used
 *
 * `pageBits`
 * : Number of bits to use for encoding the page index. 0 will disable page indices (default).
 *
 * `lineBits`
 * : Number of bits to use for encoding the line index. 0 will disable line indices (default).
 *
 * `wordBits`
 * : Number of bits to use for encoding the word index. 0 will disable word indices (default).
 *
 * Here is a sample configuration with page indices enabled:
 * ```
 * <pre>{@code
 * <filter class="de.digitalcollections.lucene.analysis.util.DelimitedOcrInfoPayloadTokenFilterFactory"
 *         coordinateBits="10" wordBits="0" lineBits="0" pageBits="12 absoluteCoordinates="false" />
 * }</pre>
 * ```
 */
public class DelimitedOcrInfoPayloadTokenFilterFactory extends TokenFilterFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(OcrPayloadHelper.class);

  private static final String COORD_BITS_ATTR = "coordinateBits";
  private static final String DELIMITER_ATTR = "delimiter";
  private static final String PAGE_BITS_ATTR = "pageBits";
  private static final String LINE_BITS_ATTR = "lineBits";
  private static final String WORD_BITS_ATTR = "wordBits";
  private static final String ABSOLUTE_COORDS_ATTR = "absoluteCoordinates";

  /** Delimiter to use for splitting OCR information from the tokens **/
  private final char delimiter;

  private OcrInfoEncoder encoder;

  public DelimitedOcrInfoPayloadTokenFilterFactory(Map<String, String> args) {
    super(args);
    delimiter = getChar(args, DELIMITER_ATTR, '|');

    /* Number of bits to use for encoding position information */
    final int coordinateBits = getInt(args, COORD_BITS_ATTR, 10);
    final int pageBits = getInt(args, PAGE_BITS_ATTR, 0);
    final int lineBits = getInt(args, LINE_BITS_ATTR, 0);
    final int wordBits = getInt(args, WORD_BITS_ATTR, 0);
    final boolean absoluteCoordinates = getBoolean(args, ABSOLUTE_COORDS_ATTR, false);

    int coordWidth = coordinateBits * 4;
    int remainder = coordWidth % 8;
    if (remainder != 0) {
      throw new IllegalArgumentException("coordinateBits must be an even number.");
    }
    int bitSum = coordWidth + pageBits + lineBits + wordBits;
    remainder = bitSum % 8;
    if (remainder != 0) {
      LOGGER.warn("Final payload size {} is not divisible by 8, will be padded. This is wasting {} bits, try playing "
              + "with the wordBits, lineBits and/or pageBits options.", bitSum, remainder);
    }
    encoder = new OcrInfoEncoder(coordinateBits, wordBits, lineBits, pageBits, absoluteCoordinates);
    if (!args.isEmpty()) {
      throw new IllegalArgumentException("Unknown parameters: " + args);
    }
  }

  @Override
  public TokenStream create(TokenStream input) {
    return new DelimitedPayloadTokenFilter(input, delimiter, encoder);
  }
}
