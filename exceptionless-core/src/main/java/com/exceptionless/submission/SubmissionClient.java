package com.exceptionless.submission;

import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.models.UserDescription;

/** Talks to the Exceptionless server. Calls block; the client makes them on its own thread. */
public interface SubmissionClient {
  /** Posts a JSON array of events to {@code /api/v2/events}. */
  SubmissionResponse postEvents(String eventsJson, ExceptionlessConfiguration config);

  /** Attaches a user's email address and description to the event with the reference id. */
  SubmissionResponse postUserDescription(
      String referenceId, UserDescription description, ExceptionlessConfiguration config);

  /** Fetches the project settings if they are newer than {@code version}. */
  SettingsResponse getSettings(int version, ExceptionlessConfiguration config);

  /** Sends a session heartbeat, or ends the session when {@code closeSession} is true. */
  SubmissionResponse sendHeartbeat(
      String sessionIdOrUserId, boolean closeSession, ExceptionlessConfiguration config);
}
