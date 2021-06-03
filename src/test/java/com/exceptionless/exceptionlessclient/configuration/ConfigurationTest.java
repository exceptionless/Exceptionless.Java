package com.exceptionless.exceptionlessclient.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.beans.PropertyChangeListener;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class ConfigurationTest {
  @Mock private PropertyChangeListener listener;

  @Test
  public void itCanSetDefaultValueHeartBeatServerUrlToServerUrlIfAbsent() {
    Configuration configuration =
        Configuration.builder().serverUrl("test-server-url").apiKey("12345678abcdef").build();

    assertThat(configuration.getHeartbeatServerUrl()).isEqualTo("test-server-url");
  }

  @Test
  public void itCanNotifyChangesToListeners() {
    Configuration configuration =
        Configuration.builder()
            .apiKey("old-api-key")
            .serverUrl("old-server-url")
            .configServerUrl("old-config-server-url")
            .heartbeatServerUrl("old-heartbeat-server-url")
            .updateSettingsWhenIdleInterval(1L)
            .submissionBatchSize(50)
            .settingsClientTimeoutInMillis(10)
            .submissionClientTimeoutInMillis(10)
            .build();
    configuration.addPropertyChangeListener(listener);

    configuration.setApiKey("new-api-key");
    configuration.setServerUrl("new-server-url");
    configuration.setConfigServerUrl("new-config-server-url");
    configuration.setHeartbeatServerUrl("new-heartbeat-server-url");
    configuration.setUpdateSettingsWhenIdleInterval(2L);
    configuration.setSubmissionBatchSize(100);
    configuration.setSubmissionClientTimeoutInMillis(20);
    configuration.setSettingsClientTimeoutInMillis(20);

    List<String> properties =
        List.of(
            "apiKey",
            "serverUrl",
            "configServerUrl",
            "heartbeatServerUrl",
            "updateSettingsWhenIdleInterval",
            "submissionBatchSize",
            "submissionClientTimeoutInMillis",
            "settingsClientTimeoutInMillis");
    List<Object> oldValues =
        List.of(
            "old-api-key",
            "old-server-url",
            "old-config-server-url",
            "old-heartbeat-server-url",
            1L,
            50,
            10,
            10);
    List<Object> newValues =
        List.of(
            "new-api-key",
            "new-server-url",
            "new-config-server-url",
            "new-heartbeat-server-url",
            2L,
            100,
            20,
            20);

    properties.forEach(
        property ->
            verify(listener, times(1))
                .propertyChange(
                    argThat(
                        event ->
                            event.getPropertyName().equals(property)
                                && event
                                    .getOldValue()
                                    .equals(oldValues.get(properties.indexOf(property)))
                                && event
                                    .getNewValue()
                                    .equals(newValues.get(properties.indexOf(property))))));
  }
}
