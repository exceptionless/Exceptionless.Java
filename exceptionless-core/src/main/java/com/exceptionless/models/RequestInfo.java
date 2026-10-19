package com.exceptionless.models;

import com.exceptionless.json.JsonSerializable;
import com.exceptionless.json.JsonWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** The HTTP request an event happened during ({@code @request}). */
public final class RequestInfo implements JsonSerializable {
  private @Nullable String userAgent;
  private @Nullable String httpMethod;
  private @Nullable Boolean secure;
  private @Nullable String host;
  private @Nullable Integer port;
  private @Nullable String path;
  private @Nullable String referrer;
  private @Nullable String clientIpAddress;
  private @Nullable Map<String, List<String>> headers;
  private @Nullable Map<String, String> cookies;
  private @Nullable Object postData;
  private @Nullable Map<String, String> queryString;
  private final Map<String, Object> data = new LinkedHashMap<>();

  public @Nullable String getUserAgent() {
    return userAgent;
  }

  public RequestInfo setUserAgent(@Nullable String userAgent) {
    this.userAgent = userAgent;
    return this;
  }

  public @Nullable String getHttpMethod() {
    return httpMethod;
  }

  public RequestInfo setHttpMethod(@Nullable String httpMethod) {
    this.httpMethod = httpMethod;
    return this;
  }

  /** True when the request used HTTPS. */
  public @Nullable Boolean getSecure() {
    return secure;
  }

  public RequestInfo setSecure(@Nullable Boolean secure) {
    this.secure = secure;
    return this;
  }

  public @Nullable String getHost() {
    return host;
  }

  public RequestInfo setHost(@Nullable String host) {
    this.host = host;
    return this;
  }

  public @Nullable Integer getPort() {
    return port;
  }

  public RequestInfo setPort(@Nullable Integer port) {
    this.port = port;
    return this;
  }

  public @Nullable String getPath() {
    return path;
  }

  public RequestInfo setPath(@Nullable String path) {
    this.path = path;
    return this;
  }

  public @Nullable String getReferrer() {
    return referrer;
  }

  public RequestInfo setReferrer(@Nullable String referrer) {
    this.referrer = referrer;
    return this;
  }

  public @Nullable String getClientIpAddress() {
    return clientIpAddress;
  }

  public RequestInfo setClientIpAddress(@Nullable String clientIpAddress) {
    this.clientIpAddress = clientIpAddress;
    return this;
  }

  /** Request headers. Each header maps to all of its values. */
  public @Nullable Map<String, List<String>> getHeaders() {
    return headers;
  }

  public RequestInfo setHeaders(@Nullable Map<String, List<String>> headers) {
    this.headers = headers;
    return this;
  }

  public @Nullable Map<String, String> getCookies() {
    return cookies;
  }

  public RequestInfo setCookies(@Nullable Map<String, String> cookies) {
    this.cookies = cookies;
    return this;
  }

  /** Form fields (a map) or the raw request body (a string). */
  public @Nullable Object getPostData() {
    return postData;
  }

  public RequestInfo setPostData(@Nullable Object postData) {
    this.postData = postData;
    return this;
  }

  public @Nullable Map<String, String> getQueryString() {
    return queryString;
  }

  public RequestInfo setQueryString(@Nullable Map<String, String> queryString) {
    this.queryString = queryString;
    return this;
  }

  public Map<String, Object> getData() {
    return data;
  }

  @Override
  public void toJson(JsonWriter writer) {
    writer.beginObject();
    writer.fieldIfNotEmpty("user_agent", userAgent);
    writer.fieldIfNotEmpty("http_method", httpMethod);
    writer.fieldIfNotEmpty("is_secure", secure);
    writer.fieldIfNotEmpty("host", host);
    writer.fieldIfNotEmpty("port", port);
    writer.fieldIfNotEmpty("path", path);
    writer.fieldIfNotEmpty("referrer", referrer);
    writer.fieldIfNotEmpty("client_ip_address", clientIpAddress);
    writer.fieldIfNotEmpty("headers", headers);
    writer.fieldIfNotEmpty("cookies", cookies);
    writer.fieldIfNotEmpty("post_data", postData);
    writer.fieldIfNotEmpty("query_string", queryString);
    writer.fieldIfNotEmpty("data", data);
    writer.endObject();
  }
}
