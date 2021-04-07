package com.exceptionless.exceptionlessclient.plugins.preconfigured;

import com.exceptionless.exceptionlessclient.plugins.EventPluginIF;
import com.prashantchaubey.exceptionlessclient.configuration.ConfigurationManager;
import com.prashantchaubey.exceptionlessclient.models.EventPluginContext;
import com.prashantchaubey.exceptionlessclient.models.UserInfo;
import com.prashantchaubey.exceptionlessclient.plugins.EventPluginIF;
import lombok.Builder;

import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

public class HeartbeatPlugin implements EventPluginIF {
  private int heartbeatInterval;
  private Timer heartbeatTimer;
  private String prevIdentity;

  @Builder
  public HeartbeatPlugin(int heartbeatInterval) {
    this.heartbeatInterval = heartbeatInterval;
    this.heartbeatTimer = new Timer();
  }

  @Override
  public int getPriority() {
    return 100;
  }

  @Override
  public void run(
      EventPluginContext eventPluginContext, ConfigurationManager configurationManager) {
    Optional<UserInfo> maybeUserInfo = eventPluginContext.getEvent().getUserInfo();
    if (!maybeUserInfo.isPresent()) {
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
            configurationManager.submitSessionHeartbeat(prevIdentity);
          }
        },
        heartbeatInterval);
  }

  private void resetHeartbeatTimer(){
    heartbeatTimer.cancel();
    heartbeatTimer = new Timer();
  }
}
