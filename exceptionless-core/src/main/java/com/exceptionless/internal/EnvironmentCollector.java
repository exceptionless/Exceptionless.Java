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

/**
 * Collects information about the machine and JVM. Values that don't change while the JVM runs are
 * computed once; the slow ones (host name and network lookups) lazily, through holder classes, so
 * they are only computed when the privacy settings allow sending them.
 */
public final class EnvironmentCollector {
  private static final Log LOG = Log.get(EnvironmentCollector.class);

  private static final @Nullable String PROCESS_NAME = processName();
  private static final String RUNTIME_VERSION = runtimeVersion();

  private static final class MachineName {
    static final @Nullable String VALUE = lookUpMachineName();
  }

  private static final class IpAddresses {
    static final @Nullable String VALUE = lookUpIpAddresses();
  }

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
      // Deliberately not the real command line: program arguments routinely contain secrets.
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
    return MachineName.VALUE;
  }

  static @Nullable String ipAddresses() {
    return IpAddresses.VALUE;
  }

  private static @Nullable String lookUpMachineName() {
    String name = Strings.stripToNull(System.getenv("HOSTNAME"));
    if (name == null) {
      name = Strings.stripToNull(System.getenv("COMPUTERNAME"));
    }
    if (name == null) {
      try {
        name = Strings.stripToNull(InetAddress.getLocalHost().getHostName());
      } catch (Exception e) {
        LOG.debug(() -> "Unable to look up the host name: " + e);
      }
    }
    return name;
  }

  private static @Nullable String lookUpIpAddresses() {
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
    return list.isEmpty() ? null : String.join(",", list);
  }

  /**
   * The file name of the main class or jar (e.g. {@code com.example.App} or {@code app.jar}). This
   * is also reported as the command line: the program arguments are deliberately left out because
   * they can contain secrets (see {@link JavaCommand#mainClassOrJar()}).
   */
  private static @Nullable String processName() {
    String mainClassOrJar = JavaCommand.mainClassOrJar();
    if (mainClassOrJar != null) {
      return JavaCommand.fileName(mainClassOrJar);
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
