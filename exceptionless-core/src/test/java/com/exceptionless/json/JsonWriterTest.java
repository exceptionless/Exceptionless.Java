package com.exceptionless.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.exceptionless.internal.DataNormalizer;
import com.exceptionless.internal.Exclusions;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JsonWriterTest {
  @Test
  void writesPrimitivesAndContainers() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("s", "text");
    map.put("i", 42);
    map.put("l", 9_000_000_000L);
    map.put("d", 1.5);
    map.put("whole", 2.0);
    map.put("b", true);
    map.put("n", null);
    map.put("list", List.of(1, "two"));
    map.put("arr", new int[] {1, 2});
    map.put("nested", Map.of("k", "v"));
    assertThat(JsonWriter.toJson(map))
        .isEqualTo(
            "{\"s\":\"text\",\"i\":42,\"l\":9000000000,\"d\":1.5,\"whole\":2,\"b\":true,\"n\":null,"
                + "\"list\":[1,\"two\"],\"arr\":[1,2],\"nested\":{\"k\":\"v\"}}");
  }

  @Test
  void escapesStrings() {
    assertThat(JsonWriter.toJson("quote\" back\\ nl\n tab\t ctl\u0001 sep\u2028"))
        .isEqualTo("\"quote\\\" back\\\\ nl\\n tab\\t ctl\\u0001 sep\\u2028\"");
  }

  @Test
  void keepsSurrogatePairsAndReplacesLoneSurrogates() {
    assertThat(JsonWriter.toJson("😀")).isEqualTo("\"😀\"");
    assertThat(JsonWriter.toJson("a\ud800b")).isEqualTo("\"a\ufffdb\"");
  }

  @Test
  void writesNonFiniteNumbersAsNull() {
    assertThat(JsonWriter.toJson(List.of(Double.NaN, Double.POSITIVE_INFINITY, Float.NaN)))
        .isEqualTo("[null,null,null]");
  }

  @Test
  void writesBigDecimalsWithoutExponent() {
    assertThat(JsonWriter.toJson(new BigDecimal("1E+3"))).isEqualTo("1000");
    assertThat(JsonWriter.toJson(new BigDecimal("0.000001"))).isEqualTo("0.000001");
  }

  @Test
  void writesDatesAsIso8601() {
    OffsetDateTime date = OffsetDateTime.of(2026, 1, 2, 3, 4, 5, 6_000_000, ZoneOffset.ofHours(-5));
    assertThat(JsonWriter.toJson(date)).isEqualTo("\"2026-01-02T03:04:05.006-05:00\"");
    assertThat(JsonWriter.toJson(Instant.parse("2026-01-02T03:04:05Z")))
        .isEqualTo("\"2026-01-02T03:04:05Z\"");
  }

  @Test
  void writesOtherTypesWithToString() {
    UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
    assertThat(JsonWriter.toJson(id)).isEqualTo("\"00000000-0000-0000-0000-000000000001\"");
    assertThat(JsonWriter.toJson(Optional.of("x"))).isEqualTo("\"x\"");
    assertThat(JsonWriter.toJson(Optional.empty())).isEqualTo("null");
    assertThat(JsonWriter.toJson(Thread.State.NEW)).isEqualTo("\"NEW\"");
  }

  @Test
  void writesSimpleValuesLikeTheDataNormalizer() {
    Path path = Path.of("app.log"); // a Path is an Iterable of Paths
    OffsetDateTime date = OffsetDateTime.of(2026, 5, 1, 10, 7, 0, 0, ZoneOffset.UTC);
    List<Object> values = List.of(path, date, String.class, Thread.State.NEW);
    assertThat(JsonWriter.toJson(values))
        .isEqualTo(JsonWriter.toJson(DataNormalizer.normalize(values, 5, Exclusions.none())))
        .isEqualTo("[\"app.log\",\"2026-05-01T10:07:00Z\",\"java.lang.String\",\"NEW\"]");
  }

  @Test
  void fieldHelpersSkipNullAndEmptyValues() {
    JsonWriter writer = new JsonWriter();
    writer
        .beginObject()
        .field("a", null)
        .fieldIfNotEmpty("b", "")
        .fieldIfNotEmpty("c", " ")
        .fieldIfNotEmpty("d", Map.of())
        .fieldIfNotEmpty("e", List.of())
        .field("f", 0)
        .endObject();
    assertThat(writer.toString()).isEqualTo("{\"f\":0}");
  }

  @Test
  void limitsNestingDepth() {
    Object nested = "leaf";
    for (int i = 0; i < 100; i++) {
      nested = List.of(nested);
    }
    String json = JsonWriter.toJson(nested);
    assertThat(json).contains("\"...\"");
    assertThat(JsonParser.parse(json)).isNotNull();
  }

  @Test
  void roundTripsThroughParser() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("text", "line1\nline2 \"q\" ü 😀");
    map.put("number", 12L);
    map.put("list", List.of(true, false));
    assertThat(JsonParser.parse(JsonWriter.toJson(map))).isEqualTo(map);
  }
}
