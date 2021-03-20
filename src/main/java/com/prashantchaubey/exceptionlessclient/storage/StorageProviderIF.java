package com.prashantchaubey.exceptionlessclient.storage;

import com.prashantchaubey.exceptionlessclient.models.settings.ServerSettings;

public interface StorageProviderIF {
    StorageIF getQueue();
    StorageIF<ServerSettings> getSettings();
}
