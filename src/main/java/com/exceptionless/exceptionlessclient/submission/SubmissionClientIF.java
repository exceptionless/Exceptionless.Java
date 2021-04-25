package com.exceptionless.exceptionlessclient.submission;

import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.UserDescription;

import java.util.List;

public interface SubmissionClientIF {
  SubmissionResponse postEvents(
      List<Event> events);

  SubmissionResponse postUserDescription(
      String referenceId,
      UserDescription description);

  void sendHeartBeat(String sessionIdOrUserId, boolean closeSession);
}
