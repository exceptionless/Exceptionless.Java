package com.exceptionless.exceptionlessclient.plugins.preconfigured;

import com.exceptionless.exceptionlessclient.configuration.Configuration;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.models.UserInfo;
import com.exceptionless.exceptionlessclient.plugins.EventPluginIF;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

@Slf4j
public class HeartbeatPlugin implements EventPluginIF {
  private static final String HEART_BEAT_TIMER_NAME = "heart-beat-timer";
  private static final Integer DEFAULT_PRIORITY = 100;
  private static final Integer DEFAULT_HEART_BEAT_INTERVAL_IN_SECS = 1;

  private final int heartbeatIntervalInSecs;
  private Timer heartbeatTimer;
  private String prevIdentity;

  @Builder
  public HeartbeatPlugin(Integer heartbeatIntervalInSecs) {
    this.heartbeatIntervalInSecs =
        heartbeatIntervalInSecs == null
            ? DEFAULT_HEART_BEAT_INTERVAL_IN_SECS
            : heartbeatIntervalInSecs;
    this.heartbeatTimer = new Timer(HEART_BEAT_TIMER_NAME);
  }

  @Override
  public int getPriority() {
    return DEFAULT_PRIORITY;
  }

  @Override
  public void run(
      EventPluginContext eventPluginContext, Configuration configuration) {
    Optional<UserInfo> maybeUserInfo = eventPluginContext.getEvent().getUserInfo();
    if (maybeUserInfo.isEmpty()) {
      return;
    }
    if (maybeUserInfo.get().getIdentity().equals(prevIdentity)) {
      return;
    }

    prevIdentity = maybeUserInfo.get().getIdentity();
    // reset if identity is changed
    resetHeartbeatTimer();
    heartbeatTimer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            try {
              configuration.submitSessionHeartbeat(prevIdentity);
            } catch (Exception e) {
              log.error(
                  String.format("Error in submitting hearbeat for identity: %s", prevIdentity), e);
            }
          }
        },
        heartbeatIntervalInSecs * 1000L,
        heartbeatIntervalInSecs * 1000L);
  }

  private void resetHeartbeatTimer() {
    heartbeatTimer.cancel();
    heartbeatTimer = new Timer(HEART_BEAT_TIMER_NAME);
  }
}
