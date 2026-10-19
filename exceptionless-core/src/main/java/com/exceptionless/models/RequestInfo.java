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

  public void setUserAgent(@Nullable String userAgent) {
    this.userAgent = userAgent;
  }

  public @Nullable String getHttpMethod() {
    return httpMethod;
  }

  public void setHttpMethod(@Nullable String httpMethod) {
    this.httpMethod = httpMethod;
  }

  /** True when the request used HTTPS. */
  public @Nullable Boolean getSecure() {
    return secure;
  }

  public void setSecure(@Nullable Boolean secure) {
    this.secure = secure;
  }

  public @Nullable String getHost() {
    return host;
  }

  public void setHost(@Nullable String host) {
    this.host = host;
  }

  public @Nullable Integer getPort() {
    return port;
  }

  public void setPort(@Nullable Integer port) {
    this.port = port;
  }

  public @Nullable String getPath() {
    return path;
  }

  public void setPath(@Nullable String path) {
    this.path = path;
  }

  public @Nullable String getReferrer() {
    return referrer;
  }

  public void setReferrer(@Nullable String referrer) {
    this.referrer = referrer;
  }

  public @Nullable String getClientIpAddress() {
    return clientIpAddress;
  }

  public void setClientIpAddress(@Nullable String clientIpAddress) {
    this.clientIpAddress = clientIpAddress;
  }

  /** Request headers. Each header maps to all of its values. */
  public @Nullable Map<String, List<String>> getHeaders() {
    return headers;
  }

  public void setHeaders(@Nullable Map<String, List<String>> headers) {
    this.headers = headers;
  }

  public @Nullable Map<String, String> getCookies() {
    return cookies;
  }

  public void setCookies(@Nullable Map<String, String> cookies) {
    this.cookies = cookies;
  }

  /** Form fields (a map) or the raw request body (a string). */
  public @Nullable Object getPostData() {
    return postData;
  }

  public void setPostData(@Nullable Object postData) {
    this.postData = postData;
  }

  public @Nullable Map<String, String> getQueryString() {
    return queryString;
  }

  public void setQueryString(@Nullable Map<String, String> queryString) {
    this.queryString = queryString;
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
