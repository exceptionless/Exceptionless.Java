package com.exceptionless;

import java.time.Duration;

/** Run in a child JVM by {@link JvmExitTest}. Never closes the client on purpose. */
public final class ExitProbe {
  private ExitProbe() {}

  public static void main(String[] args) {
    Exceptionless.startup(
        c ->
            c.apiKey(TestClients.API_KEY)
                .serverUrl(args[0])
                .queueProcessInterval(Duration.ofHours(1)));
    Exceptionless.submitLog("ExitProbe", "before exit");
    if (args.length > 1 && args[1].equals("crash")) {
      throw new IllegalStateException("main crashed");
    }
  }
}
