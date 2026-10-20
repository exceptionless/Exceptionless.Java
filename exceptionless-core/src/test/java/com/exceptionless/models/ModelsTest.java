package com.exceptionless.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelsTest {
  @Test
  void tagsAreStrippedAndOnlyChangedThroughTheEvent() {
    Event event = new Event();
    event.addTags(" a ", "", "  ", "b");
    assertThat(event.getTags()).containsExactly("a", "b");
    assertThat(event.hasTag(" a")).isTrue();
    assertThat(event.hasTag("c")).isFalse();
    assertThatThrownBy(() -> event.getTags().add("c"))
        .isInstanceOf(UnsupportedOperationException.class);
    event.removeTags("a ", "missing");
    assertThat(event.getTags()).containsExactly("b");
  }

  @Test
  void modelSettersFollowJavaBeanConventions() {
    for (Class<?> type :
        List.of(
            Event.class,
            ErrorInfo.class,
            StackFrame.class,
            RequestInfo.class,
            EnvironmentInfo.class)) {
      assertThat(Arrays.stream(type.getMethods()))
          .as(type.getSimpleName())
          .filteredOn(m -> m.getName().startsWith("set") || m.getName().startsWith("add"))
          .isNotEmpty()
          .allSatisfy(m -> assertThat(m.getReturnType()).as(m.toString()).isEqualTo(void.class));
    }
  }

  @Test
  void toStringHandlesMissingValues() {
    StackFrame frame = new StackFrame();
    frame.setDeclaringType("Service");
    frame.setName("run");
    frame.setFileName("Service.java");
    assertThat(frame).hasToString("Service.run(Service.java)");
    frame.setLineNumber(12);
    assertThat(frame).hasToString("Service.run(Service.java:12)");

    ErrorInfo error = new ErrorInfo();
    error.setMessage("boom");
    assertThat(error).hasToString("<unknown>: boom");
    error.setType("java.lang.IllegalStateException");
    assertThat(error).hasToString("java.lang.IllegalStateException: boom");
  }

  @Test
  void recordsCopyTheirMaps() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("b", 1);
    data.put("a", 2);
    UserInfo user = new UserInfo("id", "name", data);
    data.put("c", 3);
    assertThat(user.data()).containsExactly(Map.entry("b", 1), Map.entry("a", 2));
    assertThatThrownBy(() -> user.data().put("d", 4))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(new UserInfo("id").data()).isNull();
    assertThat(new UserDescription("a@b.c", "d", new HashMap<>(data)).data()).hasSize(3);

    Map<String, String> signature = new HashMap<>(Map.of("k", "v"));
    ManualStackingInfo stacking = new ManualStackingInfo("t", signature);
    signature.clear();
    assertThat(stacking.signatureData()).containsExactly(Map.entry("k", "v"));
  }
}
