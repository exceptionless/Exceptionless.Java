package com.exceptionless.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.TestClients;
import com.exceptionless.TestClients.CapturingSubmissionClient;
import com.exceptionless.storage.InMemoryKeyValueStorage;
import com.exceptionless.submission.SettingsResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettingsManagerTest {
  private final ExceptionlessConfiguration config = TestClients.config();
  private final CapturingSubmissionClient submission = new CapturingSubmissionClient();
  private final InMemoryKeyValueStorage storage = new InMemoryKeyValueStorage();
  private final SettingsManager manager = new SettingsManager(config, submission, storage);

  @Test
  void appliesAndPersistsNewSettings() {
    submission.settingsResponse = new SettingsResponse.Updated(Map.of("@@log:*", "info"), 4);
    manager.updateSettings();
    assertThat(config.getSettings().getString("@@log:*")).isEqualTo("info");
    assertThat(manager.getVersion()).isEqualTo(4);
    assertThat(storage.read(SettingsManager.STORAGE_KEY))
        .contains("\"version\":4")
        .contains("@@log:*");
    assertThat(submission.settingsRequests).containsExactly(0);
  }

  @Test
  void sendsCurrentVersionAndKeepsSettingsWhenNotModified() {
    submission.settingsResponse = new SettingsResponse.Updated(Map.of("a", "1"), 2);
    manager.updateSettings();
    submission.settingsResponse = new SettingsResponse.NotModified();
    manager.updateSettings();
    assertThat(submission.settingsRequests).containsExactly(0, 2);
    assertThat(config.getSettings().getString("a")).isEqualTo("1");
  }

  @Test
  void removedServerSettingsAreDropped() {
    submission.settingsResponse = new SettingsResponse.Updated(Map.of("a", "1", "b", "2"), 1);
    manager.updateSettings();
    submission.settingsResponse = new SettingsResponse.Updated(Map.of("a", "1"), 2);
    manager.updateSettings();
    assertThat(config.getSettings().getString("b")).isNull();
  }

  @Test
  void localSettingsSurviveButServerWins() {
    config.setting("@@log:*", "error").setting("local", "yes");
    submission.settingsResponse = new SettingsResponse.Updated(Map.of("@@log:*", "debug"), 1);
    manager.updateSettings();
    assertThat(config.getSettings().getString("@@log:*")).isEqualTo("debug");
    assertThat(config.getSettings().getString("local")).isEqualTo("yes");
  }

  @Test
  void failuresKeepCurrentSettings() {
    submission.settingsResponse = new SettingsResponse.Updated(Map.of("a", "1"), 1);
    manager.updateSettings();
    submission.settingsResponse = new SettingsResponse.Failed("boom");
    manager.updateSettings();
    assertThat(config.getSettings().getString("a")).isEqualTo("1");
    assertThat(manager.getVersion()).isEqualTo(1);
  }

  @Test
  void loadsSavedSettings() {
    storage.write(
        SettingsManager.STORAGE_KEY,
        "{\"version\":9,\"settings\":{\"@@DataExclusions\":\"*secret*\"}}");
    assertThat(manager.loadSavedSettings()).isTrue();
    assertThat(manager.getVersion()).isEqualTo(9);
    assertThat(config.getDataExclusions()).contains("*secret*");
  }

  @Test
  void ignoresCorruptSavedSettings() {
    storage.write(SettingsManager.STORAGE_KEY, "{not json");
    assertThat(manager.loadSavedSettings()).isFalse();
    assertThat(
            new SettingsManager(config, submission, new InMemoryKeyValueStorage())
                .loadSavedSettings())
        .isFalse();
  }

  @Test
  void checkVersionOnlyUpdatesWhenNewer() {
    submission.settingsResponse = new SettingsResponse.Updated(Map.of(), 3);
    manager.checkVersion(null);
    manager.checkVersion(0);
    assertThat(submission.settingsRequests).isEmpty();
    manager.checkVersion(3);
    assertThat(submission.settingsRequests).containsExactly(0);
    manager.checkVersion(3);
    assertThat(submission.settingsRequests).hasSize(1);
  }

  @Test
  void doesNothingWhenDisabled() {
    config.enabled(false);
    manager.updateSettings();
    assertThat(submission.settingsRequests).isEmpty();
  }
}
