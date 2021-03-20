package com.prashantchaubey.exceptionlessclient.settings;

import com.prashantchaubey.exceptionlessclient.models.submission.SettingsResponse;

public interface SettingsClientIF {
    SettingsResponse getSettings(long version);
}
