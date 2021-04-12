package com.exceptionless.exceptionlessclient.storage;


import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.settings.ServerSettings;

public interface StorageProviderIF {
    StorageIF<Event> getQueue();
    StorageIF<ServerSettings> getSettings();
}
