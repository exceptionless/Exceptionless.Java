package com.exceptionless.plugins;

import com.exceptionless.internal.Ids;
import com.exceptionless.internal.Strings;
import com.exceptionless.models.Event;

/**
 * Gives error events a random reference id, so users can quote it to support and you can attach
 * user descriptions with {@code updateUserEmailAndDescription}.
 */
public final class ReferenceIdPlugin implements EventPlugin {
  public static final int PRIORITY = 30;

  @Override
  public int getPriority() {
    return PRIORITY;
  }

  @Override
  public void run(EventPluginContext context) {
    Event event = context.getEvent();
    if (context.getConfiguration().isUseReferenceIds()
        && event.isError()
        && Strings.isBlank(event.getReferenceId())) {
      event.setReferenceId(Ids.newReferenceId());
    }
  }
}
