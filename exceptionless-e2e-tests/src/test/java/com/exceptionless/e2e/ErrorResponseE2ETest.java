package com.exceptionless.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.exceptionless.ExceptionlessClient;
import com.exceptionless.LogLevel;
import com.exceptionless.e2e.support.Await;
import com.exceptionless.e2e.support.ExceptionlessServer;
import com.exceptionless.e2e.support.Json;
import com.exceptionless.e2e.support.RecordingSubmissionClient.Call;
import com.exceptionless.e2e.support.TestProject;
import com.exceptionless.e2e.support.TestProject.TestClient;
import com.exceptionless.submission.SubmissionResponse;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** How the client reacts to the real server's error responses. */
@Tag("e2e")
class ErrorResponseE2ETest {

  @Test
  void invalidApiKeyDropsTheBatchAndSuspendsSubmission() throws Exception {
    TestProject project = ExceptionlessServer.get().createProject("Errors");
    String invalidKey = "InvalidKey0000000000000000000000000000000";
    try (TestClient testClient = project.newClient(invalidKey, c -> {})) {
      ExceptionlessClient client = testClient.client();
      client.createLog("ErrorResponses", "first", LogLevel.ERROR).submit();
      client.createLog("ErrorResponses", "second", LogLevel.ERROR).submit();

      // 401: the batch is dropped (the queue drains) and submission is suspended.
      assertThat(testClient.client().flush(Duration.ofSeconds(30))).isTrue();
      assertThat(testClient.recorder().calls("events"))
          .singleElement()
          .satisfies(c -> assertThat(c.status()).isEqualTo(401));
      assertThat(client.getQueue().size()).isZero();
      assertThat(client.getQueue().isSuspended()).isTrue();

      // While suspended nothing is sent; new events wait in the queue.
      client.createLog("ErrorResponses", "third", LogLevel.ERROR).submit();
      assertThat(client.flush(Duration.ofSeconds(5))).isFalse();
      assertThat(client.getQueue().size()).isEqualTo(1);
      assertThat(testClient.recorder().calls("events")).hasSize(1);

      // Settings and heartbeats are rejected too, without breaking anything.
      client.updateSettings().get();
      assertThat(testClient.recorder().calls("settings"))
          .allSatisfy(c -> assertThat(c.message()).contains("401"));
      SubmissionResponse heartbeat = client.submitSessionHeartbeat("someone@example.com").get();
      assertThat(heartbeat.statusCode()).isEqualTo(401);
      testClient.savePayloads("invalid-api-key");
    }
    assertThat(project.api().countEvents(project.id(), "source:ErrorResponses")).isZero();
  }

  @Test
  void oversizedBatchIsSplitAfter413() {
    // The server rejects request bodies over 200,000 bytes (MaximumEventPostSize, measured after
    // compression) with 413. Random text barely compresses, so 50 events of 15 KB each don't fit.
    TestProject project = ExceptionlessServer.get().createProject("Too large");
    String run = "e2e-" + UUID.randomUUID().toString().substring(0, 8);
    Random random = new Random(42);
    try (TestClient testClient = project.newClient(c -> {})) {
      for (int i = 0; i < 50; i++) {
        testClient
            .client()
            .createLog("TooLarge", "Large event " + i, LogLevel.INFO)
            .addTags(run)
            .setProperty("payload", randomText(random, 15_000))
            .submit();
      }
      testClient.flush();
      testClient.savePayloads("too-large");
      List<Call> calls = testClient.recorder().calls("events");
      assertThat(calls).anySatisfy(c -> assertThat(c.status()).isEqualTo(413));
      assertThat(calls)
          .filteredOn(c -> c.status() == 202)
          .extracting(c -> Json.array(Json.parse(c.body())).size())
          .allSatisfy(size -> assertThat(size).isLessThan(50));
      long count =
          Await.value(
              "50 large events",
              Await.EVENTS,
              () -> project.api().countEvents(project.id(), "tag:" + run),
              c -> c >= 50);
      assertThat(count).isEqualTo(50);
    }
  }

  private static String randomText(Random random, int length) {
    String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
    }
    return sb.toString();
  }
}
