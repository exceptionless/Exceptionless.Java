package com.exceptionless;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** Log levels understood by Exceptionless, in increasing order of severity. */
public enum LogLevel {
  TRACE("trace"),
  DEBUG("debug"),
  INFO("info"),
  WARN("warn"),
  ERROR("error"),
  FATAL("fatal"),
  /** Disables logging. Only meaningful as a minimum level. */
  OFF("off");

  private final String value;

  LogLevel(String value) {
    this.value = value;
  }

  /** The value sent to the server in {@code @level}. */
  public String value() {
    return value;
  }

  /** True when this level is at least as severe as {@code minimum}. */
  public boolean isAtLeast(LogLevel minimum) {
    return this != OFF && compareTo(minimum) >= 0;
  }

  /**
   * Parses a level name or setting value. Accepts the level names plus the aliases used by project
   * settings and the other clients: {@code true/yes/1} (trace), {@code false/no/0} (off), {@code
   * warning}, {@code information}, {@code critical}, {@code severe}, {@code fine...}.
   *
   * @return the level, or {@code null} when the value isn't recognized
   */
  public static @Nullable LogLevel parse(@Nullable String value) {
    if (value == null) {
      return null;
    }
    return switch (value.trim().toLowerCase(Locale.ROOT)) {
      case "trace", "true", "yes", "1", "finest", "finer", "all", "verbose" -> TRACE;
      case "debug", "fine", "config" -> DEBUG;
      case "info", "information" -> INFO;
      case "warn", "warning" -> WARN;
      case "error", "severe" -> ERROR;
      case "fatal", "critical" -> FATAL;
      case "off", "false", "no", "0", "none" -> OFF;
      default -> null;
    };
  }

  @Override
  public String toString() {
    return value;
  }
}
