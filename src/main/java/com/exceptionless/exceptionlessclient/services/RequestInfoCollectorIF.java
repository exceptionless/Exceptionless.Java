package com.exceptionless.exceptionlessclient.services;

import com.exceptionless.exceptionlessclient.models.services.RequestInfo;
import com.prashantchaubey.exceptionlessclient.models.services.RequestInfo;

import java.net.http.HttpRequest;

public interface RequestInfoCollectorIF {
  RequestInfo getRequestInfo(HttpRequest request, RequestInfoGetArgs args);
}
