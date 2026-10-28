package com.exceptionless;

import static org.assertj.core.api.Assertions.assertThat;

import com.exceptionless.TestClients.CapturingSubmissionClient;
import com.exceptionless.json.JsonWriter;
import com.exceptionless.models.Event;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The deployment environment: the root {@code environment} property of an event. */
class EventEnvironmentTest {
  private final CapturingSubmissionClient submission = new CapturingSubmissionClient();
  private final ExceptionlessConfiguration config =
      TestClients.config().submissionClient(submission);
  private final ExceptionlessClient client = new ExceptionlessClient(config);

  @AfterEach
  void close() {
    client.close();
  }

  private List<Map<String, Object>> sent() {
    assertThat(client.flush(Duration.ofSeconds(10))).isTrue();
    return submission.events();
  }

  @Test
  void appliesTheConfiguredDefaultToEveryEventType() {
    config.environment(" Production ");

    client.submitLog("log");
    client.submitException(new IllegalStateException("boom"));
    client.submitFeatureUsage("feature");
    client.submitNotFound("/missing");

    assertThat(sent())
        .hasSize(4)
        .allSatisfy(e -> assertThat(e).containsEntry("environment", "Production"));
  }

  @Test
  void sendsEventsWithoutAnEnvironmentWhenNoneIsConfigured() {
    client.submitLog("log");

    assertThat(sent())
        .singleElement()
        .satisfies(e -> assertThat(e).doesNotContainKey("environment"));
  }

  @Test
  void builderOverridesTheDefault() {
    config.environment("production");

    client.createLog("deploy").setEnvironment(" Staging ").submit();
    client.createLog("other").submit();

    assertThat(sent())
        .extracting(e -> e.get("environment"))
        .containsExactlyInAnyOrder("Staging", "production");
  }

  @Test
  void builderOverrideWorksWithoutADefault() {
    client.createLog("deploy").setEnvironment("staging").submit();

    assertThat(sent())
        .singleElement()
        .satisfies(e -> assertThat(e).containsEntry("environment", "staging"));
  }

  @Test
  void nullOverrideMeansNoOverrideAndTheDefaultApplies() {
    config.environment("production");

    client.createLog("null").setEnvironment(null).submit();

    assertThat(sent())
        .singleElement()
        .satisfies(e -> assertThat(e).containsEntry("environment", "production"));
  }

  @Test
  void nullClearsAnEarlierOverrideSoTheDefaultApplies() {
    config.environment("production");

    client.createLog("cleared").setEnvironment("staging").setEnvironment(null).submit();

    assertThat(sent())
        .singleElement()
        .satisfies(e -> assertThat(e).containsEntry("environment", "production"));
  }

  @Test
  void nullAfterAnInvalidOverrideLetsTheDefaultApplyAgain() {
    config.environment("production");

    client.createLog("cleared").setEnvironment("   ").setEnvironment(null).submit();

    assertThat(sent())
        .singleElement()
        .satisfies(e -> assertThat(e).containsEntry("environment", "production"));
  }

  @Test
  void nullWithoutADefaultSendsTheEventUnspecified() {
    client.createLog("none").setEnvironment("staging").setEnvironment(null).submit();

    assertThat(sent())
        .singleElement()
        .satisfies(e -> assertThat(e).doesNotContainKey("environment"));
  }

  @Test
  void validOverrideAfterAnInvalidOneIsSent() {
    config.environment("production");

    client.createLog("again").setEnvironment("   ").setEnvironment("qa").submit();

    assertThat(sent())
        .singleElement()
        .satisfies(e -> assertThat(e).containsEntry("environment", "qa"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "stag\ning"})
  void invalidOverrideSendsTheEventUnspecifiedInsteadOfTheDefault(String invalid) {
    config.environment("production");

    client.createLog("invalid").setEnvironment(invalid).submit();

    assertThat(sent())
        .singleElement()
        .satisfies(e -> assertThat(e).doesNotContainKey("environment"));
  }

  @Test
  void tooLongOverrideSendsTheEventUnspecifiedInsteadOfTheDefault() {
    config.environment("production");

    client.createLog("long").setEnvironment("x".repeat(65)).submit();
    client.createLog("max").setEnvironment("x".repeat(64)).submit();

    assertThat(sent())
        .extracting(e -> e.get("environment"))
        .containsExactlyInAnyOrder(null, "x".repeat(64));
  }

  @Test
  void invalidDefaultSendsEventsUnspecified() {
    config.environment("x".repeat(65));

    client.submitLog("log");

    assertThat(sent())
        .singleElement()
        .satisfies(e -> assertThat(e).doesNotContainKey("environment"));
  }

  @Test
  void anEventThatAlreadyHasAnEnvironmentKeepsIt() {
    config.environment("production");
    Event event = new Event();
    event.setType("log");
    event.setEnvironment("qa");

    client.submitEvent(event, null);

    assertThat(sent())
        .singleElement()
        .satisfies(e -> assertThat(e).containsEntry("environment", "qa"));
  }

  @Test
  void pluginsThatSetAnInvalidEnvironmentStillProduceAValidPayload() {
    config.environment("production");
    config.addPlugin("late", 5000, ctx -> ctx.getEvent().setEnvironment("bad\u0000value"));

    client.submitLog("plugin");

    assertThat(sent())
        .singleElement()
        .satisfies(e -> assertThat(e).doesNotContainKey("environment"));
  }

  @Test
  void pluginsThatSetAPaddedEnvironmentSendItTrimmed() {
    config.addPlugin("late", 5000, ctx -> ctx.getEvent().setEnvironment("  Qa  "));

    client.submitLog("plugin");

    assertThat(sent())
        .singleElement()
        .satisfies(e -> assertThat(e).containsEntry("environment", "Qa"));
  }

  @Test
  void submittingHandlersCannotQueueAnInvalidEnvironment() {
    client.addSubmittingEventHandler(
        submitting -> submitting.getEvent().setEnvironment("x".repeat(100)));

    client.submitLog("handler");

    assertThat(sent())
        .singleElement()
        .satisfies(e -> assertThat(e).doesNotContainKey("environment"));
  }

  @Test
  void isSeparateFromTheMachineEnvironmentData() {
    config.environment("production");

    client.submitLog("log");

    Map<String, Object> event = sent().get(0);
    assertThat(event).containsEntry("environment", "production");
    // @environment keeps describing the machine, never the deployment environment.
    assertThat(TestClients.data(event)).containsKey("@environment");
    assertThat(TestClients.path(event, "data.@environment.o_s_name")).isNotNull();
    assertThat(TestClients.data(event).get("@environment")).isNotEqualTo("production");
  }

  @Test
  void serializesTheEnvironmentAtTheEventRoot() {
    Event event = new Event();
    event.setType("log");
    event.setEnvironment("production");

    assertThat(JsonWriter.toJson(event)).contains("\"environment\":\"production\"");
  }

  @Test
  void omitsTheEnvironmentWhenUnset() {
    Event event = new Event();
    event.setType("log");
    assertThat(JsonWriter.toJson(event)).doesNotContain("environment");

    event.setEnvironment("production");
    event.setEnvironment(null);
    assertThat(JsonWriter.toJson(event)).doesNotContain("environment");
  }

  @Test
  void eventSetterNormalizes() {
    Event event = new Event();
    event.setEnvironment(" Production ");
    assertThat(event.getEnvironment()).isEqualTo("Production");
    event.setEnvironment("x".repeat(65));
    assertThat(event.getEnvironment()).isNull();
  }
}
