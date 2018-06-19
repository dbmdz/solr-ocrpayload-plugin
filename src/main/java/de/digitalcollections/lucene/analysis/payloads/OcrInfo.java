package de.digitalcollections.lucene.analysis.payloads;

import com.google.common.math.IntMath;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OcrInfo implements Comparable<OcrInfo> {
  private static final Pattern PAYLOAD_PAT = Pattern.compile("(\\D+):([0-9.]+),?");

  private boolean hasAbsoluteCoordinates = false;
  private float horizontalOffset = -1.0f;
  private float verticalOffset = -1.0f;
  private float width = -1.0f;
  private float height = -1.0f;
  private int pageIndex = -1;
  private int lineIndex = -1;
  private int wordIndex = -1;

  private String term; // optional, only when returning search results

  OcrInfo() {
    // NOP
  }

  public OcrInfo(int horizontalOffset, int verticalOffset, int width, int height) {
    this(-1, horizontalOffset, verticalOffset, width, height);
    this.setHasAbsoluteCoordinates(true);
  }

  public OcrInfo(int pageIndex, int horizontalOffset, int verticalOffset, int width, int height) {
    this(pageIndex, -1, -1, horizontalOffset, verticalOffset, width, height);
    this.setHasAbsoluteCoordinates(true);
  }

  public OcrInfo(int pageIndex, int lineIndex, int horizontalOffset, int verticalOffset, int width, int height) {
    this(pageIndex, lineIndex, -1, horizontalOffset, verticalOffset, width, height);
    this.setHasAbsoluteCoordinates(true);
  }

  public OcrInfo(int pageIndex, int lineIndex, int wordIndex, int horizontalOffset, int verticalOffset, int width, int height) {
    this.setHasAbsoluteCoordinates(true);
    this.setHorizontalOffset(horizontalOffset);
    this.setVerticalOffset(verticalOffset);
    this.setWidth(width);
    this.setHeight(height);
    this.setPageIndex(pageIndex);
    this.setLineIndex(lineIndex);
    this.setWordIndex(wordIndex);
  }

  public OcrInfo(float horizontalOffset, float verticalOffset, float width, float height) {
    this(-1, horizontalOffset, verticalOffset, width, height);
  }

  public OcrInfo(int pageIndex, float horizontalOffset, float verticalOffset, float width, float height) {
    this.setHorizontalOffset(horizontalOffset);
    this.setVerticalOffset(verticalOffset);
    this.setWidth(width);
    this.setHeight(height);
    this.setPageIndex(pageIndex);
  }

  public OcrInfo(int pageIndex, int lineIndex, float horizontalOffset, float verticalOffset, float width, float height) {
    this(pageIndex, horizontalOffset, verticalOffset, width, height);
    this.lineIndex = lineIndex;
  }

  public OcrInfo(int pageIndex, int lineIndex, int wordIndex, float horizontalOffset, float verticalOffset, float width, float height) {
    this(pageIndex, lineIndex, horizontalOffset, verticalOffset, width, height);
    this.wordIndex = wordIndex;
  }

  /**
   * Parse an {@link OcrInfo} object from a character buffer.
   *
   * The string contains comma-separated pairs of single-character keys and numerical
   * values, e.g. `x:13.37`.
   *
   * Valid keys are:
   * - **p**: Page index, ranging from 0 to 2^pageBits (optional)
   * - **l**: Line index, ranging from 0 to 2^lineBits (optional)
   * - **n**: Word index, ranging from 0 to 2^wordBits (optional)
   * - **x**: Horizontal offset as floating point percentage in range [0...100]
   *          OR absolute position as unsigned integer in range [0...2^coordBits] (mandatory)
   * - **y**: Vertical offset as floating point percentage in range [0...100]
   *          OR absolute position as unsigned integer in range [0...2^coordBits] (mandatory)
   * - **w**: Width as floating point percentage in range [0...100]
   *          OR absolute position as unsigned integer in range [0...2^coordBits] (mandatory)
   * - **h**: Height as floating point percentage in range [0...100]
   *          OR absolute position as unsigned integer in range [0...2^coordBits] (mandatory)
   *
   * Here es an example: `p:27,l:50,n:13,x:13.1,y:52.7,w:87.9,h:5.3`
   * or, with integral (absolute) coordinate
   *
   * @param buffer Input character buffer
   * @param offset Offset of the encoded character information
   * @param length Length of the encoded character information
   * @param wordBits Number of bits used for encoding the word index
   * @param lineBits Number of bits used for encoding the line index
   * @param pageBits Number of bits used for encoding the page index
   * @param coordBits Number of bits used for encoding the coordinates
   * @param absoluteCoordinates Whether the coordinates are stored absolute or relative (percent-values)
   * @return The decoded {@link OcrInfo} instance
   */
  public static OcrInfo parse(char[] buffer, int offset, int length, int wordBits, int lineBits, int pageBits,
                              int coordBits, boolean absoluteCoordinates) {
    OcrInfo info = new OcrInfo();
    info.setHasAbsoluteCoordinates(absoluteCoordinates);

    String payload = new String(buffer, offset, length).toLowerCase();
    Matcher m = PAYLOAD_PAT.matcher(payload);
    Set<Character> seenKeys = new HashSet<>();
    while (m.find()) {
      char key = m.group(1).charAt(0);
      if (seenKeys.contains(key)) {
        throw new IllegalArgumentException(String.format("Invalid payload %s: duplicate key '%c'", payload, key));
      } else {
        seenKeys.add(key);
      }
      String value = m.group(2);
      switch (key) {
        case 'p':
          info.setPageIndex(parseIntValue(value, pageBits)); break;
        case 'l':
          info.setLineIndex(parseIntValue(value, lineBits)); break;
        case 'n':
          info.setWordIndex(parseIntValue(value, wordBits)); break;
        case 'x':
          if (absoluteCoordinates) {
            info.setHorizontalOffset(parseIntValue(value, coordBits));
          } else {
            info.setHorizontalOffset(Float.parseFloat(value)/100f);
          }
          break;
        case 'y':
          if (absoluteCoordinates) {
            info.setVerticalOffset(parseIntValue(value, coordBits));
          } else {
            info.setVerticalOffset(Float.parseFloat(value)/100f);
          }
          break;
        case 'w':
          if (absoluteCoordinates) {
            info.setWidth(parseIntValue(value, coordBits));
          } else {
            info.setWidth(Float.parseFloat(value)/100f);
          }
          break;
        case 'h':
          if (absoluteCoordinates) {
            info.setHeight(parseIntValue(value, coordBits));
          } else {
            info.setHeight(Float.parseFloat(value)/100f);
          }
          break;
        default:
          throw new IllegalArgumentException(String.format(
              "Could not parse OCR bounding box information, string was %s, invalid character was %c",
              new String(buffer, offset, length), key));
      }
    }
    if (info.getHorizontalOffset() < 0 || info.getHorizontalOffset() < 0 || info.getWidth() < 0 || info.getHeight() < 0) {
      throw new IllegalArgumentException(String.format(
          "One or more coordinates are missing from payload (was %s), make sure you have 'x', 'y', 'w' and 'h' set!",
          payload));
    }
    if (pageBits > 0 && info.getPageIndex() < 0) {
      throw new IllegalArgumentException(String.format(
          "Page index is missing from payload (was: '%s'), fix payload or set the 'pageBits' option to 0.", payload));
    }
    if (lineBits > 0 && info.getLineIndex() < 0) {
      throw new IllegalArgumentException(String.format(
          "Line index is missing from payload (was: '%s'), fix payload or set the 'lineBits' option to 0.", payload));
    }
    if (wordBits > 0 && info.getWordIndex() < 0) {
      throw new IllegalArgumentException(String.format(
          "Word index is missing from payload (was: '%s'), fix payload or set the 'wordBits' option to 0.", payload));
    }
    return info;
  }

  private static int parseIntValue(String value, int numBits) {
    int index = Integer.parseInt(value);
    if (index >= IntMath.pow(2, numBits)) {
      throw new IllegalArgumentException(String.format("Value %d needs more than %d bits (valid values range from 0 to %d).",
                                                       index, numBits, IntMath.pow(2, numBits) - 1));
    }
    return index;
  }

  public float getHorizontalOffset() {
    return horizontalOffset;
  }

  public void setHorizontalOffset(float horizontalOffset) {
    this.horizontalOffset = horizontalOffset;
  }

  private void checkCoordinate(float coordinate) {
    if (coordinate > 1) {
      throw new IllegalArgumentException(String.format("Coordinates can at most be 100, was %1f!", coordinate*100));
    }
  }

  public float getVerticalOffset() {
    return verticalOffset;
  }

  public void setVerticalOffset(float verticalOffset) {
    if (!hasAbsoluteCoordinates) {
      checkCoordinate(verticalOffset);
    }
    this.verticalOffset = verticalOffset;
  }

  public float getWidth() {
    return width;
  }

  public void setWidth(float width) {
    if (!hasAbsoluteCoordinates) {
      checkCoordinate(width);
    }
    this.width = width;
  }

  public float getHeight() {
    return height;
  }

  public void setHeight(float height) {
    if (!hasAbsoluteCoordinates) {
      checkCoordinate(height);
    }
    this.height = height;
  }

  public int getPageIndex() {
    return pageIndex;
  }

  public void setPageIndex(int pageIndex) {
    this.pageIndex = pageIndex;
  }

  public String getTerm() {
    return term;
  }

  public void setTerm(String term) {
    this.term = term;
  }

  public int getLineIndex() {
    return lineIndex;
  }

  public void setLineIndex(int lineIndex) {
    this.lineIndex = lineIndex;
  }

  public int getWordIndex() {
    return wordIndex;
  }

  public void setWordIndex(int wordIndex) {
    this.wordIndex = wordIndex;
  }

  @Override
  public String toString() {
    return "OcrInfo{"
        + "horizontalOffset=" + horizontalOffset
        + ", verticalOffset=" + verticalOffset
        + ", width=" + width
        + ", height=" + height
        + ", pageIndex=" + pageIndex
        + ", lineIndex=" + lineIndex
        + ", wordIndex=" + wordIndex
        + ", term='" + term + '\''
        + '}';
  }

  @Override
  public int compareTo(OcrInfo other) {
    return Comparator
        .comparing(OcrInfo::getPageIndex)
        .thenComparing(OcrInfo::getLineIndex)
        .thenComparing(OcrInfo::getWordIndex)
        .thenComparing(OcrInfo::getHorizontalOffset)
        .thenComparing(OcrInfo::getVerticalOffset)
        .compare(this, other);
  }

  public boolean getHasAbsoluteCoordinates() {
    return hasAbsoluteCoordinates;
  }

  public void setHasAbsoluteCoordinates(boolean hasAbsoluteCoordinates) {
    this.hasAbsoluteCoordinates = hasAbsoluteCoordinates;
  }
}
