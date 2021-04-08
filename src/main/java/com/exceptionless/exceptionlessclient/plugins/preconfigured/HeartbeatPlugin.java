package com.exceptionless.exceptionlessclient.plugins.preconfigured;

import com.exceptionless.exceptionlessclient.configuration.ConfigurationManager;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.models.UserInfo;
import com.exceptionless.exceptionlessclient.plugins.EventPluginIF;
import lombok.Builder;

import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

public class HeartbeatPlugin implements EventPluginIF {
  private static final String HEART_BEAT_TIMER_NAME = "heart-beat-timer";

  private final int heartbeatInterval;
  private Timer heartbeatTimer;
  private String prevIdentity;

  @Builder
  public HeartbeatPlugin(int heartbeatInterval) {
    this.heartbeatInterval = heartbeatInterval;
    this.heartbeatTimer = new Timer(HEART_BEAT_TIMER_NAME);
  }

  @Override
  public int getPriority() {
    return 100;
  }

  @Override
  public void run(
      EventPluginContext eventPluginContext, ConfigurationManager configurationManager) {
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
              configurationManager.submitSessionHeartbeat(prevIdentity);
            } catch (Exception e) {
              configurationManager
                  .getLog()
                  .error(
                      String.format("Error in submitting hearbeat for identity: %s", prevIdentity),
                      e);
            }
          }
        },
        heartbeatInterval);
  }

  private void resetHeartbeatTimer() {
    heartbeatTimer.cancel();
    heartbeatTimer = new Timer(HEART_BEAT_TIMER_NAME);
  }
}
