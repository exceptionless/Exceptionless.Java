package com.prashantchaubey.exceptionlessclient.plugins.preconfigured;

import com.prashantchaubey.exceptionlessclient.configuration.ConfigurationManager;
import com.prashantchaubey.exceptionlessclient.models.EventPluginContext;
import com.prashantchaubey.exceptionlessclient.plugins.EventPluginIF;
import lombok.Builder;

import java.util.Optional;

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
    Optional<String>maybeSubmissionMethod = eventPluginContext.getContext().getSubmissionMethod();
    if(!maybeSubmissionMethod.isPresent()){
      return;
    }

    String submissionMethod = maybeSubmissionMethod.get();
    eventPluginContext.getEvent().addSubmissionMethod(submissionMethod);
  }
}
