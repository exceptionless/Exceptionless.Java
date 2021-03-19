package com.prashantchaubey.exceptionlessclient.submission;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Data
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class SubmissionRequest {
    private String apiKey;
    private String userAgent;
    private String method;
    private String url;
    private String data;
}
