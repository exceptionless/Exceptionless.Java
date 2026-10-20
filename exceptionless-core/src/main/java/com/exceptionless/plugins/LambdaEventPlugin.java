package com.exceptionless.plugins;

import java.util.function.Consumer;

/** A plugin created by {@link EventPlugin#of}. */
record LambdaEventPlugin(String name, int priority, Consumer<EventPluginContext> action)
    implements EventPlugin {

  @Override
  public String getName() {
    return name;
  }

  @Override
  public int getPriority() {
    return priority;
  }

  @Override
  public void run(EventPluginContext context) {
    action.accept(context);
  }
}
