package com.prashantchaubey.exceptionlessclient.plugins.preconfigured;

import com.prashantchaubey.exceptionlessclient.configuration.ConfigurationManager;
import com.prashantchaubey.exceptionlessclient.models.Event;
import com.prashantchaubey.exceptionlessclient.models.EventPluginContext;
import com.prashantchaubey.exceptionlessclient.models.enums.EventType;
import com.prashantchaubey.exceptionlessclient.plugins.EventPluginIF;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Builder
@Getter
public class ReferenceIdPlugin implements EventPluginIF {
  @Override
  public int getPriority() {
    return 20;
  }

  @Override
  public void run(
      EventPluginContext eventPluginContext, ConfigurationManager configurationManager) {
    Event event = eventPluginContext.getEvent();
    if (!event.getType().equals(EventType.ERROR.value()) || event.getReferenceId() != null) {
      return;
    }
    event.setReferenceId(UUID.randomUUID().toString());
  }
}
