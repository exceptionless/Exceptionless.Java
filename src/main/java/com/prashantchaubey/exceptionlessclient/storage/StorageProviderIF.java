package com.prashantchaubey.exceptionlessclient.storage;

public interface StorageProviderIF {
    StorageIF getQueue();
    StorageIF getSettings();
}
