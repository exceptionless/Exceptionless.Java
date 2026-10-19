package com.exceptionless.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IdsTest {
  @Test
  void referenceIdsAreValidAndUnique() {
    Set<String> ids = new HashSet<>();
    for (int i = 0; i < 10_000; i++) {
      String id = Ids.newReferenceId();
      assertThat(Ids.isValidReferenceId(id)).isTrue();
      ids.add(id);
    }
    assertThat(ids).hasSize(10_000);
  }

  @Test
  void validatesReferenceIdsLikeTheServer() {
    assertThat(Ids.isValidReferenceId("abcd1234")).isTrue();
    assertThat(Ids.isValidReferenceId("ABC-123-xyz")).isTrue();
    assertThat(Ids.isValidReferenceId("a".repeat(100))).isTrue();
    assertThat(Ids.isValidReferenceId("short")).isFalse();
    assertThat(Ids.isValidReferenceId("a".repeat(101))).isFalse();
    assertThat(Ids.isValidReferenceId("has space1")).isFalse();
    assertThat(Ids.isValidReferenceId("under_score")).isFalse();
    assertThat(Ids.isValidReferenceId(null)).isFalse();
  }

  @Test
  void guidsHaveNoDashes() {
    assertThat(Ids.newGuid()).matches("[0-9a-f]{32}");
  }
}
