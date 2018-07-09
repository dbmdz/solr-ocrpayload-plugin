package de.digitalcollections.lucene.analysis.payloads;

public class PayloadHelperTest {
  /*
  public static Stream<Arguments> fixtureProvider() {
    byte[] withPage = {(byte) 0x1b, (byte) 0x21, (byte) 0xa1, (byte) 0xce, (byte) 0x10, (byte) 0x36};
    byte[] withoutPage = {(byte)0x23, (byte)0x1e, (byte)0x9f, (byte)0xa8, (byte)0x36};
    byte[] withPosition = {(byte) 0x20, (byte) 0x21, (byte) 0xa1, (byte) 0xce, (byte) 0x10, (byte) 0x36};
    byte[] withPositionAndPage = {(byte) 0x0, (byte) 0xd8, (byte) 0x20, (byte) 0x21, (byte) 0xa1, (byte) 0xce, (byte) 0x10, (byte) 0x36};
    byte[] withPageLineWordAbsolute = {(byte) 0x01, (byte) 0x60, (byte) 0x42, (byte) 0x2c, (byte) 0x30, (byte) 0xa8, (byte) 0x90, (byte) 0x19, (byte) 0x03, (byte) 0x70};
    return Stream.of(
        Arguments.of(new OcrInfo(27, .131f, .527f, .879f, .053f), withPage),
        Arguments.of(new OcrInfo(.1368f, .4779f, .9782f, .0532f), withoutPage),
        Arguments.of(new OcrInfo(-1, 32, .131f, .527f, .879f, .053f), withPosition),
        Arguments.of(new OcrInfo(27, 32, .131f, .527f, .879f, .053f), withPositionAndPage),
        Arguments.of(new OcrInfo(22, 33, 44, 778, 2192, 400, 880),
                     withPageLineWordAbsolute));
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
        ocrInfo,
        ocrInfo.getHasAbsoluteCoordinates() ? 12 : 10,
        ocrInfo.getWordIndex() >= 0 ? 9 : 0,
        ocrInfo.getLineIndex() >= 0 ? 11 : 0,
        ocrInfo.getPageIndex() >= 0 ? 12 : 0);
    assertThat(encodedInfo).isEqualTo(payload);
  }

  @ParameterizedTest
  @MethodSource("fixtureProvider")
  public void decodeOcrInfo(OcrInfo ocrInfo, byte[] payload) {
    OcrInfo decodedInfo = OcrPayloadHelper.decodeOcrInfo(
        new BytesRef(payload),
        ocrInfo.getHasAbsoluteCoordinates() ? 12: 10,
        ocrInfo.getWordIndex() >= 0 ? 9 : 0,
        ocrInfo.getLineIndex() >= 0 ? 11 : 0,
        ocrInfo.getPageIndex() >= 0 ? 12 : 0,
        ocrInfo.getHasAbsoluteCoordinates());
    if (ocrInfo.getHasAbsoluteCoordinates()) {
      assertThat(decodedInfo).isEqualToComparingFieldByField(ocrInfo);
    } else {
      assertAreAboutEqual(decodedInfo, ocrInfo);
    }
  }

  @ParameterizedTest
  @MethodSource("fixtureProvider")
  public void doesNotDegradeAccuracy(OcrInfo ocrInfo, byte[] payload) {
    if (ocrInfo.getHasAbsoluteCoordinates()) {
      // NOP, there's no risk of degradation with integers
      return;
    }
    byte[] encodedInfo;
    OcrInfo decodedInfo = ocrInfo;
    for (int i=0; i < 100; i++) {
      encodedInfo = OcrPayloadHelper.encodeOcrInfo(decodedInfo, 10, 9, 11, 12);
      decodedInfo = OcrPayloadHelper.decodeOcrInfo(new BytesRef(encodedInfo), 10,
           ocrInfo.getWordIndex() >= 0 ? 9 : 0,  ocrInfo.getLineIndex() >= 0 ? 11 : 0, ocrInfo.getPageIndex() >= 0 ? 12 : 0, false);
      assertAreAboutEqual(decodedInfo, ocrInfo);
    }      assertAreAboutEqual(decodedInfo, ocrInfo);
  }
  */
}