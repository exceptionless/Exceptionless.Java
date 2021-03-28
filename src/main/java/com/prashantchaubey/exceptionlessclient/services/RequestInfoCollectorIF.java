package com.prashantchaubey.exceptionlessclient.services;

import com.prashantchaubey.exceptionlessclient.models.services.RequestInfo;

import java.net.http.HttpRequest;

public interface RequestInfoCollectorIF {
  RequestInfo getRequestInfo(HttpRequest request, RequestInfoGetArgs args);
}
