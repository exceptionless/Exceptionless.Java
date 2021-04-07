package com.exceptionless.exceptionlessclient.lastreferenceidmanager;

import lombok.Builder;

public class DefaultLastReferenceIdManager implements LastReferenceIdManagerIF {
  private String lastReferencedId;

  @Builder
  public DefaultLastReferenceIdManager() {}

  @Override
  public String getLast() {
    return lastReferencedId;
  }

  @Override
  public void clearLast() {
    lastReferencedId = null;
  }

  @Override
  public void setLast(String eventId) {
    lastReferencedId = eventId;
  }
}
