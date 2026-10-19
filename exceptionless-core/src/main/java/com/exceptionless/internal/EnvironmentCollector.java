package com.exceptionless.internal;

import com.exceptionless.models.EnvironmentInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Collects information about the machine and JVM. Static values are computed once. */
public final class EnvironmentCollector {
  private static final Log LOG = Log.get(EnvironmentCollector.class);

  private static volatile @Nullable String machineName;
  private static volatile @Nullable String ipAddresses;
  private static final @Nullable String PROCESS_NAME = processName();
  private static final String RUNTIME_VERSION = runtimeVersion();

  private EnvironmentCollector() {}

  /** Collects the current environment. Never throws. */
  public static EnvironmentInfo collect(
      boolean includeMachineName, boolean includeIpAddress, @Nullable String installId) {
    EnvironmentInfo info = new EnvironmentInfo();
    try {
      Runtime runtime = Runtime.getRuntime();
      info.setProcessorCount(runtime.availableProcessors());
      info.setProcessMemorySize(runtime.totalMemory());
      info.setProcessName(PROCESS_NAME);
      info.setCommandLine(PROCESS_NAME);
      info.setProcessId(Long.toString(ProcessHandle.current().pid()));
      Thread thread = Thread.currentThread();
      info.setThreadName(thread.getName());
      info.setThreadId(Long.toString(threadId(thread)));
      info.setArchitecture(System.getProperty("os.arch"));
      info.setOsName(System.getProperty("os.name"));
      info.setOsVersion(System.getProperty("os.version"));
      info.setRuntimeVersion(RUNTIME_VERSION);
      info.setInstallId(installId);
      putIfNotNull(info, "java.vendor", System.getProperty("java.vendor"));
      putIfNotNull(info, "java.vm.name", System.getProperty("java.vm.name"));
      putIfNotNull(info, "file.encoding", System.getProperty("file.encoding"));
      addMemory(info);
      if (includeMachineName) {
        info.setMachineName(machineName());
      }
      if (includeIpAddress) {
        info.setIpAddress(ipAddresses());
      }
    } catch (RuntimeException | LinkageError e) {
      LOG.debug(() -> "Unable to collect environment info: " + e);
    }
    return info;
  }

  @SuppressWarnings("deprecation") // Thread.threadId() is only available on Java 19+.
  private static long threadId(Thread thread) {
    return thread.getId();
  }

  private static void putIfNotNull(EnvironmentInfo info, String key, @Nullable String value) {
    if (value != null) {
      info.getData().put(key, value);
    }
  }

  private static void addMemory(EnvironmentInfo info) {
    try {
      MemoryProbe.addMemory(info);
    } catch (RuntimeException | LinkageError e) {
      // java.management or jdk.management isn't available (for example in a jlinked runtime).
    }
  }

  /** Isolated so that a missing java.management module only fails this class. */
  private static final class MemoryProbe {
    static void addMemory(EnvironmentInfo info) {
      OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
      if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
        info.setTotalPhysicalMemory(sun.getTotalMemorySize());
        info.setAvailablePhysicalMemory(sun.getFreeMemorySize());
      }
    }
  }

  static @Nullable String machineName() {
    String name = machineName;
    if (name == null) {
      name = Strings.trimToNull(System.getenv("HOSTNAME"));
      if (name == null) {
        name = Strings.trimToNull(System.getenv("COMPUTERNAME"));
      }
      if (name == null) {
        try {
          name = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
          name = "";
        }
      }
      machineName = name;
    }
    return name.isEmpty() ? null : name;
  }

  static @Nullable String ipAddresses() {
    String addresses = ipAddresses;
    if (addresses == null) {
      List<String> list = new ArrayList<>();
      try {
        for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
          if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) {
            continue;
          }
          for (InetAddress address : Collections.list(nic.getInetAddresses())) {
            if (address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isAnyLocalAddress()) {
              continue;
            }
            String host = address.getHostAddress();
            if (address instanceof Inet6Address) {
              int scope = host.indexOf('%');
              if (scope > 0) {
                host = host.substring(0, scope);
              }
            }
            if (!list.contains(host)) {
              list.add(host);
            }
          }
        }
      } catch (Exception e) {
        LOG.debug(() -> "Unable to read network interfaces: " + e);
      }
      addresses = String.join(",", list);
      ipAddresses = addresses;
    }
    return addresses.isEmpty() ? null : addresses;
  }

  private static @Nullable String processName() {
    String command = System.getProperty("sun.java.command");
    if (command != null && !command.isBlank()) {
      String first = command.trim().split("\\s+", 2)[0];
      int slash = Math.max(first.lastIndexOf('/'), first.lastIndexOf('\\'));
      return slash >= 0 ? first.substring(slash + 1) : first;
    }
    try {
      return ProcessHandle.current().info().command().orElse("java");
    } catch (RuntimeException e) {
      return "java";
    }
  }

  private static String runtimeVersion() {
    String version = System.getProperty("java.runtime.version", System.getProperty("java.version"));
    String vendor = System.getProperty("java.vendor");
    return "Java " + version + (vendor != null ? " (" + vendor + ")" : "");
  }
}
