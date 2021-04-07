package com.exceptionless.exceptionlessclient.settings;


import com.exceptionless.exceptionlessclient.models.submission.SettingsResponse;

public interface SettingsClientIF {
    SettingsResponse getSettings(long version);
}
