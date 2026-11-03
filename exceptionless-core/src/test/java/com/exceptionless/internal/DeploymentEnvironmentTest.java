package com.exceptionless.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class DeploymentEnvironmentTest {
  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "production|production",
        "Staging-EU|Staging-EU",
        "qa.2|qa.2",
        "dev_local|dev_local",
        "a|a",
        "0|0",
        "  Production |Production",
      })
  void validNamesAreTrimmedAndKeepTheirCasing(String value, String expected) {
    assertThat(DeploymentEnvironment.normalize(value)).isEqualTo(expected);
  }

  @Test
  void trimsControlWhiteSpaceAndUnicodeSpaces() {
    assertThat(DeploymentEnvironment.normalize("\tProduction\n")).isEqualTo("Production");
    assertThat(DeploymentEnvironment.normalize("\u00a0Production\u00a0")).isEqualTo("Production");
    assertThat(DeploymentEnvironment.normalize("\u2003Production\u2028")).isEqualTo("Production");
    assertThat(DeploymentEnvironment.normalize("\u0085Production")).isEqualTo("Production");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " ", "\t\n", "\u00a0", "  "})
  void emptyIsUnspecified(String value) {
    assertThat(DeploymentEnvironment.normalize(value)).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Staging West",
        " Prod  West ",
        "prod/eu",
        "prod:eu",
        "-prod",
        ".prod",
        "_prod",
        "produ\u00e7\u00e3o",
        "prod\u200b"
      })
  void namesOutsideTheRuleAreUnspecifiedAndNeverRewritten(String value) {
    assertThat(DeploymentEnvironment.normalize(value)).isNull();
  }

  @Test
  void nonAsciiLettersAreUnspecifiedEvenWithinTheLengthLimit() {
    assertThat(DeploymentEnvironment.normalize("\u0130".repeat(64))).isNull();
  }

  @Test
  void allowsSixtyFourCharactersAndRejectsSixtyFive() {
    String name = "a" + "b".repeat(63);
    assertThat(DeploymentEnvironment.normalize(name)).isEqualTo(name);
    assertThat(DeploymentEnvironment.normalize("a".repeat(65))).isNull();
  }

  @Test
  void measuresTheLengthAfterTrimming() {
    assertThat(DeploymentEnvironment.normalize("  " + "x".repeat(64) + "  "))
        .isEqualTo("x".repeat(64));
    assertThat(DeploymentEnvironment.normalize("  " + "x".repeat(65) + "  ")).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "prod\ninvalid",
        "prod\tinvalid",
        "pro\u0000d",
        "prod\u007f",
        "pro\u0085d",
        "a\u009fb"
      })
  void controlCharactersAreUnspecified(String value) {
    assertThat(DeploymentEnvironment.normalize(value)).isNull();
  }
}
