package com.exceptionless.plugins;

/** Records how the event was captured ({@code @submission_method}). */
public final class SubmissionMethodPlugin implements EventPlugin {
  public static final int PRIORITY = 90;

  @Override
  public int getPriority() {
    return PRIORITY;
  }

  @Override
  public void run(EventPluginContext context) {
    String method = context.getContextData().getSubmissionMethod();
    if (method != null && context.getEvent().getSubmissionMethod() == null) {
      context.getEvent().setSubmissionMethod(method);
    }
  }
}
