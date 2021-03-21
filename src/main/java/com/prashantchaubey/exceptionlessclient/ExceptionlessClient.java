package com.prashantchaubey.exceptionlessclient;

import com.prashantchaubey.exceptionlessclient.configuration.ConfigurationManager;
import com.prashantchaubey.exceptionlessclient.models.Event;
import com.prashantchaubey.exceptionlessclient.plugins.ContextData;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.Timer;
import java.util.TimerTask;

@Builder(builderClassName = "ExceptionlessClientInternalBuilder")
@Getter
public class ExceptionlessClient {
  private static final int UPDATE_SETTINGS_TIMER_INITIAL_DELAY = 5000;

  private ConfigurationManager configurationManager;
  // todo I moved it from configuration; check we can remove this
  @Builder.Default private boolean enabled = true;

  // lombok ignored fields
  private Timer $updateSettingsTimer;

  public static ExceptionlessClient from(String apiKey, String serverUrl) {
    return ExceptionlessClient.builder()
        .configurationManager(ConfigurationManager.from(apiKey, serverUrl))
        .build();
  }

  public Event.EventBuilder createException(Exception exception) {
    ContextData contextData = new ContextData();
    contextData.setException(exception);
    return createEvent(contextData).type("error");
  }

  public Event.EventBuilder createEvent(ContextData contextData) {
    return Event.builder(contextData, configurationManager).date(LocalDate.now());
  }

  public static class ExceptionlessClientBuilder extends ExceptionlessClientInternalBuilder {
    ExceptionlessClientBuilder() {
      super();
    }

    @Override
    public ExceptionlessClient build() {
      ExceptionlessClient client = super.build();
      client.init();

      return client;
    }
  }

  public static ExceptionlessClientBuilder builder() {
    return new ExceptionlessClientBuilder();
  }

  private void init() {
    $updateSettingsTimer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            configurationManager.getSettingsManager().updateSettingsThreadSafe();
          }
        },
        UPDATE_SETTINGS_TIMER_INITIAL_DELAY,
        configurationManager.getConfiguration().getUpdateSettingsWhenIdleInterval());
  }

  public static void main(String[] args) {
    System.out.println("Hello World!");
  }
}
