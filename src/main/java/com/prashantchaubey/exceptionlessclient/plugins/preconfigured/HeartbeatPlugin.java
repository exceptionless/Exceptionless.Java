package com.prashantchaubey.exceptionlessclient.plugins.preconfigured;

import com.prashantchaubey.exceptionlessclient.configuration.ConfigurationManager;
import com.prashantchaubey.exceptionlessclient.models.EventPluginContext;
import com.prashantchaubey.exceptionlessclient.models.UserInfo;
import com.prashantchaubey.exceptionlessclient.models.enums.EventPropertyKey;
import com.prashantchaubey.exceptionlessclient.plugins.EventPluginIF;
import lombok.Builder;

import java.util.Timer;
import java.util.TimerTask;

public class HeartbeatPlugin implements EventPluginIF {
  private int heartbeatInterval;
  private Timer heartbeatTimer;
  private String identity;

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
    UserInfo userInfo =
        (UserInfo) eventPluginContext.getEvent().getData().get(EventPropertyKey.USER.value());
    if (userInfo == null) {
      return;
    }
    heartbeatTimer.cancel(); // Cancel previous tasks
    heartbeatTimer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            configurationManager.submitSessionHeartbeat(userInfo.getIdentity());
          }
        },
        heartbeatInterval);
  }
}
