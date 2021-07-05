package com.exceptionless.exceptionlessclient.plugins.preconfigured;

import com.exceptionless.exceptionlessclient.configuration.Configuration;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.plugins.EventPluginIF;
import lombok.Builder;

public class SubmissionMethodPlugin implements EventPluginIF {
  private static final Integer DEFAULT_PRIORITY = 100;

  @Builder
  public SubmissionMethodPlugin() {}

  @Override
  public int getPriority() {
    return DEFAULT_PRIORITY;
  }

  @Override
  public void run(
      EventPluginContext eventPluginContext, Configuration configuration) {
    String submissionMethod = eventPluginContext.getContext().getSubmissionMethod();
    if (submissionMethod == null) {
      return;
    }

    eventPluginContext.getEvent().addSubmissionMethod(submissionMethod);
  }
}
