package com.exceptionless.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WildcardTest {
  @ParameterizedTest
  @CsvSource({
    "password, password, true",
    "Password, password, true",
    "password, PASSWORD, true",
    "userPassword, *password, true",
    "passwordHash, password*, true",
    "myPasswordHash, *password*, true",
    "anything, *, true",
    "pass, password, false",
    "userPassword, password*, false",
    "passwordHash, *password, false",
    "a.b(c)[d]+, a.b(c)[d]+, true",
    "x.y, x?y, false",
    "com.example.Foo, com.example.*, true",
    "com.other.Foo, com.example.*, false",
  })
  void matchesLikeTheOtherClients(String value, String pattern, boolean expected) {
    assertThat(Wildcard.matches(value, pattern)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource(
      value = {"null, *", "value, null", "value, ''", "value, '  '"},
      nullValues = "null")
  void nullOrBlankNeverMatches(String value, String pattern) {
    assertThat(Wildcard.matches(value, pattern)).isFalse();
  }

  @org.junit.jupiter.api.Test
  void matchesAny() {
    assertThat(Wildcard.matchesAny("Googlebot/2.1", List.of("*crawler*", "*bot*"))).isTrue();
    assertThat(Wildcard.matchesAny("Mozilla/5.0", List.of("*crawler*", "*bot*"))).isFalse();
    assertThat(Wildcard.matchesAny("x", List.of())).isFalse();
  }
}
