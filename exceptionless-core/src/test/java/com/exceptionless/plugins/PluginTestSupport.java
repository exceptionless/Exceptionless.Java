package com.exceptionless.plugins;

import com.exceptionless.ExceptionlessClient;
import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.TestClients;
import com.exceptionless.models.Event;

/** Runs single plugins against a real (offline) client. */
final class PluginTestSupport {
  private PluginTestSupport() {}

  static ExceptionlessClient client(ExceptionlessConfiguration config) {
    return new ExceptionlessClient(
        config.submissionClient(new TestClients.CapturingSubmissionClient()));
  }

  static EventPluginContext run(EventPlugin plugin, ExceptionlessClient client, Event event) {
    return run(plugin, client, event, new ContextData());
  }

  static EventPluginContext run(
      EventPlugin plugin, ExceptionlessClient client, Event event, ContextData data) {
    EventPluginContext context = new EventPluginContext(client, event, data);
    plugin.run(context);
    return context;
  }
}
