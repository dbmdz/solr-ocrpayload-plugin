package de.digitalcollections.lucene.analysis.payloads;

class TestUtils {
  public static char[] toChars(String input) {
    char[] buf = new char[input.length()];
    input.getChars(0, input.length(), buf, 0);
    return buf;
  }
}
