package com.prashantchaubey.exceptionlessclient.submission;

import com.prashantchaubey.exceptionlessclient.configuration.Configuration;
import com.prashantchaubey.exceptionlessclient.models.Event;
import com.prashantchaubey.exceptionlessclient.models.UserDescription;

import java.util.List;
import java.util.function.Consumer;

public interface SubmissionClientIF {
  void postEvents(
      List<Event> events,
      Configuration config,
      Consumer<SubmissionResponse> responseHandler,
      boolean isAppExiting);

  void postUserDescription(
      String referenceId,
      UserDescription description,
      Configuration config,
      Consumer<SubmissionResponse> responseHandler);

  void getSettings(Configuration config, int version, Consumer<SettingsResponse> responseHandler);

  void sendHeartBeat(String sessionIdOrUserId, boolean closeSession, Configuration config);
}
