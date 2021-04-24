package com.exceptionless.exceptionlessclient.services;

import com.exceptionless.exceptionlessclient.models.enums.EnvironmentInfoPropertyKey;
import com.exceptionless.exceptionlessclient.models.services.EnvironmentInfo;
import com.sun.management.OperatingSystemMXBean;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class DefaultEnvironmentInfoCollector implements EnvironmentInfoCollectorIF {
  private EnvironmentInfo defaultEnvironmentInfo;

  @Builder
  public DefaultEnvironmentInfoCollector() {
    initDefaultEnvironmentInfo();
  }

  private void initDefaultEnvironmentInfo() {
    OperatingSystemMXBean operatingSystemMXBean =
        (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    this.defaultEnvironmentInfo =
        EnvironmentInfo.builder()
            .processorCount(Runtime.getRuntime().availableProcessors())
            .totalPhysicalMemory(operatingSystemMXBean.getTotalPhysicalMemorySize())
            .osName(operatingSystemMXBean.getName())
            .architecture(operatingSystemMXBean.getArch())
            .osVersion(operatingSystemMXBean.getVersion())
            .runtimeVersion(System.getProperty("java.version"))
            .processName(String.valueOf(ProcessHandle.current().pid()))
            .processName(ManagementFactory.getRuntimeMXBean().getName())
            .commandLine(ProcessHandle.current().info().commandLine().orElse(null))
            .build();
  }

  @Override
  public EnvironmentInfo getEnvironmentInfo(EnvironmentInfoGetArgs args) {
    OperatingSystemMXBean operatingSystemMXBean =
        (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    EnvironmentInfo.EnvironmentInfoBuilder<?, ?> builder =
        EnvironmentInfo.builder()
            .processorCount(defaultEnvironmentInfo.getProcessorCount())
            .totalPhysicalMemory(defaultEnvironmentInfo.getTotalPhysicalMemory())
            .availablePhysicalMemory(operatingSystemMXBean.getFreePhysicalMemorySize())
            .commandLine(defaultEnvironmentInfo.getCommandLine())
            .processName(defaultEnvironmentInfo.getProcessName())
            .processId(defaultEnvironmentInfo.getProcessId())
            .processMemorySize(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed())
            .threadId(String.valueOf(Thread.currentThread().getId()))
            .architecture(defaultEnvironmentInfo.getArchitecture())
            .osName(defaultEnvironmentInfo.getOsName())
            .osVersion(defaultEnvironmentInfo.getOsVersion())
            .runtimeVersion(defaultEnvironmentInfo.getRuntimeVersion())
            .data(getData());

    try {
      InetAddress localhost = InetAddress.getLocalHost();
      if (args.isIncludeMachineName()) {
        builder.machineName(localhost.getHostName());
      }
      if (args.isIncludeIpAddress()) {
        builder.ipAddress(localhost.getHostAddress());
      }
    } catch (UnknownHostException e) {
      log.error("Error while getting machine name", e);
    }

    return builder.build();
  }

  private Map<String, Object> getData() {
    Map<String, Object> data = new HashMap<>();
    OperatingSystemMXBean operatingSystemMXBean =
        (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    data.put(
        EnvironmentInfoPropertyKey.LOAD_AVG.value(),
        String.valueOf(operatingSystemMXBean.getSystemLoadAverage()));
    data.put(EnvironmentInfoPropertyKey.TMP_DIR.value(), System.getProperty("java.io.tmpdir"));
    data.put(
        EnvironmentInfoPropertyKey.UP_TIME.value(),
        String.valueOf(ManagementFactory.getRuntimeMXBean().getUptime()));
    data.put(EnvironmentInfoPropertyKey.ENDIANESS.value(), getEndianess());

    return data;
  }

  private String getEndianess() {
    return ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN) ? "Big-endian" : "Little-endian";
  }
}
