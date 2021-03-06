package de.digitalcollections.lucene.analysis.payloads;

import com.google.common.collect.ImmutableMap;
import org.apache.lucene.util.BytesRef;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static de.digitalcollections.lucene.analysis.payloads.TestUtils.toChars;
import static org.assertj.core.api.Assertions.assertThat;

public class OcrInfoEncoderTest {

  public static Stream<Arguments> data() {
    byte[] withPage = {(byte) 0x1b, (byte) 0x21, (byte) 0xa1, (byte) 0xce, (byte) 0x10, (byte) 0x36};
    byte[] withoutPage = {(byte) 0x21, (byte) 0xa1, (byte) 0xce, (byte) 0x10, (byte) 0x36};
    byte[] withPosition = {(byte) 0x20, (byte) 0x21, (byte) 0xa1, (byte) 0xce, (byte) 0x10, (byte) 0x36};
    byte[] withPositionAndPage = {(byte) 0x36, (byte) 0x20, (byte) 0x21, (byte) 0xa1, (byte) 0xce, (byte) 0x10, (byte) 0x36};
    byte[] withPageLineWord = {(byte) 0x01, (byte) 0xb0, (byte) 0x54, (byte) 0x0c, (byte) 0x1f, (byte) 0x8f, (byte) 0x05, (byte) 0x85, (byte) 0xd3};
    byte[] withPageLineWordAbsolute = {(byte) 0x01, (byte) 0x60, (byte) 0x42, (byte) 0x2c, (byte) 0x03, (byte) 0x0a, (byte) 0x08, (byte) 0x90, (byte) 0x0f, (byte) 0xa0, (byte) 0x03, (byte) 0x70};
    Map<String, byte[]> params = ImmutableMap.<String, byte[]>builder()
            .put("withPageooo|p:27,x:13.1,y:52.7,w:87.9,h:5.3,", withPage)
            .put("withoutPage|x:13.1,y:52.7,w:87.9,h:5.3,", withoutPage)
            .put("withWordoooo|n:32,x:13.1,y:52.7,w:87.9,h:5.3,", withPosition)
            .put("withPageWord|p:27,n:32,x:13.1,y:52.7,w:87.9,h:5.3,", withPositionAndPage)
            .put("withPageLineWord|p:27,l:42,n:12,x:12.3,y:23.4,w:34.5,h:45.6,", withPageLineWord)
            .put("withPageLineWordAbsolute|p:22,l:33,n:44,x:778,y:2192,w:4000,h:880", withPageLineWordAbsolute)
            .build();
    return params.entrySet().stream().map(e -> Arguments.of(e.getKey(), e.getValue()));
  }

  @ParameterizedTest
  @MethodSource("data")
  public void encode(String tokenFixture, byte[] bytesFixture) {
    OcrInfoEncoder encoder = new OcrInfoEncoder(
        tokenFixture.contains("Absolute") ? 16 : 10,
        tokenFixture.contains("Word") ? 9 : 0,
        tokenFixture.contains("Line") ? 11 : 0,
        tokenFixture.contains("withPage") ? 12 : 0,
        tokenFixture.contains("Absolute"));

    BytesRef bytes = encoder.encode(
            toChars(tokenFixture),
            tokenFixture.indexOf("|") + 1,
            tokenFixture.length() - tokenFixture.indexOf("|") -1 );
    assertThat(bytes.bytes).isEqualTo(bytesFixture);
  }
}
