package com.exceptionless.exceptionlessclient.services;

import com.exceptionless.exceptionlessclient.models.services.EnvironmentInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultEnvironmentInfoCollectorTest {

  @Test
  public void itShouldNotIncludeMachineNameAndIpAddressUntilExplicitlyTold() {
    EnvironmentInfo info =
        DefaultEnvironmentInfoCollector.builder()
            .build()
            .getEnvironmentInfo(EnvironmentInfoGetArgs.builder().build());

    assertThat(info.getMachineName()).isNull();
    assertThat(info.getIpAddress()).isNull();

    assertThat(info.getProcessorCount()).isNotNull();
  }

  @Test
  public void itCanIncludeMachineNameAndIpAddress() {
    EnvironmentInfo info =
        DefaultEnvironmentInfoCollector.builder()
            .build()
            .getEnvironmentInfo(
                EnvironmentInfoGetArgs.builder()
                    .includeIpAddress(true)
                    .includeMachineName(true)
                    .build());

    assertThat(info.getMachineName()).isNotNull();
    assertThat(info.getIpAddress()).isNotNull();

    assertThat(info.getProcessorCount()).isNotNull();
  }
}
