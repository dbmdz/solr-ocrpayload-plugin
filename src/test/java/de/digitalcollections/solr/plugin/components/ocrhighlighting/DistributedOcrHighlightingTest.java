package de.digitalcollections.solr.plugin.components.ocrhighlighting;

import org.apache.solr.BaseDistributedSearchTestCase;
import org.apache.solr.handler.component.SearchComponent;
import org.junit.BeforeClass;
import org.junit.Test;

public class DistributedOcrHighlightingTest extends BaseDistributedSearchTestCase {

  @BeforeClass
  public static void beforeClass() throws Exception {
    System.setProperty("managed.schema.mutable", "true");

    initCore("conf/solrconfig.xml", "conf/schema.xml", "src/test/resources/solr", "alldata");

    // The highlighting component should be active
    SearchComponent highlighter = h.getCore().getSearchComponent("ocr_highlight");
    assertTrue("wrong highlighter: " + highlighter.getClass(),
        highlighter instanceof OcrHighlighting);

    assertU(adoc("ocr_text", "contains|p20l3n5x111y222w333h444 position|p20l4n6x555y666w777h888", "id", "105"));

    assertU(BaseDistributedSearchTestCase.commit());
  }

  @Test
  @ShardsRepeat(max=5)
  public void testWithPageNumberAndPosition() {
    assertQ(
        "terms with both page number and word position",
        req("q", "contains position", "sort", "id asc", "ocr_hl", "true","ocr_hl.fields", "ocr_text", "df", "ocr_text"),
        "count(//lst[@name='ocr_highlighting']/*)=1",
        "count(//lst[@name='ocr_highlighting']/lst[@name='105']/arr[@name='ocr_text']/lst)=2",
        "(//lst[@name='ocr_highlighting']/lst[@name='105']/arr[@name='ocr_text']/lst)[1]/int[@name='page']='20'",
        "(//lst[@name='ocr_highlighting']/lst[@name='105']/arr[@name='ocr_text']/lst)[1]/int[@name='line']='3'",
        "(//lst[@name='ocr_highlighting']/lst[@name='105']/arr[@name='ocr_text']/lst)[1]/int[@name='word']='5'",
        "(//lst[@name='ocr_highlighting']/lst[@name='105']/arr[@name='ocr_text']/lst)[2]/int[@name='page']='20'",
        "(//lst[@name='ocr_highlighting']/lst[@name='105']/arr[@name='ocr_text']/lst)[2]/int[@name='line']='4'",
        "(//lst[@name='ocr_highlighting']/lst[@name='105']/arr[@name='ocr_text']/lst)[2]/int[@name='word']='6'");
  }
}
