package com.prashantchaubey.exceptionlessclient.storage;

import com.prashantchaubey.exceptionlessclient.models.Event;
import com.prashantchaubey.exceptionlessclient.models.settings.ServerSettings;

public interface StorageProviderIF {
    StorageIF<Event> getQueue();
    StorageIF<ServerSettings> getSettings();
}
