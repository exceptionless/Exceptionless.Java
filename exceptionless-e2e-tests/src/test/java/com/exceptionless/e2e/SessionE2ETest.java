package com.exceptionless.e2e;

import static com.exceptionless.e2e.support.Json.at;
import static com.exceptionless.e2e.support.Json.objectAt;
import static org.assertj.core.api.Assertions.assertThat;

import com.exceptionless.LogLevel;
import com.exceptionless.e2e.support.Await;
import com.exceptionless.e2e.support.ExceptionlessServer;
import com.exceptionless.e2e.support.Json;
import com.exceptionless.e2e.support.TestProject;
import com.exceptionless.e2e.support.TestProject.TestClient;
import com.exceptionless.submission.SubmissionResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Sessions: start events, heartbeats and ends. The server closes sessions in a background job
 * (CloseInactiveSessionsJob, every 30 s, only for sessions older than a minute), so these tests
 * wait up to a few minutes.
 */
@Tag("e2e")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SessionE2ETest {
  private static final Duration SESSION_CLOSE = Duration.ofMinutes(3);
  private final String run = "e2e-" + UUID.randomUUID().toString().substring(0, 8);
  private TestProject project;

  @BeforeAll
  void createProject() {
    project = ExceptionlessServer.get().createProject("Sessions");
  }

  @Test
  void sessionForSignedInUser() throws Exception {
    String identity = "session-user-" + run;
    try (TestClient client =
        project.newClient(
            c ->
                c.setUserIdentity(identity, "Session User")
                    .useSessions()
                    .addDefaultTags(run, "identity"))) {
      client
          .client()
          .createLog("SessionTest", "Did something", LogLevel.INFO)
          .addTags("identity-log")
          .submit();
      client.flush();

      SubmissionResponse heartbeat = client.client().submitSessionHeartbeat(null).get();
      assertThat(heartbeat.statusCode()).as(heartbeat.message()).isEqualTo(200);

      Map<String, Object> start = project.awaitEvent("type:session tag:" + run + " tag:identity");
      assertThat(objectAt(start, "data", "@user"))
          .containsEntry("identity", identity)
          .containsEntry("name", "Session User");
      assertThat(objectAt(start, "data", "@environment")).containsKey("machine_name");
      String sessionId = (String) start.get("reference_id");
      assertThat(sessionId).as("the server assigns a session id to identity sessions").isNotBlank();

      Map<String, Object> log = project.awaitEvent("tag:" + run + " tag:identity-log");
      assertThat(at(log, "data", "@ref:session")).isEqualTo(sessionId);

      SubmissionResponse end = client.client().submitSessionEnd(null).get();
      assertThat(end.statusCode()).as(end.message()).isEqualTo(200);
      client.savePayloads("session-identity");

      Map<String, Object> closed = awaitClosedSession(sessionId);
      assertThat(closed.get("value")).isNotNull();
      // Note: the periodic HeartbeatPlugin timer is only stopped by a "sessionend" event, not by
      // submitSessionEnd(), so a further heartbeat for the ended session may follow after 60 s.
      assertThat(client.recorder().calls("heartbeat"))
          .extracting(c -> c.detail())
          .startsWith(identity, identity + " close");
    }
  }

  @Test
  void sessionWithClientManagedSessionId() throws Exception {
    try (TestClient client =
        project.newClient(
            c ->
                c.useSessions(false, Duration.ofSeconds(30), true)
                    .addDefaultTags(run, "managed"))) {
      String sessionId = client.client().getConfiguration().getCurrentSessionIdentifier();
      assertThat(sessionId).isNotBlank();
      client.client().createFeatureUsage("managed.feature").addTags("managed-usage").submit();
      client.flush();

      Map<String, Object> start = project.awaitEvent("type:session tag:" + run + " tag:managed");
      assertThat(start.get("reference_id")).isEqualTo(sessionId);
      Map<String, Object> usage = project.awaitEvent("tag:" + run + " tag:managed-usage");
      assertThat(at(usage, "data", "@ref:session")).isEqualTo(sessionId);

      SubmissionResponse end = client.client().submitSessionEnd(null).get();
      assertThat(end.statusCode()).as(end.message()).isEqualTo(200);
      client.savePayloads("session-managed");

      awaitClosedSession(sessionId);
    }
  }

  private Map<String, Object> awaitClosedSession(String sessionId) {
    return Await.value(
        "session " + sessionId + " to be closed",
        SESSION_CLOSE,
        () -> {
          List<Object> events = project.api().getEventsByReference(sessionId);
          return events.stream()
              .map(Json::object)
              .filter(e -> "session".equals(e.get("type")))
              .findFirst()
              .orElse(null);
        },
        e -> at(e, "data", "sessionend") != null);
  }
}
