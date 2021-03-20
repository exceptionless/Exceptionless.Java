package com.prashantchaubey.exceptionlessclient.plugins;

import com.prashantchaubey.exceptionlessclient.ExceptionlessClient;
import com.prashantchaubey.exceptionlessclient.logging.LogIF;
import com.prashantchaubey.exceptionlessclient.models.Event;

// todo think about moving it to models as it only contains presentation logic
public class EventPluginContext {
  private boolean cancelled;
  private ExceptionlessClient client;
  private Event event;
  private ContextData contextData;

  public EventPluginContext(ExceptionlessClient client, Event event) {
    this.client = client;
    this.event = event;
    this.contextData = new ContextData();
  }

  public EventPluginContext(ExceptionlessClient client, Event event, ContextData contextData) {
    this.client = client;
    this.event = event;
    this.contextData = contextData;
  }

  public LogIF log() {
    return this.client.getConfig().getLog();
  }
}
