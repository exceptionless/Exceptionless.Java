package com.exceptionless.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.exceptionless.models.EnvironmentInfo;
import com.exceptionless.models.ModuleInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

class CollectorsTest {
  @Test
  void collectsEnvironment() {
    EnvironmentInfo info = EnvironmentCollector.collect(true, true, "install");
    assertThat(info.getProcessorCount()).isPositive();
    assertThat(info.getProcessId()).matches("\\d+");
    assertThat(info.getThreadName()).isEqualTo(Thread.currentThread().getName());
    assertThat(info.getArchitecture()).isNotBlank();
    assertThat(info.getOsName()).isNotBlank();
    assertThat(info.getOsVersion()).isNotBlank();
    assertThat(info.getRuntimeVersion())
        .startsWith("Java " + System.getProperty("java.runtime.version", ""));
    assertThat(info.getInstallId()).isEqualTo("install");
    assertThat(info.getTotalPhysicalMemory()).isPositive();
    assertThat(info.getProcessName()).isNotBlank();
    assertThat(info.getCommandLine()).isEqualTo(info.getProcessName());
  }

  @Test
  void omitsPrivateFieldsWhenAsked() {
    EnvironmentInfo info = EnvironmentCollector.collect(false, false, null);
    assertThat(info.getMachineName()).isNull();
    assertThat(info.getIpAddress()).isNull();
  }

  @Test
  void collectsModulesFromManifests() {
    List<ModuleInfo> modules = ModuleCollector.getModules();
    assertThat(modules).isNotEmpty();
    assertThat(modules)
        .anySatisfy(
            m -> {
              assertThat(m.getName()).containsIgnoringCase("assertj");
              assertThat(m.getVersion()).isNotBlank();
            });
    assertThat(modules).extracting(ModuleInfo::getModuleId).doesNotHaveDuplicates();
    assertThat(ModuleCollector.getModules()).isSameAs(modules);
  }
}
