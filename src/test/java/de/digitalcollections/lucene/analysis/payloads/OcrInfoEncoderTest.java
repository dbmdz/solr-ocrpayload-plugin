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
    Map<String, byte[]> params = ImmutableMap.of(
            "withPageooo|p27x131y527w879h053", withPage,
            "withoutPage|x131y527w879h053", withoutPage,
            "withWordoooo|n32x131y527w879h053", withPosition,
            "withPageWord|p27n32x131y527w879h053", withPositionAndPage,
        "withPageLineWord|p27l42n12x123y234w345h456", withPageLineWord);
    return params.entrySet().stream().map(e -> Arguments.of(e.getKey(), e.getValue()));
  }

  @ParameterizedTest
  @MethodSource("data")
  public void encode(String tokenFixture, byte[] bytesFixture) {
    OcrInfoEncoder encoder = new OcrInfoEncoder(
        10, tokenFixture.contains("Word") ? 9 : 0, tokenFixture.contains("Line") ? 11 : 0, tokenFixture.contains("withPage") ? 12 : 0);

    BytesRef bytes = encoder.encode(
            toChars(tokenFixture),
            tokenFixture.indexOf("|") + 1,
            tokenFixture.length() - tokenFixture.indexOf("|") -1 );
    assertThat(bytes.bytes).isEqualTo(bytesFixture);
  }
}
