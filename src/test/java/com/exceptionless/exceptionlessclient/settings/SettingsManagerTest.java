package com.exceptionless.exceptionlessclient.settings;

import com.exceptionless.exceptionlessclient.exceptions.SettingsClientException;
import com.exceptionless.exceptionlessclient.storage.InMemoryStorage;
import com.exceptionless.exceptionlessclient.storage.InMemoryStorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.beans.PropertyChangeListener;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SettingsManagerTest {
  @Mock private InMemoryStorageProvider storageProvider;
  @Mock private DefaultSettingsClient settingsClient;
  @Mock private PropertyChangeListener listener;
  private SettingsManager settingsManager;
  private InMemoryStorage<ServerSettings> storage;

  @BeforeEach
  public void setup() {
    storage = InMemoryStorage.<ServerSettings>builder().maxItems(1).build();
    doReturn(storage).when(storageProvider).getSettings();

    settingsManager =
        SettingsManager.builder()
            .settingsClient(settingsClient)
            .storageProvider(storageProvider)
            .build();
  }

  @Test
  public void itReturnsADefaultValueIfNoServerSettingsArePresent() {
    ServerSettings serverSettings = settingsManager.getSavedServerSettings();

    assertThat(serverSettings.getVersion()).isEqualTo(0);
    assertThat(serverSettings.getSettings()).isEmpty();
  }

  @Test
  public void itCanGetSavedServerSettings() {
    ServerSettings expectedSettings =
        ServerSettings.builder().version(1L).settings(Map.of("key", "value")).build();
    storage.save(expectedSettings);

    ServerSettings actualSettings = settingsManager.getSavedServerSettings();

    assertThat(expectedSettings).isEqualTo(actualSettings);
  }

  @Test
  public void itDontUpdateSettingsIfCheckedVersionIsLessThanCurrentVersion() {
    ServerSettings serverSettings =
        ServerSettings.builder().version(3L).settings(Map.of("key", "value")).build();
    storage.save(serverSettings);

    settingsManager.checkVersion(1);

    verifyZeroInteractions(settingsClient);
  }

  @Test
  public void itDontUpdateSettingsIfCheckedVersionIsEqualToCurrentVersion() {
    ServerSettings serverSettings =
        ServerSettings.builder().version(3L).settings(Map.of("key", "value")).build();
    storage.save(serverSettings);

    settingsManager.checkVersion(3);

    verifyZeroInteractions(settingsClient);
  }

  @Test
  public void itCanUpdateSettingsIfCheckedVersionIsGreaterThanCurrentVersion() {
    ServerSettings serverSettings =
        ServerSettings.builder().version(3L).settings(Map.of("key", "value")).build();
    storage.save(serverSettings);

    ServerSettings newSettingsFromServer =
        ServerSettings.builder().version(4L).settings(Map.of("new-key", "new-value")).build();
    doReturn(SettingsResponse.builder().code(200).settings(newSettingsFromServer).build())
        .when(settingsClient)
        .getSettings(3);

    settingsManager.checkVersion(4);

    assertThat(storage.peek().getValue()).isEqualTo(newSettingsFromServer);
  }

  @Test
  public void itWillLetOnlyOneThreadUpdateSettingsAtATime()
      throws ExecutionException, InterruptedException {
    ServerSettings newSettingsFromServer =
        ServerSettings.builder().version(4L).settings(Map.of("new-key", "new-value")).build();
    doAnswer(
            invocation -> {
              Thread.sleep(1000);
              return SettingsResponse.builder().code(200).settings(newSettingsFromServer).build();
            })
        .when(settingsClient)
        .getSettings(anyLong());

    Future<?> future =
        Executors.newSingleThreadExecutor().submit(() -> settingsManager.updateSettings());
    settingsManager.updateSettings();
    future.get();

    verify(settingsClient, times(1)).getSettings(anyLong());
  }

  @Test
  public void itWillNotSaveSettingsIfSettingsAreNotModified() {
    doReturn(SettingsResponse.builder().code(304).body("test-message").build())
        .when(settingsClient)
        .getSettings(anyLong());

    settingsManager.updateSettings();

    assertThat(storage.peek()).isNull();
  }

  @Test
  public void itWillNotSaveSettingsIfUnableToGetSettingsFromServer() {
    doReturn(SettingsResponse.builder().code(400).body("test-message").build())
        .when(settingsClient)
        .getSettings(anyLong());

    settingsManager.updateSettings();

    assertThat(storage.peek()).isNull();
  }

  @Test
  public void itWillNotSaveSettingsIfNoSettingsReturnedByTheServer() {
    doReturn(SettingsResponse.builder().code(200).body("test-message").build())
        .when(settingsClient)
        .getSettings(anyLong());

    settingsManager.updateSettings();

    assertThat(storage.peek()).isNull();
  }

  @Test
  public void itCanHandleSettingsClientException() {
    doThrow(new SettingsClientException("test")).when(settingsClient).getSettings(anyLong());

    settingsManager.updateSettings();
  }

  @Test
  public void itCanSuccessfullyUpdateSettingsAndNotifyListeners() {
    ServerSettings newSettingsFromServer =
        ServerSettings.builder().version(4L).settings(Map.of("new-key", "new-value")).build();
    doReturn(SettingsResponse.builder().code(200).settings(newSettingsFromServer).build())
        .when(settingsClient)
        .getSettings(anyLong());

    settingsManager.addPropertyChangeListener(listener);
    settingsManager.updateSettings();

    assertThat(storage.peek().getValue()).isEqualTo(newSettingsFromServer);
    verify(listener, times(1))
        .propertyChange(
            argThat(
                event ->
                    event.getPropertyName().equals("settings")
                        && ((ServerSettings) event.getOldValue()).getVersion() == 0
                        && ((ServerSettings) event.getNewValue()).getVersion() == 4));
  }
}
