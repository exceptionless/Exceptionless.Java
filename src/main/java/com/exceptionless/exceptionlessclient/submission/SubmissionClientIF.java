package com.exceptionless.exceptionlessclient.submission;

import com.prashantchaubey.exceptionlessclient.models.Event;
import com.prashantchaubey.exceptionlessclient.models.UserDescription;
import com.prashantchaubey.exceptionlessclient.models.submission.SubmissionResponse;

import java.util.List;

public interface SubmissionClientIF {
  SubmissionResponse postEvents(
      List<Event> events,
      boolean isAppExiting);

  SubmissionResponse postUserDescription(
      String referenceId,
      UserDescription description);

  void sendHeartBeat(String sessionIdOrUserId, boolean closeSession);
}
