package com.exceptionless.submission;

import static org.assertj.core.api.Assertions.assertThat;

import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.FakeServer;
import com.exceptionless.TestClients;
import com.exceptionless.models.UserDescription;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DefaultSubmissionClientTest {
  private final FakeServer server;
  private final ExceptionlessConfiguration config;
  private final DefaultSubmissionClient client;

  DefaultSubmissionClientTest() throws Exception {
    server = new FakeServer();
    config = TestClients.config().serverUrl(server.url());
    client = new DefaultSubmissionClient(config);
  }

  @AfterEach
  void close() {
    server.close();
  }

  @Test
  void postsEventsWithBearerTokenAndUserAgent() throws Exception {
    SubmissionResponse response = client.postEvents("[{\"type\":\"log\"}]");
    assertThat(response.statusCode()).isEqualTo(202);
    assertThat(response.isSuccess()).isTrue();
    FakeServer.Request request = server.take(Duration.ofSeconds(5));
    assertThat(request.method()).isEqualTo("POST");
    assertThat(request.path()).isEqualTo("/api/v2/events");
    assertThat(request.query()).isEmpty();
    assertThat(request.header("Authorization")).isEqualTo("Bearer " + TestClients.API_KEY);
    assertThat(request.header("Content-Type")).isEqualTo("application/json; charset=utf-8");
    assertThat(request.header("User-Agent")).startsWith("exceptionless-java/");
    assertThat(request.header("Content-Encoding")).isNull();
    assertThat(request.body()).isEqualTo("[{\"type\":\"log\"}]");
  }

  @Test
  void gzipsLargeBodies() throws Exception {
    String json = "[{\"message\":\"" + "x".repeat(5000) + "\"}]";
    client.postEvents(json);
    FakeServer.Request request = server.take(Duration.ofSeconds(5));
    assertThat(request.header("Content-Encoding")).isEqualTo("gzip");
    assertThat(request.body()).isEqualTo(json);
  }

  @Test
  void readsConfigVersionHeader() {
    server.setConfigVersionHeader(12);
    assertThat(client.postEvents("[]").configVersion()).isEqualTo(12);
  }

  @Test
  void returnsErrorStatusesAndBodies() {
    server.onEvents(r -> new FakeServer.Response(400, "{\"title\":\"bad\"}", Map.of()));
    SubmissionResponse response = client.postEvents("[]");
    assertThat(response.isBadRequest()).isTrue();
    assertThat(response.message()).contains("bad");
  }

  @Test
  void connectionFailuresBecomeFailedResponses() {
    server.close();
    SubmissionResponse response = client.postEvents("[]");
    assertThat(response.statusCode()).isEqualTo(-1);
    assertThat(response.isSuccess()).isFalse();
    assertThat(response.message()).isNotBlank();
  }

  @Test
  void getsSettings() throws Exception {
    server.onConfig(
        r ->
            new FakeServer.Response(
                200, "{\"version\":3,\"settings\":{\"@@log:*\":\"info\",\"n\":5}}", Map.of()));
    SettingsResponse response = client.getSettings(2);
    assertThat(response).isInstanceOf(SettingsResponse.Updated.class);
    SettingsResponse.Updated updated = (SettingsResponse.Updated) response;
    assertThat(updated.version()).isEqualTo(3);
    assertThat(updated.settings()).containsEntry("@@log:*", "info").containsEntry("n", "5");
    FakeServer.Request request = server.take(Duration.ofSeconds(5));
    assertThat(request.path()).isEqualTo("/api/v2/projects/config");
    assertThat(request.query()).isEqualTo("v=2");
    assertThat(request.header("Authorization")).isEqualTo("Bearer " + TestClients.API_KEY);
  }

  @Test
  void settingsNotModified() {
    server.onConfig(r -> FakeServer.Response.of(304));
    assertThat(client.getSettings(3)).isInstanceOf(SettingsResponse.NotModified.class);
  }

  @Test
  void settingsErrors() {
    server.onConfig(r -> new FakeServer.Response(200, "not json", Map.of()));
    assertThat(client.getSettings(0)).isInstanceOf(SettingsResponse.Failed.class);
    server.onConfig(r -> new FakeServer.Response(200, "{\"settings\":{}}", Map.of()));
    assertThat(client.getSettings(0)).isInstanceOf(SettingsResponse.Failed.class);
    server.onConfig(r -> FakeServer.Response.of(401));
    assertThat(client.getSettings(0))
        .isInstanceOfSatisfying(
            SettingsResponse.Failed.class, failed -> assertThat(failed.message()).contains("401"));
  }

  @Test
  void usesConfigAndHeartbeatServerUrls() throws Exception {
    try (FakeServer other = new FakeServer()) {
      ExceptionlessConfiguration split =
          TestClients.config()
              .serverUrl(server.url())
              .configServerUrl(other.url())
              .heartbeatServerUrl(other.url());
      DefaultSubmissionClient splitClient = new DefaultSubmissionClient(split);
      splitClient.getSettings(0);
      splitClient.sendHeartbeat("user 1&x", true);
      assertThat(other.take(Duration.ofSeconds(5)).path()).isEqualTo("/api/v2/projects/config");
      FakeServer.Request heartbeat = other.take(Duration.ofSeconds(5));
      assertThat(heartbeat.path()).isEqualTo("/api/v2/events/session/heartbeat");
      assertThat(heartbeat.query()).isEqualTo("id=user+1%26x&close=true");
      assertThat(server.requests()).isEmpty();
    }
  }

  @Test
  void postsUserDescriptions() throws Exception {
    client.postUserDescription("abc-12345", new UserDescription("a@b.c", "desc"));
    FakeServer.Request request = server.take(Duration.ofSeconds(5));
    assertThat(request.path()).isEqualTo("/api/v2/events/by-ref/abc-12345/user-description");
    assertThat(request.body()).isEqualTo("{\"email_address\":\"a@b.c\",\"description\":\"desc\"}");
  }

  @Test
  void reportsRedirectsInsteadOfFollowingThem() {
    server.onEvents(
        r ->
            new FakeServer.Response(
                301, "", Map.of("Location", "https://secure.example.com/api/v2/events")));
    SubmissionResponse response = client.postEvents("[]");
    assertThat(response.statusCode()).isEqualTo(301);
    assertThat(response.message()).contains("https://secure.example.com");
    assertThat(server.requests()).hasSize(1);
  }

  @Test
  void parsesVersionHeaders() {
    assertThat(DefaultSubmissionClient.parseVersion(" 7 ")).isEqualTo(7);
    assertThat(DefaultSubmissionClient.parseVersion("x")).isNull();
    assertThat(DefaultSubmissionClient.parseVersion(null)).isNull();
  }
}
