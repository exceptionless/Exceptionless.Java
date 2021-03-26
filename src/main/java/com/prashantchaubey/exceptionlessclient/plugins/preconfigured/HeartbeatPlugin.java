package com.prashantchaubey.exceptionlessclient.plugins.preconfigured;

import com.prashantchaubey.exceptionlessclient.configuration.ConfigurationManager;
import com.prashantchaubey.exceptionlessclient.models.EventPluginContext;
import com.prashantchaubey.exceptionlessclient.models.UserInfo;
import com.prashantchaubey.exceptionlessclient.models.enums.EventPropertyKey;
import com.prashantchaubey.exceptionlessclient.plugins.EventPluginIF;

public class HeartbeatPlugin implements EventPluginIF {
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
    configurationManager.submitSessionHeartbeat(userInfo.getIdentity());
  }
}
