package com.exceptionless.internal;

import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The client's name and version, as reported to the server. */
public final class ClientInfo {
  public static final String NAME = "exceptionless-java";

  /** The full version, e.g. {@code 0.9.0-SNAPSHOT}. */
  public static final String VERSION = loadVersion();

  /**
   * The {@code User-Agent} header. The server only records a client version that parses as {@code
   * major.minor[.build[.revision]]}, so pre-release suffixes are dropped.
   */
  public static final String USER_AGENT = NAME + "/" + numericVersion(VERSION);

  private ClientInfo() {}

  private static String loadVersion() {
    try (InputStream in =
        ClientInfo.class.getResourceAsStream("/com/exceptionless/client.properties")) {
      if (in != null) {
        Properties properties = new Properties();
        properties.load(in);
        String version = properties.getProperty("version");
        String stripped = Strings.stripToNull(version);
        if (stripped != null && !stripped.startsWith("${")) {
          return stripped;
        }
      }
    } catch (Exception e) {
      // Fall through to the default.
    }
    return "0.9.0";
  }

  static String numericVersion(String version) {
    Matcher matcher = Pattern.compile("^(\\d+)(\\.\\d+)?(\\.\\d+)?(\\.\\d+)?").matcher(version);
    if (!matcher.find()) {
      return "0.9.0";
    }
    String result = matcher.group();
    return result.contains(".") ? result : result + ".0";
  }
}
