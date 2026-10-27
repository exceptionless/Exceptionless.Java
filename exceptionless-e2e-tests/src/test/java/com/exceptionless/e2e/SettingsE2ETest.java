package com.exceptionless.e2e;

import static com.exceptionless.e2e.support.Json.object;
import static org.assertj.core.api.Assertions.assertThat;

import com.exceptionless.ExceptionlessClient;
import com.exceptionless.LogLevel;
import com.exceptionless.e2e.support.Await;
import com.exceptionless.e2e.support.ExceptionlessServer;
import com.exceptionless.e2e.support.RecordingSubmissionClient.Call;
import com.exceptionless.e2e.support.TestProject;
import com.exceptionless.e2e.support.TestProject.TestClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Project settings: fetched from the server, applied by the client, refreshed on config version
 * changes.
 */
@Tag("e2e")
class SettingsE2ETest {
  private final String run = "e2e-" + UUID.randomUUID().toString().substring(0, 8);

  @Test
  void settingsRoundTripAndAreApplied() throws Exception {
    TestProject project = ExceptionlessServer.get().createProject("Settings");
    project.api().setProjectConfig(project.id(), "@@log:*", "Warn");
    project.api().setProjectConfig(project.id(), "@@log:com.example.noisy.*", "Off");
    project
        .api()
        .setProjectConfig(project.id(), "@@error:java.lang.IllegalArgumentException", "false");
    project.api().setProjectConfig(project.id(), "@@usage:hidden.feature", "false");
    int serverVersion =
        ((Number) project.api().getProjectConfig(project.id()).get("version")).intValue();

    // Polling every 2 hours: after the initial fetch, only the config version header can refresh
    // settings.
    try (TestClient testClient =
        project.newClient(c -> c.updateSettingsWhenIdleInterval(Duration.ofHours(2)))) {
      ExceptionlessClient client = testClient.client();
      // The first fetch is scheduled 5 s after startup; also fetch explicitly.
      Await.until(
          "the scheduled settings fetch",
          Duration.ofSeconds(30),
          () -> !testClient.recorder().calls("settings").isEmpty());
      client.updateSettings().get();

      Map<String, String> settings = client.getConfiguration().getSettings().asMap();
      assertThat(settings)
          .containsEntry("@@log:*", "Warn")
          .containsEntry("@@log:com.example.noisy.*", "Off")
          .containsEntry("@@error:java.lang.IllegalArgumentException", "false")
          .containsKey("@@UserAgentBotPatterns");
      assertThat(testClient.recorder().calls("settings"))
          .anySatisfy(c -> assertThat(c.configVersion()).isEqualTo(serverVersion));

      // The client filters with them.
      assertThat(
              client
                  .createLog("com.example.App", "info", LogLevel.INFO)
                  .addTags(run)
                  .submit()
                  .isCancelled())
          .isTrue();
      assertThat(
              client
                  .createLog("com.example.App", "warn", LogLevel.WARN)
                  .addTags(run, "kept-log")
                  .submit()
                  .isCancelled())
          .isFalse();
      assertThat(
              client
                  .createLog("com.example.noisy.Worker", "error", LogLevel.ERROR)
                  .addTags(run)
                  .submit()
                  .isCancelled())
          .isTrue();
      assertThat(
              client
                  .createException(new IllegalArgumentException("ignored"))
                  .addTags(run)
                  .submit()
                  .isCancelled())
          .isTrue();
      assertThat(
              client
                  .createException(new IllegalStateException("kept"))
                  .addTags(run, "kept-error")
                  .submit()
                  .isCancelled())
          .isFalse();
      assertThat(client.createFeatureUsage("hidden.feature").addTags(run).submit().isCancelled())
          .isTrue();
      testClient.flush();
      project.awaitEvents("tag:" + run, 2);
      assertThat(project.api().countEvents(project.id(), "tag:" + run)).isEqualTo(2);

      // A settings change on the server bumps the config version, which the server returns in the
      // X-Exceptionless-ConfigVersion header of every event post; the client then refetches.
      int settingsCalls = testClient.recorder().calls("settings").size();
      project.api().setProjectConfig(project.id(), "@@DataExclusions", "*secret*");
      int newVersion =
          ((Number) project.api().getProjectConfig(project.id()).get("version")).intValue();
      assertThat(newVersion).isGreaterThan(serverVersion);

      client
          .createLog("com.example.App", "trigger", LogLevel.ERROR)
          .addTags(run, "trigger")
          .submit();
      testClient.flush();
      Await.until(
          "the client to apply the new settings",
          Duration.ofSeconds(20),
          () ->
              "*secret*"
                  .equals(client.getConfiguration().getSettings().getString("@@DataExclusions")));
      List<Call> events = testClient.recorder().calls("events");
      assertThat(events.get(events.size() - 1).configVersion()).isEqualTo(newVersion);
      assertThat(testClient.recorder().calls("settings")).hasSizeGreaterThan(settingsCalls);

      // And the new data exclusion applies.
      client
          .createLog("com.example.App", "with data", LogLevel.ERROR)
          .addTags(run, "exclusions")
          .setProperty("api_secret", "hunter2")
          .setProperty("visible", "yes")
          .submit();
      testClient.flush();
      testClient.savePayloads("settings");
      Map<String, Object> stored = project.awaitEvent("tag:" + run + " tag:exclusions");
      assertThat(object(stored.get("data")))
          .containsEntry("visible", "yes")
          .doesNotContainKey("api_secret");
    }
  }
}
