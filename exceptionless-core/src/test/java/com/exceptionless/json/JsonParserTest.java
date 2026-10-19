package com.exceptionless.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonParserTest {
  @Test
  void parsesSettingsResponse() {
    Map<String, Object> json =
        JsonParser.parseObject(
            "{\"version\":7,\"settings\":{\"@@log:*\":\"Info\",\"@@DataExclusions\":\"*password*\"}}");
    assertThat(json.get("version")).isEqualTo(7L);
    assertThat(json.get("settings"))
        .isEqualTo(Map.of("@@log:*", "Info", "@@DataExclusions", "*password*"));
  }

  @Test
  void parsesAllValueTypes() {
    Object value = JsonParser.parse(" [1, -2.5, 1e3, true, false, null, \"s\", {}, []] ");
    assertThat(value)
        .isEqualTo(
            java.util.Arrays.asList(
                1L,
                new BigDecimal("-2.5"),
                new BigDecimal("1e3"),
                true,
                false,
                null,
                "s",
                Map.of(),
                List.of()));
  }

  @Test
  void parsesEscapes() {
    assertThat(JsonParser.parse("\"a\\\"b\\\\c\\/d\\n\\u00fc\"")).isEqualTo("a\"b\\c/d\nü");
  }

  @Test
  void parsesHugeIntegersAsBigDecimal() {
    assertThat(JsonParser.parse("123456789012345678901234567890"))
        .isEqualTo(new BigDecimal("123456789012345678901234567890"));
  }

  @Test
  void rejectsInvalidJson() {
    for (String invalid :
        List.of(
            "",
            "{",
            "[1,]",
            "{\"a\" 1}",
            "tru",
            "\"unterminated",
            "{} extra",
            "{'a':1}",
            "\"\\x\"",
            "[\"\u0001\"]")) {
      assertThatThrownBy(() -> JsonParser.parse(invalid))
          .as(invalid)
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void rejectsDeepNesting() {
    String deep = "[".repeat(1000) + "]".repeat(1000);
    assertThatThrownBy(() -> JsonParser.parse(deep)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parseObjectRequiresObject() {
    assertThatThrownBy(() -> JsonParser.parseObject("[]"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
