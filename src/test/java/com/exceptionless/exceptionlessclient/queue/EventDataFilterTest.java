package com.exceptionless.exceptionlessclient.queue;

import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class EventDataFilterTest {
  @Test
  public void itShouldFilterCorrectly() {
    EventDataFilter filter =
        EventDataFilter.builder()
            .exclusions(Set.of("exclude", "exclude-exceeding-depth"))
            .maxDepth(2)
            .build();

    Map<String, Object> filteredObject =
        (Map<String, Object>) filter.filter(new TestEventFilterObject());

    Map<String, Object> expected =
        Map.of(
            "intValue",
            1,
            "stringValue",
            "abc",
            "booleanValue",
            true,
            "depth3Map",
            Map.of("depth3Key", Map.of("exclude-exceeding-depth", "exclude-value")),
            "depth2Map",
            Map.of("depth2Key", "value"),
            "depth2List",
            List.of(123, Map.of()),
            "depth3List",
            List.of(123, Map.of("depth3Key", Map.of("exclude-exceeding-depth", "exclude-value"))));
    assertThat(filteredObject).isEqualTo(expected);
  }
}

@Getter
@Setter
class TestEventFilterObject {
  private int intValue = 1;
  private String stringValue = "abc";
  private boolean booleanValue = true;
  private Object nullValue = null;
  private Map<String, Object> depth3Map =
      Map.of("depth3Key", Map.of("exclude-exceeding-depth", "exclude-value"), "exclude", "value");
  private Map<String, String> depth2Map = Map.of("depth2Key", "value", "exclude", "value");
  private List<Object> depth2List = List.of(123, Map.of("exclude", "value"));
  private List<Object> depth3List =
      List.of(
          123,
          Map.of(
              "exclude", "value", "depth3Key", Map.of("exclude-exceeding-depth", "exclude-value")));
  private Object exclude = "exclude-value";
}
