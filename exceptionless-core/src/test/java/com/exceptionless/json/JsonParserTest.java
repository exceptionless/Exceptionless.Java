package com.exceptionless.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

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
            Arrays.asList(
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

  static List<String> invalidJson() {
    return List.of(
        "",
        " ",
        "{",
        "[1,]",
        "{\"a\":1,}",
        "{\"a\" 1}",
        "tru",
        "nul",
        "\"unterminated",
        "{} extra",
        "{'a':1}",
        "\"\\x\"",
        "[\"\u0001\"]",
        "\"\\u12\"",
        "\"\\u+123\"",
        "\"\\u-123\"",
        // Numbers outside the RFC 8259 grammar.
        "01",
        "-01",
        "00",
        "1.",
        ".5",
        "-.5",
        "-",
        "+1",
        "1e",
        "1e+",
        "1.e5",
        "1.5.5",
        "1-2",
        "0x10",
        "NaN",
        "Infinity",
        // A byte order mark is only allowed at the very start.
        "[1,\uFEFF2]",
        " \uFEFF{}",
        "{}\uFEFF",
        // Only space, tab, CR and LF are white space.
        "\u00A0{}",
        "{}\u000B");
  }

  @ParameterizedTest
  @MethodSource("invalidJson")
  void rejectsInvalidJson(String invalid) {
    assertThatThrownBy(() -> JsonParser.parse(invalid))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "0 | 0",
        "-0 | 0",
        "10 | 10",
        "-12 | -12",
        "0.5 | 0.5",
        "-0.5 | -0.5",
        "1e3 | 1E+3",
        "1E-2 | 0.01",
        "2.5e+2 | 2.5E+2",
      })
  void parsesValidNumbers(String json, String expected) {
    Object parsed = JsonParser.parse(json);
    if (parsed instanceof Long value) {
      assertThat(value).isEqualTo(Long.parseLong(expected));
    } else {
      assertThat(parsed).isInstanceOf(BigDecimal.class);
      assertThat((BigDecimal) parsed).isEqualByComparingTo(expected);
    }
  }

  @Test
  void ignoresALeadingByteOrderMark() {
    assertThat(JsonParser.parse("\uFEFF {\"a\":1}")).isEqualTo(Map.of("a", 1L));
  }

  @Test
  void parsesUnicodeEscapesInEitherCase() {
    assertThat(JsonParser.parse("\"\\u00E9\\u00e9\"")).isEqualTo("\u00e9\u00e9");
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
