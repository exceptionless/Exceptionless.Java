package com.exceptionless.plugins;

import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.internal.ErrorConverter;
import com.exceptionless.internal.ModuleCollector;
import com.exceptionless.models.Event;
import com.exceptionless.models.EventTypes;
import com.exceptionless.models.ModuleInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Converts the context exception into the {@code @error} model. Each exception is only reported
 * once, including when it is later reported again wrapped in another exception (or vice versa), so
 * an error that is both logged and caught by a framework integration doesn't show up twice.
 */
public final class ErrorPlugin implements EventPlugin {
  public static final int PRIORITY = 20;

  /** Exceptions already reported. Weak, so reported exceptions can still be collected. */
  private final Set<Throwable> processed = Collections.newSetFromMap(new WeakHashMap<>());

  @Override
  public int getPriority() {
    return PRIORITY;
  }

  @Override
  public void run(EventPluginContext context) {
    Throwable exception = context.getContextData().getException();
    if (exception == null) {
      return;
    }
    if (!markProcessed(exception)) {
      context.cancel();
      return;
    }
    Event event = context.getEvent();
    event.setType(EventTypes.ERROR);
    if (event.getError() != null) {
      return;
    }
    ExceptionlessConfiguration config = context.getConfiguration();
    List<ModuleInfo> modules = config.isIncludeModules() ? ModuleCollector.getModules() : List.of();
    event.setError(ErrorConverter.convert(exception, config::isDataExcluded, modules));
  }

  /**
   * Records the exception and its causes. Returns false when any of them was already reported, so a
   * wrapper of an already reported exception (or a cause of one) isn't sent again.
   */
  private boolean markProcessed(Throwable exception) {
    List<Throwable> chain = new ArrayList<>();
    for (Throwable t = exception;
        t != null && chain.size() < ErrorConverter.MAX_CAUSES && !chain.contains(t);
        t = ErrorConverter.safeCause(t)) {
      chain.add(t);
    }
    synchronized (processed) {
      for (Throwable t : chain) {
        if (processed.contains(t)) {
          return false;
        }
      }
      processed.addAll(chain);
    }
    return true;
  }

  /** Visible for tests: forgets which exceptions were reported. */
  void reset() {
    synchronized (processed) {
      processed.clear();
    }
  }
}
