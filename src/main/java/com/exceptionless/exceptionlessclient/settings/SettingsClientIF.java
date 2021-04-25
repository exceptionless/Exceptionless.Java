package com.exceptionless.exceptionlessclient.settings;


public interface SettingsClientIF {
    SettingsResponse getSettings(long version);
}
