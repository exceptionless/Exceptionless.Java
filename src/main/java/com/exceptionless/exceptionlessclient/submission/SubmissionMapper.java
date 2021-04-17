package com.exceptionless.exceptionlessclient.submission;

import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.UserDescription;

public final class SubmissionMapper {
  private SubmissionMapper() {}

  public static UserDescriptionRequest toRequest(UserDescription userDescription) {
    return UserDescriptionRequest.builder()
        .description(userDescription.getDescription())
        .emailAddress(userDescription.getEmailAddress())
        .data(userDescription.getData())
        .build();
  }

  public static EventRequest toRequest(Event event) {
    return EventRequest.builder()
        .type(event.getType())
        .source(event.getSource())
        .date(event.getDate())
        .tags(event.getTags())
        .message(event.getMessage())
        .geo(event.getGeo())
        .referenceId(event.getReferenceId())
        .value(event.getValue())
        .count(event.getCount())
        .data(event.getData())
        .build();
  }
}
