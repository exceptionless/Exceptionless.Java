package com.exceptionless.submission;

import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.internal.ClientInfo;
import com.exceptionless.internal.Log;
import com.exceptionless.json.JsonParser;
import com.exceptionless.json.JsonWriter;
import com.exceptionless.models.UserDescription;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.jspecify.annotations.Nullable;

/** Sends requests with the JDK {@link HttpClient}. Bodies over 1 KB are gzip compressed. */
public final class DefaultSubmissionClient implements SubmissionClient {
  static final int COMPRESSION_THRESHOLD = 1024;
  private static final Log LOG = Log.get(DefaultSubmissionClient.class);
  private static final String CONFIG_VERSION_HEADER = "X-Exceptionless-ConfigVersion";

  private final HttpClient httpClient;

  public DefaultSubmissionClient(ExceptionlessConfiguration config) {
    HttpClient custom = config.getHttpClient();
    this.httpClient = custom != null ? custom : createHttpClient(config);
  }

  private static HttpClient createHttpClient(ExceptionlessConfiguration config) {
    HttpClient.Builder builder =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(config.getConnectTimeout());
    if (config.getProxySelector() != null) {
      builder.proxy(config.getProxySelector());
    }
    if (config.getSslContext() != null) {
      builder.sslContext(config.getSslContext());
    }
    return builder.build();
  }

  @Override
  public SubmissionResponse postEvents(String eventsJson, ExceptionlessConfiguration config) {
    return post(config.getServerUrl() + "/api/v2/events", eventsJson, config);
  }

  @Override
  public SubmissionResponse postUserDescription(
      String referenceId, UserDescription description, ExceptionlessConfiguration config) {
    String url =
        config.getServerUrl()
            + "/api/v2/events/by-ref/"
            + URLEncoder.encode(referenceId, StandardCharsets.UTF_8)
            + "/user-description";
    return post(url, JsonWriter.toJson(description), config);
  }

  @Override
  public SettingsResponse getSettings(int version, ExceptionlessConfiguration config) {
    String url = config.getConfigServerUrl() + "/api/v2/projects/config?v=" + version;
    try {
      HttpResponse<String> response =
          httpClient.send(request(url, config).GET().build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 304) {
        return SettingsResponse.notModifiedResponse();
      }
      if (response.statusCode() != 200) {
        return SettingsResponse.failed("Unable to get settings: HTTP " + response.statusCode());
      }
      return parseSettings(response.body());
    } catch (IOException e) {
      return SettingsResponse.failed("Unable to get settings: " + e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return SettingsResponse.failed("Interrupted while getting settings");
    } catch (RuntimeException e) {
      return SettingsResponse.failed("Unable to get settings: " + e);
    }
  }

  static SettingsResponse parseSettings(String body) {
    Map<String, Object> json;
    try {
      json = JsonParser.parseObject(body);
    } catch (IllegalArgumentException e) {
      return SettingsResponse.failed("Invalid settings response: " + e.getMessage());
    }
    Object rawVersion = json.get("version");
    if (!(rawVersion instanceof Number version)) {
      return SettingsResponse.failed("Invalid settings response: missing version");
    }
    Map<String, String> settings = new LinkedHashMap<>();
    if (json.get("settings") instanceof Map<?, ?> values) {
      for (Map.Entry<?, ?> entry : values.entrySet()) {
        if (entry.getValue() != null) {
          settings.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
      }
    }
    return SettingsResponse.of(settings, version.intValue());
  }

  @Override
  public SubmissionResponse sendHeartbeat(
      String sessionIdOrUserId, boolean closeSession, ExceptionlessConfiguration config) {
    String url =
        config.getHeartbeatServerUrl()
            + "/api/v2/events/session/heartbeat?id="
            + URLEncoder.encode(sessionIdOrUserId, StandardCharsets.UTF_8)
            + "&close="
            + closeSession;
    return send(request(url, config).GET().build());
  }

  private SubmissionResponse post(String url, String json, ExceptionlessConfiguration config) {
    byte[] body = json.getBytes(StandardCharsets.UTF_8);
    HttpRequest.Builder builder =
        request(url, config).header("Content-Type", "application/json; charset=utf-8");
    if (body.length > COMPRESSION_THRESHOLD) {
      body = gzip(body);
      builder.header("Content-Encoding", "gzip");
    }
    return send(builder.POST(HttpRequest.BodyPublishers.ofByteArray(body)).build());
  }

  private SubmissionResponse send(HttpRequest request) {
    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      String body = response.body();
      int status = response.statusCode();
      if (status >= 300 && status < 400) {
        // Redirects would turn a POST into a GET, so they're reported instead of followed.
        String location = response.headers().firstValue("Location").orElse("(unknown)");
        LOG.warn(
            "The Exceptionless server redirected "
                + request.uri()
                + " to "
                + location
                + "; update the server URL");
        body = "Redirected to " + location;
      }
      return new SubmissionResponse(
          status,
          body == null || body.isEmpty() ? null : body,
          parseVersion(response.headers().firstValue(CONFIG_VERSION_HEADER).orElse(null)));
    } catch (IOException e) {
      LOG.debug(() -> "Request to " + request.uri() + " failed: " + e);
      return SubmissionResponse.failed(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return SubmissionResponse.failed(e);
    } catch (RuntimeException e) {
      LOG.warn("Request to " + request.uri() + " failed", e);
      return SubmissionResponse.failed(e);
    }
  }

  private static HttpRequest.Builder request(String url, ExceptionlessConfiguration config) {
    return HttpRequest.newBuilder(URI.create(url))
        .timeout(config.getRequestTimeout())
        .header("Authorization", "Bearer " + config.getApiKey())
        .header("User-Agent", ClientInfo.USER_AGENT)
        .header("Accept", "application/json");
  }

  static @Nullable Integer parseVersion(@Nullable String value) {
    if (value == null) {
      return null;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  static byte[] gzip(byte[] data) {
    ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, data.length / 4));
    try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
      gzip.write(data);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toByteArray();
  }
}
