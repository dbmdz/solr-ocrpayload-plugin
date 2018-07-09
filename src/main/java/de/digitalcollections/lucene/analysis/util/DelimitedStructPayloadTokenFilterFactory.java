package de.digitalcollections.lucene.analysis.util;

import de.digitalcollections.lucene.analysis.payloads.PayloadSchema;
import de.digitalcollections.lucene.analysis.payloads.StructEncoder;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.payloads.DelimitedPayloadTokenFilter;
import org.apache.lucene.analysis.util.ResourceLoader;
import org.apache.lucene.analysis.util.ResourceLoaderAware;
import org.apache.lucene.analysis.util.TokenFilterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

public class DelimitedStructPayloadTokenFilterFactory extends TokenFilterFactory implements ResourceLoaderAware {
  private static final Logger LOGGER = LoggerFactory.getLogger(DelimitedStructPayloadTokenFilterFactory.class);

  private static final String SCHEMA_ATTR = "schema";
  private static final String DELIMITER_ATTR = "delimiter";

  /** Delimiter to use for splitting OCR information from the tokens **/
  private final char delimiter;

  /** Schema used for parsing and serializing the payloads **/
  private String schemaPath;
  private StructEncoder encoder;

  public DelimitedStructPayloadTokenFilterFactory(Map<String, String> args) {
    super(args);
    delimiter = getChar(args, DELIMITER_ATTR, '|');
    schemaPath = require(args, SCHEMA_ATTR);
    if (!args.isEmpty()) {
      throw new IllegalArgumentException("Unknown parameters: " + args);
    }
  }

  @Override
  public TokenStream create(TokenStream input) {
    return new DelimitedPayloadTokenFilter(input, delimiter, encoder);
  }

  @Override
  public void inform(ResourceLoader loader) throws IOException {
    PayloadSchema schema = PayloadSchema.load(loader, this.schemaPath);
    int payloadSize = schema.getSize();
    int wastedBits = payloadSize % 8;
    if (wastedBits != 0) {
      LOGGER.warn("Final payload size {} is not divisible by 8, will be padded. This is wasting {} bits, try playing " +
          "with the wordBits, lineBits and/or pageBits options.", payloadSize, wastedBits);
    }
    this.encoder = new StructEncoder(schema);
  }
}
