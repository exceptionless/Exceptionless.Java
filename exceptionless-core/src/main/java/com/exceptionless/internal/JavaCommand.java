package com.exceptionless.internal;

import org.jspecify.annotations.Nullable;

/** What the JVM was launched with, from the {@code sun.java.command} system property. */
final class JavaCommand {
  private JavaCommand() {}

  /**
   * The main class or jar the JVM was started with, e.g. {@code com.example.App} or {@code
   * /opt/app/app.jar}.
   *
   * <p>Only the first token of the command is returned. The program arguments that follow it are
   * deliberately dropped: they routinely contain secrets such as passwords, tokens and connection
   * strings, so they must never be reported.
   *
   * @return the main class or jar path, or {@code null} when the JVM doesn't report it
   */
  static @Nullable String mainClassOrJar() {
    String command = Strings.stripToNull(System.getProperty("sun.java.command"));
    if (command == null) {
      return null;
    }
    for (int i = 0; i < command.length(); i++) {
      if (Character.isWhitespace(command.charAt(i))) {
        return command.substring(0, i);
      }
    }
    return command;
  }

  /** {@return the last segment of a file path} */
  static String fileName(String path) {
    int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
    return slash >= 0 ? path.substring(slash + 1) : path;
  }
}
