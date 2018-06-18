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
    assertU(adoc("ocr_text", "two|p:27,l:13,n:24,x:12.3,y:43.2,w:54.3,h:65.4, one|p:28,l:27,n:64,x:65.4,y:54.3,w:43.2,h:32.1", "id", "101"));
    assertU(adoc("ocr_text", "three|p:28,l:14,n:25,x:12.7,y:48.2,w:54.9,h:65.4, two|p:29,l:27,n:64,x:65.4,y:54.3,w:43.1,h:34.1, five|p:30,l:17,n:80,x:0,y:0,w:0,h:0, "
        + "four|p:31,l:32,n:33,x:11.1,y:11.1,w:11.1,h:11.1", "id", "102"));
    assertU(adoc("ocr_text", ocrText, "id", "103"));

    // Test with a dynamic field
    assertU(adoc("body_ocr", "one|p:42,l:13,n:55,x:11.1,y:22.2,w:33.3,h:44.4, two|p:42,l:13,n:66,x:55.5,y:66.6,w:77.7,h:88.8", "id", "106"));

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
