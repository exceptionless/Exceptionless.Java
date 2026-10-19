package com.exceptionless.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

  @ParameterizedTest
  @CsvSource(
      value = {
        "abcd1234, true",
        "ABC-123-xyz, true",
        "short, false",
        "has space1, false",
        "under_score, false",
        "null, false",
      },
      nullValues = "null")
  void validatesReferenceIdsLikeTheServer(String id, boolean valid) {
    assertThat(Ids.isValidReferenceId(id)).isEqualTo(valid);
  }

  @ParameterizedTest
  @CsvSource({"8, true", "100, true", "101, false"})
  void limitsReferenceIdLength(int length, boolean valid) {
    assertThat(Ids.isValidReferenceId("a".repeat(length))).isEqualTo(valid);
  }

  @Test
  void guidsHaveNoDashes() {
    assertThat(Ids.newGuid()).matches("[0-9a-f]{32}");
  }
}
