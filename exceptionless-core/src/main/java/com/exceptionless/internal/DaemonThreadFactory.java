package com.exceptionless.internal;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Creates daemon threads so the client never keeps the JVM alive. */
public final class DaemonThreadFactory implements ThreadFactory {
  /** The name prefix of the client's background threads ({@code exceptionless-1}, ...). */
  public static final String CLIENT_THREAD_NAME = "exceptionless";

  private final String prefix;
  private final AtomicInteger counter = new AtomicInteger();

  public DaemonThreadFactory(String prefix) {
    this.prefix = prefix;
  }

  @Override
  public Thread newThread(Runnable runnable) {
    Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
    thread.setDaemon(true);
    return thread;
  }
}
