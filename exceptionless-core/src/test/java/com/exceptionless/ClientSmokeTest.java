package com.exceptionless;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClientSmokeTest {
  @Test
  void sendsErrorWithSnakeCaseStackTrace() throws Exception {
    try (FakeServer server = new FakeServer();
        ExceptionlessClient client =
            new ExceptionlessClient(
                new ExceptionlessConfiguration()
                    .apiKey("LhhP1C9gijpSKCslHHCvwdSIz298twx271nTest")
                    .serverUrl(server.url())
                    .captureUncaughtExceptions(false)
                    .registerShutdownHook(false))) {
      client.submitException(new IllegalStateException("boom", new RuntimeException("cause")));
      assertThat(client.flush(Duration.ofSeconds(10))).isTrue();

      FakeServer.Request request = server.take("/api/v2/events", Duration.ofSeconds(5));
      assertThat(request.header("Authorization"))
          .isEqualTo("Bearer LhhP1C9gijpSKCslHHCvwdSIz298twx271nTest");
      assertThat(request.header("User-Agent")).matches("exceptionless-java/\\d+\\.\\d+\\.\\d+");
      List<Map<String, Object>> events = request.events();
      assertThat(events).hasSize(1);
      Map<String, Object> event = events.get(0);
      assertThat(event.get("type")).isEqualTo("error");
      @SuppressWarnings("unchecked")
      Map<String, Object> data = (Map<String, Object>) event.get("data");
      @SuppressWarnings("unchecked")
      Map<String, Object> error = (Map<String, Object>) data.get("@error");
      assertThat(error.get("type")).isEqualTo("java.lang.IllegalStateException");
      assertThat(error.get("message")).isEqualTo("boom");
      assertThat(error).containsKey("stack_trace").containsKey("inner");
    }
  }
}
