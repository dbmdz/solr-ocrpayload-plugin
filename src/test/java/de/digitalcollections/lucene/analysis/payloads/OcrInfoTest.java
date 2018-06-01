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
        Arguments.of(new OcrInfo(27, .131f, .527f, .879f, .053f), "p27x131y527w879h053"),
        Arguments.of(new OcrInfo(.131f, .527f, .879f, .053f), "x131y527w879h053"),
        Arguments.of(new OcrInfo(-1, 50, .123f, .456f, .789f, .091f), "l50x123y456w789h091"),
        Arguments.of(new OcrInfo(123,456,.123f,.456f, .234f, .456f), "p123l456x123y456w234h456"),
        Arguments.of(new OcrInfo( 123, 456, 511, .123f, .234f, .345f, .456f), "p123l456n511x123y234w345h456"),
        Arguments.of(new OcrInfo(123, -1, 456, .123f, .234f, .345f, .456f), "p123n456x123y234w345h456"),
        Arguments.of(new OcrInfo( 123, 456, 511, .1234f, .2345f, .3456f, .4567f), "p123l456n511x1234y2345w3456h4567")
    );
  }

  @ParameterizedTest
  @MethodSource("fixtureProvider")
  public void parseFromBeginning(OcrInfo info, String payload) {
    char[] buf = toChars(payload);
    OcrInfo parsed = OcrInfo.parse(
        buf, 0, payload.length(),
        info.getWordIndex() > 0 ? 9 : 0, info.getLineIndex() > 0 ? 11 : 0, info.getPageIndex() > 0 ? 12 : 0);
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
        info.getWordIndex() > 0 ? 9 : 0, info.getLineIndex() > 0 ? 11 : 0, info.getPageIndex() > 0 ? 12 : 0);
    assertThat(parsed).isEqualToComparingFieldByField(info);
  }

  @Test
  public void keysMustNotBeUsedMultipleTimes() {
    String payload = "p12x345n56x789y876w543h21";
    assertThatThrownBy(() -> OcrInfo.parse(toChars(payload), 0, payload.length(), 9, 11, 12))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid payload p12x345n56x789y876w543h21: duplicate key 'x'");
  }

  @Test
  public void catchOverFlow() {
    String overflow = "p12l34n512x789y876w543h021";
    assertThatThrownBy(() -> OcrInfo.parse(toChars(overflow), 0, overflow.length(), 9, 11, 12))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("512 needs more than 9 bits (valid values range from 0 to 511)");
  }

  @Test
  public void missingParametersAreCaught() {
    String missingLine = "p12n56x789y876w543h021";
    assertThatThrownBy(() -> OcrInfo.parse(toChars(missingLine), 0, missingLine.length(), 9, 11, 12))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fix payload or set the 'lineBits' option to 0.");
    String missingWord = "p12l34x789y876w543h021";
    assertThatThrownBy(() -> OcrInfo.parse(toChars(missingWord), 0, missingWord.length(), 9, 11, 12))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fix payload or set the 'wordBits' option to 0.");
    String missingPage = "l34n56x789y876w543h021";
    assertThatThrownBy(() -> OcrInfo.parse(toChars(missingPage), 0, missingPage.length(), 9, 11, 12))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fix payload or set the 'pageBits' option to 0.");
    String missingCoord = "p12l34n56x789y876w543";
    assertThatThrownBy(() -> OcrInfo.parse(toChars(missingCoord), 0, missingCoord.length(), 9, 11, 12))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("coordinates are missing from payload ");
  }
}