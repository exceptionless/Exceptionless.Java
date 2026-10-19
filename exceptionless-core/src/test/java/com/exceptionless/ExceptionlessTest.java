package com.exceptionless;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.exceptionless.TestClients.CapturingSubmissionClient;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ExceptionlessTest {
  @AfterEach
  void reset() {
    Exceptionless.shutdown();
  }

  @Test
  void defaultClientIsConfiguredFromTheEnvironment() {
    assertThat(Exceptionless.hasDefault()).isFalse();
    ExceptionlessClient client = Exceptionless.getDefault();
    assertThat(Exceptionless.getDefault()).isSameAs(client);
    // From src/test/resources/exceptionless.properties.
    assertThat(client.getConfiguration().getVersion()).isEqualTo("from-file");
  }

  @Test
  void startupConfiguresAndStartsTheDefaultClient() {
    CapturingSubmissionClient submission = new CapturingSubmissionClient();
    Exceptionless.startup(
        c ->
            c.apiKey(TestClients.API_KEY)
                .submissionClient(submission)
                .captureUncaughtExceptions(false)
                .registerShutdownHook(false));
    Exceptionless.submitLog("static");
    Exceptionless.createFeatureUsage("feature").submit();
    Exceptionless.submitException(new RuntimeException("x"));
    assertThat(Exceptionless.flush(Duration.ofSeconds(5))).isTrue();
    assertThat(submission.events())
        .extracting(e -> e.get("type"))
        .containsExactly("log", "usage", "error");
    assertThat(Exceptionless.getLastReferenceId()).isNotNull();
    assertThatThrownBy(() -> Exceptionless.startup(c -> {}))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void setDefaultReplacesTheClient() {
    ExceptionlessClient mine = new ExceptionlessClient(TestClients.config());
    Exceptionless.setDefault(mine);
    assertThat(Exceptionless.getDefault()).isSameAs(mine);
    Exceptionless.shutdown();
    assertThat(mine.isStarted()).isFalse();
    assertThat(Exceptionless.getDefault()).isNotSameAs(mine);
  }

  @Test
  void startupReplacesADefaultClientThatStartedUnconfigured() {
    Exceptionless.submitLog("used before configuration");
    ExceptionlessClient unconfigured = Exceptionless.getDefault();
    assertThat(unconfigured.isStarted()).isTrue();
    CapturingSubmissionClient submission = new CapturingSubmissionClient();
    ExceptionlessClient configured =
        Exceptionless.startup(
            c ->
                c.apiKey(TestClients.API_KEY)
                    .submissionClient(submission)
                    .captureUncaughtExceptions(false)
                    .registerShutdownHook(false));
    assertThat(configured).isNotSameAs(unconfigured);
    assertThat(unconfigured.isStarted()).isFalse();
    Exceptionless.submitLog("after configuration");
    Exceptionless.flush(Duration.ofSeconds(5));
    assertThat(submission.events())
        .extracting(e -> e.get("message"))
        .containsExactly("after configuration");
  }

  @Test
  void unconfiguredDefaultClientIsDisabled() {
    Exceptionless.submitLog("goes nowhere");
    assertThat(Exceptionless.getConfiguration().isEnabled()).isFalse();
  }
}
