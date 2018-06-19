package de.digitalcollections.lucene.analysis.payloads;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static de.digitalcollections.lucene.analysis.payloads.TestUtils.toChars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OcrInfoTest {
  public static Stream<Arguments> fixtureProvider() {
    return Stream.of(
        Arguments.of(new OcrInfo(27, .131f, .527f, .879f, .053f), "p:27,x:13.1,y:52.7,w:87.9,h:5.3"),
        Arguments.of(new OcrInfo(.131f, .527f, .879f, .053f), "x:13.1,y:52.7,w:87.9,h:5.3"),
        Arguments.of(new OcrInfo(-1, 50, .123f, .456f, .789f, .091f), "l:50,x:12.3,y:45.6,w:78.9,h:9.1"),
        Arguments.of(new OcrInfo(123,456,.123f,.456f, .234f, .456f), "p:123,l:456,x:12.3,y:45.6,w:23.4,h:45.6"),
        Arguments.of(new OcrInfo( 123, 456, 511, .123f, .234f, .345f, .456f), "p:123,l:456,n:511,x:12.3,y:23.4,w:34.5,h:45.6"),
        Arguments.of(new OcrInfo(123, -1, 456, .123f, .234f, .345f, .456f), "p:123,n:456,x:12.3,y:23.4,w:34.5,h:45.6"),
        Arguments.of(new OcrInfo( 123, 456, 511, .1234f, .2345f, .3456f, .4567f), "p:123,l:456,n:511,x:12.34,y:23.45,w:34.56,h:45.67"),
        Arguments.of(new OcrInfo(123, 456, 511, 768, 1024, 2048, 4095),
                     "p:123,l:456,n:511,x:768,y:1024,w:2048,h:4095")
    );
  }

  @ParameterizedTest
  @MethodSource("fixtureProvider")
  public void parseFromBeginning(OcrInfo info, String payload) {
    char[] buf = toChars(payload);
    OcrInfo parsed = OcrInfo.parse(
        buf, 0, payload.length(),
        info.getWordIndex() > 0 ? 9 : 0, info.getLineIndex() > 0 ? 11 : 0, info.getPageIndex() > 0 ? 12 : 0,
        12,
        info.getHasAbsoluteCoordinates());
    assertThat(parsed).isEqualToComparingFieldByField(info);
  }

  @ParameterizedTest
  @MethodSource("fixtureProvider")
  public void parseFromPosition(OcrInfo info, String payload) {
    String padding = "someToken|";
    String padded = padding + payload;
    char[] buf = toChars(padded);
    OcrInfo parsed = OcrInfo.parse(
        buf, padding.length(), payload.length(),
        info.getWordIndex() > 0 ? 9 : 0, info.getLineIndex() > 0 ? 11 : 0, info.getPageIndex() > 0 ? 12 : 0,
        12,
        info.getHasAbsoluteCoordinates());
    assertThat(parsed).isEqualToComparingFieldByField(info);
  }

  @Test
  public void keysMustNotBeUsedMultipleTimes() {
    String payload = "p:12,x:34.5,n:56,x:78.9,y:87.6,w:54.3,h:21";
    assertThatThrownBy(() -> OcrInfo.parse(toChars(payload), 0, payload.length(), 9, 11, 12, 12, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid payload p:12,x:34.5,n:56,x:78.9,y:87.6,w:54.3,h:21: duplicate key 'x'");
  }

  @Test
  public void catchOverFlow() {
    String idxOverflow = "p:12,l:34,n:512,x:78.9,y:87.6,w:54.3,h:2.1";
    assertThatThrownBy(() -> OcrInfo.parse(toChars(idxOverflow), 0, idxOverflow.length(), 9, 11, 12, 12, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("512 needs more than 9 bits (valid values range from 0 to 511)");
    String coordOverFlow = "p:1,l:2,n:3,x:4096,y:2048,w:1024,h:512";
    assertThatThrownBy(() -> OcrInfo.parse(toChars(coordOverFlow), 0, coordOverFlow.length(), 9, 11, 12, 12, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("4096 needs more than 12 bits (valid values range from 0 to 4095)");
  }

  @Test
  public void missingParametersAreCaught() {
    String missingLine = "p:12,n:56,x:78.9,y:87.6,w:54.3,h:2.1";
    assertThatThrownBy(() -> OcrInfo.parse(toChars(missingLine), 0, missingLine.length(), 9, 11, 12, 12, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fix payload or set the 'lineBits' option to 0.");
    String missingWord = "p:12,l:34,x:78.9,y:87.6,w:54.3,h:2.1";
    assertThatThrownBy(() -> OcrInfo.parse(toChars(missingWord), 0, missingWord.length(), 9, 11, 12, 12, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fix payload or set the 'wordBits' option to 0.");
    String missingPage = "l:34,n:56,x:78.9,y:87.6,w:54.3,h:2.1";
    assertThatThrownBy(() -> OcrInfo.parse(toChars(missingPage), 0, missingPage.length(), 9, 11, 12, 12, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fix payload or set the 'pageBits' option to 0.");
    String missingCoord = "p:12,l:34,n:56,x:78.9,y:87.6,w:54.3";
    assertThatThrownBy(() -> OcrInfo.parse(toChars(missingCoord), 0, missingCoord.length(), 9, 11, 12, 12, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("coordinates are missing from payload ");
  }
}