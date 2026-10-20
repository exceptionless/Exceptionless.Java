package com.exceptionless.plugins;

import com.exceptionless.internal.Strings;
import com.exceptionless.models.Event;
import com.exceptionless.models.UserInfo;

/**
 * Discards session start events that can't be tied to a user or a client managed session id,
 * because the server can't track them.
 */
public final class CancelSessionsWithNoUserPlugin implements EventPlugin {
  public static final int PRIORITY = 900;

  @Override
  public int getPriority() {
    return PRIORITY;
  }

  @Override
  public void run(EventPluginContext context) {
    Event event = context.getEvent();
    if (!event.isSessionStart()) {
      return;
    }
    UserInfo user = event.getUserInfo();
    if ((user == null || Strings.isBlank(user.identity()))
        && Strings.isBlank(event.getReferenceId())) {
      context.cancel();
    }
  }
}
