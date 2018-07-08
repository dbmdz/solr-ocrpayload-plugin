package de.digitalcollections.solr.plugin.components.struct;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.revinate.assertj.json.JsonPathAssert;
import org.apache.solr.SolrTestCaseJ4;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;

/** Test that configuring the plugin without page/line/word indices works as expected. **/
public class MinimalHighlightingTest extends SolrTestCaseJ4 {
  @BeforeClass
  public static void beforeClass() throws Exception {
    initCore("conf/solrconfig.xml", "conf/schema.xml", "src/test/resources/solr", "minimal");
    assertU(adoc("ocr_text", "two|x:12.3,y:43.2,w:54.3,h:65.4, one|x:65.4,y:54.3,w:43.2,h:32.1,", "id", "101"));
    assertU(adoc("ocr_text", "three|x:12.7,y:48.2,w:54.9,h:65.4, two|x:65.4,y:54.3,w:43.1,h:34.1, five|x:0,y:0,w:0,h:0, "
        + "four|x:11.1,y:11.1,w:11.1,h:11.1,", "id", "102"));

    assertU(commit());
  }

  @Test
  public void testMinimal() throws Exception {
    String json = JQ(req(
        "q", "two", "sort", "id asc", "ocr_hl", "true", "ocr_hl.fields", "ocr_text", "df", "ocr_text"));
    DocumentContext ctx = JsonPath.parse(json);
    JsonPathAssert.assertThat(ctx).jsonPathAsBigDecimal("ocr_highlighting.101.ocr_text[0].x")
        .isBetween(BigDecimal.valueOf(0.1230), BigDecimal.valueOf(0.1239));
    JsonPathAssert.assertThat(ctx).jsonPathAsInteger("ocr_highlighting.101.ocr_text.length()").isEqualTo(1);
    JsonPathAssert.assertThat(ctx).jsonPathAsBigDecimal("ocr_highlighting.102.ocr_text[0].x")
        .isBetween(BigDecimal.valueOf(0.6540), BigDecimal.valueOf(0.6549));
    JsonPathAssert.assertThat(ctx).jsonPathAsInteger("ocr_highlighting.101.ocr_text.length()").isEqualTo(1);
  }
}
