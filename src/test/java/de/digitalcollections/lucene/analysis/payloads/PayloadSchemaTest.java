package de.digitalcollections.lucene.analysis.payloads;

import de.digitalcollections.lucene.analysis.payloads.fields.*;
import org.apache.lucene.analysis.util.ClasspathResourceLoader;
import org.apache.lucene.analysis.util.ResourceLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class PayloadSchemaTest {

  @Test
  public void testParseSchema() throws IOException {
    ResourceLoader loader = new ClasspathResourceLoader(this.getClass().getClassLoader());
    PayloadSchema schema = PayloadSchema.load(loader, "schemata/alltypes.yml");
    assertThat(schema.getFieldNames()).containsExactly(
        "intfield", "floatfield", "p", "boolfield", "setfield");

    assertThat(schema.getField("intfield")).isInstanceOf(IntegerFieldDefinition.class);
    IntegerFieldDefinition intDef = (IntegerFieldDefinition) schema.getField("intfield");
    assertThat(intDef.isSigned()).isFalse();
    assertThat(intDef.getNumBits()).isEqualTo(4);
    assertThat(intDef.getKey()).isEqualTo("ui");

    assertThat(schema.getField("floatfield")).isInstanceOf(FloatFieldDefinition.class);
    FloatFieldDefinition floatDef = (FloatFieldDefinition) schema.getField("floatfield");
    assertThat(floatDef.getNumBits()).isEqualTo(16);
    assertThat(floatDef.getKey()).isEqualTo("f");

    assertThat(schema.getField("p")).isInstanceOf(PercentageFieldDefinition.class);
    PercentageFieldDefinition pDef = (PercentageFieldDefinition) schema.getField("p");
    assertThat(pDef.getKey()).isEqualTo("p");
    assertThat(pDef.getNumBits()).isEqualTo(10);

    assertThat(schema.getField("boolfield")).isInstanceOf(BoolFieldDefinition.class);

    assertThat(schema.getField("setfield")).isInstanceOf(BitSetFieldDefinition.class);
    BitSetFieldDefinition setDef = (BitSetFieldDefinition) schema.getField("setfield");
    assertThat(setDef.getValues()).containsExactly("one", "two", "three");
    assertThat(setDef.getNumBits()).isEqualTo(3);
  }
}
