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

    assertU(adoc("ocr_text", "contains|p:20,l:3,n:5,x:11.1,y:22.2,w:33.3,h:44.4, position|p:20,l:4,n:6,x:55.5,y:66.6,w:77.7,h:88.8,", "id", "105"));

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
