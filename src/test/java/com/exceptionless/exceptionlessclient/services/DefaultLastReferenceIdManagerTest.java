package com.exceptionless.exceptionlessclient.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultLastReferenceIdManagerTest {
  @Test
  public void itCanSetAndClearLastReferenceId() {
    DefaultLastReferenceIdManager manager = DefaultLastReferenceIdManager.builder().build();

    manager.setLast("123");
    assertThat(manager.getLast()).isEqualTo("123");

    manager.clearLast();
    assertThat(manager.getLast()).isNull();
  }
}
