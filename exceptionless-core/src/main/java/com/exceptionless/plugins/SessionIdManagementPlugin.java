package com.exceptionless.plugins;

import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.internal.Ids;
import com.exceptionless.models.Event;

/**
 * Generates session ids on the client, for apps that track sessions without a signed in user.
 * Session start events get a new id, and every other event references the current session.
 */
public final class SessionIdManagementPlugin implements EventPlugin {
  public static final int PRIORITY = 25;

  @Override
  public int getPriority() {
    return PRIORITY;
  }

  @Override
  public void run(EventPluginContext context) {
    ExceptionlessConfiguration config = context.getConfiguration();
    Event event = context.getEvent();
    if (event.isSessionStart()) {
      String id = Ids.newGuid();
      config.currentSessionIdentifier(id);
      event.setReferenceId(id);
      return;
    }
    String current = config.getCurrentSessionIdentifier();
    if (current == null) {
      return;
    }
    if (event.isSessionEnd()) {
      event.setReferenceId(current);
      config.currentSessionIdentifier(null);
    } else if (event.getSessionId() == null) {
      event.setEventReference("session", current);
    }
  }
}
