package com.exceptionless.plugins;

import com.exceptionless.internal.Log;
import com.exceptionless.internal.Wildcard;
import com.exceptionless.models.RequestInfo;

/** Discards events from requests whose user agent matches a bot pattern. */
public final class IgnoreUserAgentPlugin implements EventPlugin {
  public static final int PRIORITY = 65;
  private static final Log LOG = Log.get(IgnoreUserAgentPlugin.class);

  @Override
  public int getPriority() {
    return PRIORITY;
  }

  @Override
  public void run(EventPluginContext context) {
    RequestInfo request = context.getEvent().getRequestInfo();
    if (request == null || request.getUserAgent() == null) {
      return;
    }
    String userAgent = request.getUserAgent();
    if (Wildcard.matchesAny(userAgent, context.getConfiguration().getUserAgentBotPatterns())) {
      LOG.debug(() -> "Cancelling event from bot user agent " + userAgent);
      context.cancel();
    }
  }
}
