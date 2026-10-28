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
        "  Production |Production",
        "Prod West|Prod West",
      })
  void trimsSurroundingWhiteSpaceAndKeepsCasing(String value, String expected) {
    assertThat(DeploymentEnvironment.normalize(value)).isEqualTo(expected);
  }

  @Test
  void trimsControlWhiteSpaceAndUnicodeSpaces() {
    // The server trims non-breaking and Unicode spaces too, which String.strip() keeps.
    assertThat(DeploymentEnvironment.normalize("\tProduction\n")).isEqualTo("Production");
    assertThat(DeploymentEnvironment.normalize("\u00a0Production\u00a0")).isEqualTo("Production");
    assertThat(DeploymentEnvironment.normalize("\u2003Production\u2028")).isEqualTo("Production");
    assertThat(DeploymentEnvironment.normalize("\u0085Production")).isEqualTo("Production");
  }

  @Test
  void keepsInnerCasingAndSpacing() {
    assertThat(DeploymentEnvironment.normalize(" Prod  West ")).isEqualTo("Prod  West");
    assertThat(DeploymentEnvironment.normalize(" QA ")).isEqualTo("QA");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " ", "\t\n", " ", "  "})
  void emptyIsUnspecified(String value) {
    assertThat(DeploymentEnvironment.normalize(value)).isNull();
  }

  @Test
  void allowsSixtyFourCharactersAndRejectsSixtyFive() {
    assertThat(DeploymentEnvironment.normalize("x".repeat(64))).isEqualTo("x".repeat(64));
    assertThat(DeploymentEnvironment.normalize("x".repeat(65))).isNull();
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
