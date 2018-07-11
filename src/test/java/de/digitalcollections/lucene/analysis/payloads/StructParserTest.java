package de.digitalcollections.lucene.analysis.payloads;

import com.google.common.collect.ImmutableSet;
import org.apache.lucene.analysis.util.ClasspathResourceLoader;
import org.apache.lucene.analysis.util.ResourceLoader;
import org.apache.lucene.util.BytesRef;
import org.assertj.core.util.DoubleComparator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class StructParserTest {
  private static final ResourceLoader loader = new ClasspathResourceLoader(StructParserTest.class.getClassLoader());
  private static PayloadSchema ocrFull;
  private static PayloadSchema allTypes;
  private static PayloadStruct ocrStruct;
  private static PayloadStruct fullStruct;

  @BeforeAll
  public static void initialize() throws IOException {
    ocrFull = PayloadSchema.load(loader, "schemata/ocr_all.yml");
    allTypes = PayloadSchema.load(loader, "schemata/alltypes.yml");
    ocrStruct = PayloadStruct.builder(ocrFull)
        .addInt("page", 27)
        .addInt("line", 54)
        .addInt("word", 78)
        .addPercentage("x", 13.1)
        .addPercentage("y", 52.7)
        .addPercentage("width", 87.9)
        .addPercentage("height", 5.3).build();
    fullStruct = PayloadStruct.builder(allTypes)
        .addInt("intfield", -64)
        .addInt("uintfield", 12)
        .addFloat("halffloat", 7.1878)
        .addFloat("singlefloat", 637.187813373)
        .addFloat("doublefloat", 637.18781337331337)
        .addPercentage("p", 87.137)
        .addBool("boolfield", true)
        .addSet("setfield", ImmutableSet.of("two", "three")).build();
  }

  public static Stream<Arguments> stringFixtures() throws IOException {
    return Stream.of(
        Arguments.of(ocrFull, ocrStruct, "p:27,l:54,n:78,x:13.1,y:52.7,w:87.9,h:5.3"),
        Arguments.of(
            allTypes, fullStruct,
            "i:-64,ui:12,hf:7.1878,sf:637.187813373,df:637.18781337331337,p:87.137,b:true,s:[two,three]"));
  }

  public static Stream<Arguments> byteFixtures() throws IOException {
    return Stream.of(
        Arguments.of(
            ocrFull, ocrStruct,
             new byte[]{(byte) 0x01, (byte) 0xb0, (byte) 0x6c, (byte) 0x4e, (byte) 0x21, (byte) 0xa1, (byte) 0xce,
                        (byte) 0x10, (byte) 0x36}),
        Arguments.of(
            allTypes, fullStruct,
            new byte[]{(byte) 0xff, (byte) 0x03, (byte) 0x11, (byte) 0xcc, (byte) 0x11, (byte) 0x07, (byte) 0xd3,
                       (byte) 0x01, (byte) 0x50, (byte) 0x20, (byte) 0xfa, (byte) 0x60, (byte) 0x29, (byte) 0x13,
                       (byte) 0x10, (byte) 0x43, (byte) 0xf7, (byte) 0xce}));
  }

  @ParameterizedTest
  @MethodSource("stringFixtures")
  public void parseStringPayload(PayloadSchema schema, PayloadStruct struct, String payload) {
    StructParser parser = new StructParser(schema);
    PayloadStruct parsed = parser.fromString(payload);
    assertThat(parsed).isEqualToComparingFieldByFieldRecursively(struct);
  }

  @ParameterizedTest
  @MethodSource("byteFixtures")
  public void parseBytePayload(PayloadSchema schema, PayloadStruct struct, byte[] payload) {
    StructParser parser = new StructParser(schema);
    PayloadStruct parsed = parser.fromBytes(new BytesRef(payload));
    assertThat(parsed)
        .usingComparatorForType(new DoubleComparator(0.05), Double.class)
        .isEqualToComparingFieldByFieldRecursively(struct);
  }

  @Test
  public void keysMustNotBeUsedMultipleTimes() {
    String payload = "p:12,l:13,x:34.5,n:56,x:78.9,y:87.6,w:54.3,h:21";
    StructParser parser = new StructParser(ocrFull);
    assertThatThrownBy(() -> parser.fromString(payload))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid payload p:12,l:13,x:34.5,n:56,x:78.9,y:87.6,w:54.3,h:21: duplicate key 'x'");
  }

  @Test
  public void catchOverFlow() {
    String overFlow = "p:12,l:34,n:512,x:78.9,y:87.6,w:54.3,h:2.1";
    StructParser parser = new StructParser(ocrFull);
    assertThatThrownBy(() -> parser.fromString(overFlow))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Value '512' for field 'word' exceeds configured number of bits (9, legal range is [0, 511])");
  }

  @Test
  public void testBadStringPayload() {
    /*
    String payload = "ui:12,hf:7.1878,sf:637.187813373,df:637.18781337331337,p:87.137,b:true,s:[two,three]";
    StructParser parser = new StructParser(allTypes);
    assertThatThrownBy(() -> parser.fromString(payload.replaceFirst("ui:12", "ui:32")))
        .hasMessageContaining("foobar");
        */
  }

  @Test
  public void missingParametersAreCaught() {
    StructParser parser = new StructParser(ocrFull);
    String missingLine = "p:12,n:56,x:78.9,y:87.6,w:54.3,h:2.1";
    assertThatThrownBy(() -> parser.fromString(missingLine))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Key 'l' is missing");
    String missingWord = "p:12,l:34,x:78.9,y:87.6,w:54.3,h:2.1";
    assertThatThrownBy(() -> parser.fromString(missingWord))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Key 'n' is missing");
    String missingPage = "l:34,n:56,x:78.9,y:87.6,w:54.3,h:2.1";
    assertThatThrownBy(() -> parser.fromString(missingPage))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Key 'p' is missing");
    String missingCoord = "p:12,l:34,n:56,x:78.9,y:87.6,w:54.3";
    assertThatThrownBy(() -> parser.fromString(missingCoord))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Key 'h' is missing");
  }
}