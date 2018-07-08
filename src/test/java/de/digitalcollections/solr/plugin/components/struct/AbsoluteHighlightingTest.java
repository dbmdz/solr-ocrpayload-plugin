package de.digitalcollections.solr.plugin.components.struct;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.revinate.assertj.json.JsonPathAssert;
import org.apache.solr.SolrTestCaseJ4;
import org.junit.BeforeClass;
import org.junit.Test;

/** Test that configuring the plugin with absolute coordinates works as expected. **/
public class AbsoluteHighlightingTest extends SolrTestCaseJ4 {
  @BeforeClass
  public static void beforeClass() throws Exception {
    initCore("conf/solrconfig.xml", "conf/schema.xml", "src/test/resources/solr", "min_absolute");
    assertU(adoc("ocr_text", "two☛x:12300,y:432,w:543,h:654, one☛x:654,y:543,w:432,h:321,", "id", "101"));
    assertU(adoc("ocr_text", "three☛x:127,y:4820,w:5490,h:654, two☛x:654,y:54337,w:431,h:341 five☛x:0,y:0,w:0,h:0, "
        + "four☛x:111,y:111,w:111,h:111,", "id", "102"));

    assertU(commit());
  }

  @Test
  public void testMinimal() throws Exception {
    String json = JQ(req(
        "q", "two", "sort", "id asc", "ocr_hl", "true", "ocr_hl.fields", "ocr_text", "df", "ocr_text"));
    DocumentContext ctx = JsonPath.parse(json);
    JsonPathAssert.assertThat(ctx).jsonPathAsInteger("ocr_highlighting.101.ocr_text[0].x")
        .isEqualTo(12300);
    JsonPathAssert.assertThat(ctx).jsonPathAsInteger("ocr_highlighting.101.ocr_text.length()").isEqualTo(1);
    JsonPathAssert.assertThat(ctx).jsonPathAsInteger("ocr_highlighting.102.ocr_text[0].y")
        .isEqualTo(54337);
    JsonPathAssert.assertThat(ctx).jsonPathAsInteger("ocr_highlighting.101.ocr_text.length()").isEqualTo(1);
  }
}
