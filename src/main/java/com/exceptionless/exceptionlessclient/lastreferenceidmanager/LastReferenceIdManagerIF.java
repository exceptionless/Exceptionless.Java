package com.exceptionless.exceptionlessclient.lastreferenceidmanager;

public interface LastReferenceIdManagerIF {
    String getLast();
    void clearLast();
    void setLast(String eventId);
}
