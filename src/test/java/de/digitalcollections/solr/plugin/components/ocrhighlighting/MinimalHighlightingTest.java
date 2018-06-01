package de.digitalcollections.solr.plugin.components.ocrhighlighting;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.revinate.assertj.json.JsonPathAssert;
import java.math.BigDecimal;
import org.apache.solr.SolrTestCaseJ4;
import org.junit.BeforeClass;
import org.junit.Test;

/** Test that configuring the plugin without page/line/word indices works as expected. **/
public class MinimalHighlightingTest extends SolrTestCaseJ4 {
  @BeforeClass
  public static void beforeClass() throws Exception {
    initCore("conf/solrconfig.xml", "conf/schema.xml", "src/test/resources/solr", "minimal");
    assertU(adoc("ocr_text", "two|x123y432w543h654 one|x654y543w432h321", "id", "101"));
    assertU(adoc("ocr_text", "three|x127y482w549h654 two|x654y543w431h341 five|x0y0w0h0 "
        + "four|x111y111w111h111", "id", "102"));

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
