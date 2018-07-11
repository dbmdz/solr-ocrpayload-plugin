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
        "intfield", "uintfield", "halffloat", "singlefloat", "doublefloat", "p", "boolfield", "setfield");

    assertThat(schema.getField("intfield")).isInstanceOf(IntegerFieldDefinition.class);
    IntegerFieldDefinition intDef = (IntegerFieldDefinition) schema.getField("intfield");
    assertThat(intDef.isSigned()).isTrue();
    assertThat(intDef.getNumBits()).isEqualTo(8);
    assertThat(intDef.getKey()).isEqualTo("i");

    assertThat(schema.getField("uintfield")).isInstanceOf(IntegerFieldDefinition.class);
    IntegerFieldDefinition uintDef = (IntegerFieldDefinition) schema.getField("uintfield");
    assertThat(uintDef.isSigned()).isFalse();
    assertThat(uintDef.getNumBits()).isEqualTo(4);
    assertThat(uintDef.getKey()).isEqualTo("ui");

    assertThat(schema.getField("halffloat")).isInstanceOf(FloatFieldDefinition.class);
    FloatFieldDefinition halfDef = (FloatFieldDefinition) schema.getField("halffloat");
    assertThat(halfDef.getNumBits()).isEqualTo(16);
    assertThat(halfDef.getKey()).isEqualTo("hf");

    assertThat(schema.getField("singlefloat")).isInstanceOf(FloatFieldDefinition.class);
    FloatFieldDefinition singleDef = (FloatFieldDefinition) schema.getField("singlefloat");
    assertThat(singleDef.getNumBits()).isEqualTo(32);
    assertThat(singleDef.getKey()).isEqualTo("sf");

    assertThat(schema.getField("doublefloat")).isInstanceOf(FloatFieldDefinition.class);
    FloatFieldDefinition doubleDef = (FloatFieldDefinition) schema.getField("doublefloat");
    assertThat(doubleDef.getNumBits()).isEqualTo(64);
    assertThat(doubleDef.getKey()).isEqualTo("df");

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
