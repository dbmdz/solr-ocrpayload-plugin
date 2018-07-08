package de.digitalcollections.lucene.analysis.payloads;

import org.apache.lucene.analysis.util.ClasspathResourceLoader;
import org.apache.lucene.analysis.util.ResourceLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class PayloadSchemaTest {

  @Test
  public void testParseSchema() throws IOException {
    ResourceLoader loader = new ClasspathResourceLoader(this.getClass().getClassLoader());
    PayloadSchema schema = new PayloadSchema(loader, "data/schema_ocr.yml");
    assertThat(schema.getFields().keySet()).containsExactly(
        "intfield", "floatfield", "boolfield", "setfield");

    assertThat(schema.getFields().get("intfield")).isInstanceOf(NumberFieldDefinition.class);
    NumberFieldDefinition intDef = (NumberFieldDefinition) schema.getFields().get("intfield");
    assertThat(intDef.isFloatingPoint()).isFalse();
    assertThat(intDef.isSigned()).isFalse();
    assertThat(intDef.getNumBits()).isEqualTo(4);
    assertThat(intDef.getKey()).isEqualTo("ui");

    assertThat(schema.getFields().get("floatfield")).isInstanceOf(NumberFieldDefinition.class);
    NumberFieldDefinition floatDef = (NumberFieldDefinition) schema.getFields().get("floatfield");
    assertThat(floatDef.isFloatingPoint()).isTrue();
    assertThat(floatDef.isSigned()).isTrue();
    assertThat(floatDef.getNumBits()).isEqualTo(16);
    assertThat(floatDef.getKey()).isEqualTo("f");

    assertThat(schema.getFields().get("boolfield")).isInstanceOf(BoolFieldDefinition.class);

    assertThat(schema.getFields().get("setfield")).isInstanceOf(BitSetFieldDefinition.class);
    BitSetFieldDefinition setDef = (BitSetFieldDefinition) schema.getFields().get("setfield");
    assertThat(setDef.getValues()).containsExactly("one", "two", "three");
    assertThat(setDef.getNumBits()).isEqualTo(3);
  }
}
