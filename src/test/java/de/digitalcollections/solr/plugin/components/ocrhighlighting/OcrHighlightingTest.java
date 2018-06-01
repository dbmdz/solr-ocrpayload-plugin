package de.digitalcollections.solr.plugin.components.ocrhighlighting;

import java.nio.file.Files;
import java.nio.file.Paths;
import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.handler.component.SearchComponent;
import org.junit.BeforeClass;
import org.junit.Test;

public class OcrHighlightingTest extends SolrTestCaseJ4 {
  @BeforeClass
  public static void beforeClass() throws Exception {
    initCore("solrconfig.xml", "schema.xml", "src/test/resources/solr", "alldata");

    // The highlighting component should be active
    SearchComponent highlighter = h.getCore().getSearchComponent("ocr_highlight");
    assertTrue("wrong highlighter: " + highlighter.getClass(),
        highlighter instanceof OcrHighlighting);

    String ocrText = String.join(" ", Files
        .readAllLines(Paths.get(OcrHighlighting.class.getResource("/data/ocrtext_full.txt").toURI())));
    assertU(adoc("ocr_text", "two|p27l13n24x123y432w543h654 one|p28l27n64x654y543w432h321", "id", "101"));
    assertU(adoc("ocr_text", "three|p28l14n25x127y482w549h654 two|p29l27n64x654y543w431h341 five|p30l17n80x0y0w0h0 "
        + "four|p31l32n33x111y111w111h111", "id", "102"));
    assertU(adoc("ocr_text", ocrText, "id", "103"));

    // Test with a dynamic field
    assertU(adoc("body_ocr", "one|p42l13n55x111y222w333h444 two|p42l13n66x555y666w777h888", "id", "106"));

    assertU(commit());
  }

  @Test
  public void testSingleQueryTerm() {
    assertQ(
        "single query term",
        req("q", "two", "sort", "id asc", "ocr_hl", "true","ocr_hl.fields", "ocr_text", "df", "ocr_text"),
        "count(//lst[@name='ocr_highlighting']/*)=2",
        "//lst[@name='ocr_highlighting']/lst[@name='101']/arr[@name='ocr_text']/lst[1]/int[@name='page']='27'",
        "count(//lst[@name='ocr_highlighting']/lst[@name='101']/arr[@name='ocr_text']/lst)=number('1')",
        "//lst[@name='ocr_highlighting']/lst[@name='102']/arr[@name='ocr_text']/lst[1]/int[@name='page']='29'",
        "count(//lst[@name='ocr_highlighting']/lst[@name='102']/arr[@name='ocr_text']/lst)=number('1')");
  }

  @Test
  public void testMultipleQueryTerms() {
    assertQ(
        "multiple query terms",
        req("q", "five four", "sort", "id asc", "ocr_hl", "true","ocr_hl.fields", "ocr_text", "df", "ocr_text"),
        "count(//lst[@name='ocr_highlighting']/*)=1",
        "count(//lst[@name='ocr_highlighting']/lst[@name='102']/arr[@name='ocr_text']/lst)=number('2')",
        "//lst[@name='ocr_highlighting']/lst[@name='102']/arr[@name='ocr_text']/lst[1]/int[@name='page']='30'",
        "//lst[@name='ocr_highlighting']/lst[@name='102']/arr[@name='ocr_text']/lst[2]/int[@name='page']='31'");

  }

  @Test
  public void testMultipleFuzzyQueryTerms() {
    assertQ(
        "multiple fuzzy query terms",
        req("q", "fives fours", "sort", "id asc", "ocr_hl", "true","ocr_hl.fields", "ocr_text", "df", "ocr_text"),
        "count(//lst[@name='ocr_highlighting']/*)=1",
        "count(//lst[@name='ocr_highlighting']/lst[@name='102']/arr[@name='ocr_text']/lst)=number('2')",
        "//lst[@name='ocr_highlighting']/lst[@name='102']/arr[@name='ocr_text']/lst[1]/int[@name='page']='30'",
        "//lst[@name='ocr_highlighting']/lst[@name='102']/arr[@name='ocr_text']/lst[1]/str[@name='term']='five'",
        "//lst[@name='ocr_highlighting']/lst[@name='102']/arr[@name='ocr_text']/lst[2]/int[@name='page']='31'",
        "//lst[@name='ocr_highlighting']/lst[@name='102']/arr[@name='ocr_text']/lst[2]/str[@name='term']='four'");
  }

  @Test
  public void testLimitHighlightsPerDoc() {
    assertQ(
        "limit number of highlights per document",
        req("q", "und", "sort", "id asc", "ocr_hl", "true","ocr_hl.fields", "ocr_text", "ocr_hl.maxPerDoc", "5", "df",
            "ocr_text"),
        "count(//lst[@name='ocr_highlighting']/lst[@name='103']/arr[@name='ocr_text']/lst)=number('5')");
  }

  @Test
  public void testLimitHighlightsPerPage() {
    assertQ(
        "limit number of highlights per page",
        req("q", "und", "sort", "id asc", "ocr_hl", "true","ocr_hl.fields", "ocr_text", "ocr_hl.maxPerPage", "5", "df",
            "ocr_text"),
        "count(//lst[@name='ocr_highlighting']/lst[@name='103']/arr[@name='ocr_text']/lst[int[@name='page']='183'])=number('5')");
  }

  @Test
  public void testDynamicField() {
    assertQ(
      "Dynamic field contains term with page number and word position",
      req("q", "one two", "sort", "id asc", "ocr_hl", "true", "ocr_hl.fields", "body_ocr", "df", "body_ocr"),
        "count(//lst[@name='ocr_highlighting']/*)=1",
        "count(//lst[@name='ocr_highlighting']/lst[@name='106']/arr[@name='body_ocr']/lst)=number('2')",
        "(//lst[@name='ocr_highlighting']/lst[@name='106']/arr[@name='body_ocr']/lst)[1]/int[@name='page']='42'",
        "(//lst[@name='ocr_highlighting']/lst[@name='106']/arr[@name='body_ocr']/lst)[1]/int[@name='word']='55'",
        "(//lst[@name='ocr_highlighting']/lst[@name='106']/arr[@name='body_ocr']/lst)[1]/int[@name='line']='13'",
        "(//lst[@name='ocr_highlighting']/lst[@name='106']/arr[@name='body_ocr']/lst)[2]/int[@name='page']='42'",
        "(//lst[@name='ocr_highlighting']/lst[@name='106']/arr[@name='body_ocr']/lst)[2]/int[@name='line']='13'",
        "(//lst[@name='ocr_highlighting']/lst[@name='106']/arr[@name='body_ocr']/lst)[2]/int[@name='word']='66'"
    );
  }
}
