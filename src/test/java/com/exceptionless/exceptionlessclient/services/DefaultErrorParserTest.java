package com.exceptionless.exceptionlessclient.services;

import com.exceptionless.exceptionlessclient.models.services.error.Error;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultErrorParserTest {
  @Test
  public void itCanParseAnException() {
    Error error = DefaultErrorParser.builder().build().parse(new RuntimeException("test"));

    assertThat(error.getType()).isEqualTo("java.lang.RuntimeException");
    assertThat(error.getMessage()).isEqualTo("test");
    assertThat(error.getStackTrace()).isNotEmpty();
    // dependent on test file name
    assertThat(error.getStackTrace().get(0).getFilename()).isEqualTo("DefaultErrorParserTest.java");
  }
}
