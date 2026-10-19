package com.exceptionless.internal;

import com.exceptionless.models.ModuleInfo;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Discovers the libraries on the class path from their jar manifests. The result is computed once
 * and cached, because scanning can take a few milliseconds.
 */
public final class ModuleCollector {
  static final int MAX_MODULES = 500;
  private static final Log LOG = Log.get(ModuleCollector.class);
  private static final Pattern JAR_NAME =
      Pattern.compile("(?:^|[/!])([^/!]+?)(?:-(\\d+(?:\\.\\w+)*(?:[-.+][\\w.]+)?))?\\.jar(?:!|$)");

  private static volatile @Nullable List<ModuleInfo> cached;

  private ModuleCollector() {}

  /** The modules on the class path, sorted by name. Never throws. */
  public static List<ModuleInfo> getModules() {
    List<ModuleInfo> modules = cached;
    if (modules == null) {
      synchronized (ModuleCollector.class) {
        modules = cached;
        if (modules == null) {
          modules = List.copyOf(collect());
          cached = modules;
        }
      }
    }
    return modules;
  }

  static List<ModuleInfo> collect() {
    // Libraries can appear more than once (e.g. shaded); one of the copies may be the entry point.
    Map<String, Library> byName = new LinkedHashMap<>();
    try {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      if (loader == null) {
        loader = ModuleCollector.class.getClassLoader();
      }
      if (loader == null) {
        return List.of();
      }
      String entryPoint = entryPoint();
      Enumeration<URL> manifests = loader.getResources("META-INF/MANIFEST.MF");
      while (manifests.hasMoreElements() && byName.size() < MAX_MODULES) {
        URL url = manifests.nextElement();
        Library module = read(url, entryPoint);
        if (module != null) {
          byName.merge(module.name() + "@" + module.version(), module, Library::merge);
        }
      }
    } catch (IOException | RuntimeException | LinkageError e) {
      LOG.debug(() -> "Unable to collect modules: " + e);
    }
    List<Library> sorted = new ArrayList<>(byName.values());
    sorted.sort(Comparator.comparing(Library::name, String.CASE_INSENSITIVE_ORDER));
    List<ModuleInfo> modules = new ArrayList<>(sorted.size());
    for (Library module : sorted) {
      modules.add(new ModuleInfo(modules.size(), module.name(), module.version(), module.entry()));
    }
    return modules;
  }

  /** A library read from a manifest, before it is given a module id. */
  private record Library(String name, @Nullable String version, boolean entry) {
    Library merge(Library other) {
      return other.entry && !entry ? other : this;
    }
  }

  private static @Nullable Library read(URL url, @Nullable String entryPoint) {
    String location = url.toString();
    Manifest manifest;
    try {
      URLConnection connection = url.openConnection();
      connection.setUseCaches(false);
      try (InputStream in = connection.getInputStream()) {
        manifest = new Manifest(in);
      }
    } catch (IOException | RuntimeException e) {
      return null;
    }
    Attributes attributes = manifest.getMainAttributes();
    String fileName = null;
    String fileVersion = null;
    Matcher matcher = JAR_NAME.matcher(location.replace("!/META-INF/MANIFEST.MF", "!"));
    String lastName = null;
    String lastVersion = null;
    while (matcher.find()) {
      lastName = matcher.group(1);
      lastVersion = matcher.group(2);
    }
    if (lastName != null) {
      fileName = lastName;
      fileVersion = lastVersion;
    }

    String name =
        firstNonBlank(
            attributes.getValue("Implementation-Title"),
            symbolicName(attributes.getValue("Bundle-SymbolicName")),
            attributes.getValue("Automatic-Module-Name"),
            fileName);
    if (name == null) {
      return null;
    }
    String version =
        firstNonBlank(
            attributes.getValue("Implementation-Version"),
            attributes.getValue("Bundle-Version"),
            fileVersion);

    String mainClass = attributes.getValue("Main-Class");
    String startClass = attributes.getValue("Start-Class");
    boolean entry =
        entryPoint != null
            && (entryPoint.equals(mainClass)
                || entryPoint.equals(startClass)
                || (entryPoint.endsWith(".jar") && location.contains(fileNameOf(entryPoint))));
    return new Library(name, version, entry);
  }

  private static @Nullable String entryPoint() {
    String command = System.getProperty("sun.java.command");
    if (command == null || command.isBlank()) {
      return null;
    }
    int space = command.indexOf(' ');
    return space > 0 ? command.substring(0, space) : command;
  }

  private static String fileNameOf(String path) {
    int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
    return slash >= 0 ? path.substring(slash + 1) : path;
  }

  private static @Nullable String symbolicName(@Nullable String value) {
    if (value == null) {
      return null;
    }
    int semicolon = value.indexOf(';');
    return semicolon > 0 ? value.substring(0, semicolon).trim() : value.trim();
  }

  private static @Nullable String firstNonBlank(@Nullable String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value.trim();
      }
    }
    return null;
  }
}
