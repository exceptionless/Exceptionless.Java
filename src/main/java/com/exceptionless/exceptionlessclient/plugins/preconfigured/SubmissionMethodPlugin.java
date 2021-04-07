package com.exceptionless.exceptionlessclient.plugins.preconfigured;

import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.plugins.EventPluginIF;
import com.prashantchaubey.exceptionlessclient.configuration.ConfigurationManager;
import com.prashantchaubey.exceptionlessclient.models.EventPluginContext;
import com.prashantchaubey.exceptionlessclient.plugins.EventPluginIF;
import lombok.Builder;

public class SubmissionMethodPlugin implements EventPluginIF {
  @Builder
  public SubmissionMethodPlugin() {}

  @Override
  public int getPriority() {
    return 100;
  }

  @Override
  public void run(
          EventPluginContext eventPluginContext, ConfigurationManager configurationManager) {
    String submissionMethod = eventPluginContext.getContext().getSubmissionMethod();
    if (submissionMethod == null) {
      return;
    }

    eventPluginContext.getEvent().addSubmissionMethod(submissionMethod);
  }
}
