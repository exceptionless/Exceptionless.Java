package com.exceptionless.exceptionlessclient.models.services;

import com.prashantchaubey.exceptionlessclient.models.base.Model;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

@SuperBuilder
@Value
@NonFinal
@EqualsAndHashCode(callSuper = true)
public class RequestInfo extends Model {
  private String userAgent;
  private String httpMethod;
  private boolean isSecure;
  private String host;
  private int port;
  private String path;
  private String referrer;
  private String clientIpAddress;
  private Map<String, String> cookies;
  private Object postData;
  private Map<String, List<String>> queryString;
}
