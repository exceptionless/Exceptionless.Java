package com.exceptionless.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ClientInfoTest {
  @ParameterizedTest
  @CsvSource({
    "2.0.0, 2.0.0",
    "2.0.0-SNAPSHOT, 2.0.0",
    "2.1, 2.1",
    "3, 3.0",
    "1.2.3.4.5, 1.2.3.4",
    "2.0.0-rc.1+abc, 2.0.0",
    "garbage, 2.0.0"
  })
  void numericVersionIsParsableByTheServer(String version, String expected) {
    assertThat(ClientInfo.numericVersion(version)).isEqualTo(expected);
  }

  @Test
  void userAgentHasNameAndNumericVersion() {
    assertThat(ClientInfo.USER_AGENT).matches("exceptionless-java/\\d+\\.\\d+(\\.\\d+){0,2}");
    assertThat(ClientInfo.VERSION).doesNotStartWith("${");
  }
}
