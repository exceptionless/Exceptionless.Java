package com.exceptionless.plugins;

import com.exceptionless.ExceptionlessClient;
import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.models.Event;
import com.exceptionless.models.KnownDataKeys;
import com.exceptionless.models.UserInfo;
import java.util.concurrent.ScheduledFuture;
import org.jspecify.annotations.Nullable;

/**
 * Keeps the current session alive by sending a heartbeat on an interval for the session id or user
 * identity of the most recent event.
 */
public final class HeartbeatPlugin implements EventPlugin {
  public static final int PRIORITY = 100;

  private @Nullable ScheduledFuture<?> heartbeat;
  private @Nullable String identity;

  @Override
  public int getPriority() {
    return PRIORITY;
  }

  @Override
  public void run(EventPluginContext context) {
    ExceptionlessConfiguration config = context.getConfiguration();
    Event event = context.getEvent();
    if (event.isSessionEnd()) {
      stop();
      return;
    }
    String id = config.getCurrentSessionIdentifier();
    if (id == null) {
      id = identityOf(event.getUserInfo());
    }
    if (id == null
        && config.getDefaultData().get(KnownDataKeys.USER_INFO) instanceof UserInfo user) {
      id = identityOf(user);
    }
    if (id != null) {
      start(context.getClient(), id, config);
    }
  }

  private static @Nullable String identityOf(@Nullable UserInfo user) {
    return user != null && user.identity() != null && !user.identity().isBlank()
        ? user.identity()
        : null;
  }

  private synchronized void start(
      ExceptionlessClient client, String id, ExceptionlessConfiguration config) {
    if (heartbeat != null && !heartbeat.isDone() && id.equals(identity)) {
      return;
    }
    stop();
    identity = id;
    heartbeat =
        client.scheduleAtFixedRate(
            () -> client.submitSessionHeartbeat(id),
            config.getHeartbeatInterval(),
            config.getHeartbeatInterval());
  }

  /** True while heartbeats are being sent. */
  public synchronized boolean isRunning() {
    return heartbeat != null && !heartbeat.isDone();
  }

  /** Stops sending heartbeats (called when the session ends). */
  public synchronized void stop() {
    if (heartbeat != null) {
      heartbeat.cancel(false);
      heartbeat = null;
    }
    identity = null;
  }

  @Override
  public void suspend(ExceptionlessClient client) {
    stop();
  }
}
