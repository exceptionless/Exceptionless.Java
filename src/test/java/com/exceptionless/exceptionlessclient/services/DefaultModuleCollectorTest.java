package com.exceptionless.exceptionlessclient.services;

import com.exceptionless.exceptionlessclient.models.services.Module;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultModuleCollectorTest {
  @Test
  public void itCanGetModules() {
    List<Module> modules = DefaultModuleCollector.builder().build().getModules();

    assertThat(modules).isNotEmpty();
  }
}
