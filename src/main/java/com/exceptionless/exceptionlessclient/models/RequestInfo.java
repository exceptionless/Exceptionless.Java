package com.exceptionless.exceptionlessclient.models;

import com.exceptionless.exceptionlessclient.models.base.Model;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuperBuilder
@Value
@EqualsAndHashCode(callSuper = true)
public class RequestInfo extends Model {
  String userAgent;
  String httpMethod;
  Boolean secure;
  String host;
  Integer port;
  String path;
  String referrer;
  String clientIpAddress;
  @Builder.Default Map<String, String> cookies = new HashMap<>();
  Object postData;
  @Builder.Default Map<String, List<String>> queryString = new HashMap<>();
  @Builder.Default Map<String, List<String>> headers = new HashMap<>();

  public Boolean isSecure() {
    return secure;
  }
}
