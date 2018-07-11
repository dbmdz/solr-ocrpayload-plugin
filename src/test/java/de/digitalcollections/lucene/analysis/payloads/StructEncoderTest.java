package de.digitalcollections.lucene.analysis.payloads;

import org.apache.lucene.analysis.util.ClasspathResourceLoader;
import org.apache.lucene.analysis.util.ResourceLoader;
import org.apache.lucene.util.BytesRef;
import org.assertj.core.util.DoubleComparator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class StructEncoderTest {
  public static Stream<Arguments> data() throws IOException {
    ResourceLoader loader = new ClasspathResourceLoader(StructEncoderTest.class.getClassLoader());
    return Stream.<Arguments>builder()
        .add(Arguments.of(
            "withoutPage|x:13.1,y:52.7,w:87.9,h:5.3",
            new byte[]{(byte) 0x21, (byte) 0xa1, (byte) 0xce, (byte) 0x10, (byte) 0x36},
            PayloadSchema.load(loader, "schemata/ocr_minimal.yml")))
        .add(Arguments.of(
            "withPageooo|p:27,x:13.1,y:52.7,w:87.9,h:5.3",
            new byte[]{(byte) 0x1b, (byte) 0x21, (byte) 0xa1, (byte) 0xce, (byte) 0x10, (byte) 0x36},
            PayloadSchema.load(loader, "schemata/ocr_page.yml")))
        .add(Arguments.of(
            "withWordoooo|n:32,x:13.1,y:52.7,w:87.9,h:5.3",
            new byte[]{(byte) 0x20, (byte) 0x21, (byte) 0xa1, (byte) 0xce, (byte) 0x10, (byte) 0x36},
            PayloadSchema.load(loader, "schemata/ocr_word.yml")))
        .add(Arguments.of(
            "withPageWord|p:27,n:32,x:13.1,y:52.7,w:87.9,h:5.3",
            new byte[]{(byte) 0x36, (byte) 0x20, (byte) 0x21, (byte) 0xa1, (byte) 0xce, (byte) 0x10,
                       (byte) 0x36},
            PayloadSchema.load(loader, "schemata/ocr_page_word.yml")))
        .add(Arguments.of(
            "withPageLineWord|p:27,l:42,n:12,x:12.3,y:23.4,w:34.5,h:45.6",
            new byte[]{(byte) 0x01, (byte) 0xb0, (byte) 0x54, (byte) 0x0c, (byte) 0x1f, (byte) 0x8f, (byte) 0x05,
                       (byte) 0x85, (byte) 0xd3},
            PayloadSchema.load(loader, "schemata/ocr_all.yml")))
        .add(Arguments.of(
            "withPageLineWordAbsolute|p:22,l:33,n:44,x:778,y:2192,w:4000,h:880",
            new byte[]{(byte) 0x01, (byte) 0x60, (byte) 0x42, (byte) 0x2c, (byte) 0x03, (byte) 0x0a, (byte) 0x08,
                       (byte) 0x90, (byte) 0x0f, (byte) 0xa0, (byte) 0x03, (byte) 0x70},
            PayloadSchema.load(loader, "schemata/ocr_all_absolute.yml")))
        .add(Arguments.of(
            "allTypes|i:-64, ui:12,hf:8.737,sf:87.37373,df:8737.12317575609,p:27.88,b:y,s:[two,three]",
            new byte[]{(byte) 0xff, (byte) 0x03, (byte) 0x12, (byte) 0x17, (byte) 0x90, (byte) 0xab, (byte) 0xaf,
                       (byte) 0xd6, (byte) 0x90, (byte) 0x30, (byte) 0x44, (byte) 0x23, (byte) 0xf1, (byte) 0x0e,
                       (byte) 0x48, (byte) 0x82, (byte) 0x51, (byte) 0xde},
            PayloadSchema.load(loader, "schemata/alltypes.yml")))
        .build();
  }

  @ParameterizedTest
  @MethodSource("data")
  public void encode(String tokenFixture, byte[] bytesFixture, PayloadSchema schema) {
    StructEncoder encoder = new StructEncoder(schema);
    BytesRef bytes = encoder.encode(
            TestUtils.toChars(tokenFixture),
            tokenFixture.indexOf("|") + 1,
            tokenFixture.length() - tokenFixture.indexOf("|") -1 );
    assertThat(bytes.bytes).isEqualTo(bytesFixture);
  }

  @ParameterizedTest
  @MethodSource("data")
  public void roundTrip(String tokenFixture, byte[] bytesFixture, PayloadSchema schema) {
    StructParser parser = new StructParser(schema);
    StructEncoder encoder = new StructEncoder(schema);
    PayloadStruct struct = parser.fromString(tokenFixture.split("\\|")[1]);
    BytesRef bytes = new BytesRef(encoder.serializeStruct(struct));
    assertThat(parser.fromBytes(bytes))
        .usingComparatorForType(new DoubleComparator(0.05), Double.class)
        .isEqualToComparingFieldByFieldRecursively(struct);
  }
}
