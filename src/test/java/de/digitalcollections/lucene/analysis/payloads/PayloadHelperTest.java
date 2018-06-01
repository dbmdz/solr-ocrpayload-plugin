package de.digitalcollections.lucene.analysis.payloads;

import org.apache.lucene.util.BytesRef;
import org.assertj.core.data.Offset;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;


public class PayloadHelperTest {
  public static Stream<Arguments> fixtureProvider() {
    byte[] withPage = {(byte) 0x1b, (byte) 0x21, (byte) 0xa1, (byte) 0xce, (byte) 0x10, (byte) 0x36};
    byte[] withoutPage = {(byte)0x23, (byte)0x1e, (byte)0x9f, (byte)0xa8, (byte)0x36};
    byte[] withPosition = {(byte) 0x20, (byte) 0x21, (byte) 0xa1, (byte) 0xce, (byte) 0x10, (byte) 0x36};
    byte[] withPositionAndPage = {(byte) 0x0, (byte) 0xd8, (byte) 0x20, (byte) 0x21, (byte) 0xa1, (byte) 0xce, (byte) 0x10, (byte) 0x36};
    return Stream.of(
        Arguments.of(new OcrInfo(27, .131f, .527f, .879f, .053f), withPage),
        Arguments.of(new OcrInfo(.1368f, .4779f, .9782f, .0532f), withoutPage),
        Arguments.of(new OcrInfo(-1, 32, .131f, .527f, .879f, .053f), withPosition),
        Arguments.of(new OcrInfo(27, 32, .131f, .527f, .879f, .053f), withPositionAndPage));
  }

  private void assertAreAboutEqual(OcrInfo a, OcrInfo b) {
    assertThat(a.getHorizontalOffset()).isCloseTo(b.getHorizontalOffset(), Offset.offset(0.09f));
    assertThat(a.getVerticalOffset()).isCloseTo(b.getVerticalOffset(), Offset.offset(0.09f));
    assertThat(a.getWidth()).isCloseTo(b.getWidth(), Offset.offset(0.09f));
    assertThat(a.getHeight()).isCloseTo(b.getHeight(), Offset.offset(0.09f));
  }

  @ParameterizedTest
  @MethodSource("fixtureProvider")
  public void encodeOcrInfo(OcrInfo ocrInfo, byte[] payload) {
    byte[] encodedInfo = OcrPayloadHelper.encodeOcrInfo(
        ocrInfo, 10,
        ocrInfo.getWordIndex() >= 0 ? 9 : 0, ocrInfo.getLineIndex() >= 0 ? 11 : 0, ocrInfo.getPageIndex() >= 0 ? 12 : 0);
    assertThat(encodedInfo).isEqualTo(payload);
  }

  @ParameterizedTest
  @MethodSource("fixtureProvider")
  public void decodeOcrInfo(OcrInfo ocrInfo, byte[] payload) {
    OcrInfo decodedInfo = OcrPayloadHelper.decodeOcrInfo(new BytesRef(payload), 10,
          ocrInfo.getWordIndex() >= 0 ? 9 : 0, ocrInfo.getLineIndex() >= 0 ? 11 : 0, ocrInfo.getPageIndex() >= 0 ? 12 : 0);
    assertAreAboutEqual(decodedInfo, ocrInfo);
  }

  @ParameterizedTest
  @MethodSource("fixtureProvider")
  public void doesNotDegradeAccuracy(OcrInfo ocrInfo, byte[] payload) {
    byte[] encodedInfo;
    OcrInfo decodedInfo = ocrInfo;
    for (int i=0; i < 100; i++) {
      encodedInfo = OcrPayloadHelper.encodeOcrInfo(decodedInfo, 10, 9, 11, 12);
      decodedInfo = OcrPayloadHelper.decodeOcrInfo(new BytesRef(encodedInfo), 10,
           ocrInfo.getWordIndex() >= 0 ? 9 : 0,  ocrInfo.getLineIndex() >= 0 ? 11 : 0, ocrInfo.getPageIndex() >= 0 ? 12 : 0);
      assertAreAboutEqual(decodedInfo, ocrInfo);
    }
  }
}