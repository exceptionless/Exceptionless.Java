package com.exceptionless.exceptionlessclient.services;

public interface LastReferenceIdManagerIF {
    String getLast();
    void clearLast();
    void setLast(String eventId);
}
