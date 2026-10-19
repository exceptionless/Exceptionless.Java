package com.exceptionless.e2e.support;

import com.exceptionless.json.JsonWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A minimal client for the Exceptionless REST API (src/Exceptionless.Web/Api/Endpoints in the
 * server repository), used to bootstrap accounts and read back what the server stored.
 */
public final class ServerApi {
  private final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          .version(HttpClient.Version.HTTP_1_1)
          .build();
  private final String baseUrl;
  private String userToken;

  public ServerApi(String baseUrl) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }

  public String baseUrl() {
    return baseUrl;
  }

  public String userToken() {
    return userToken;
  }

  // -------------------------------------------------------------------------------------------
  // Bootstrap

  /** POST /api/v2/auth/signup (Signup: name, email, password). Falls back to login if taken. */
  public String signup(String name, String email, String password) {
    Map<String, Object> body = Map.of("name", name, "email", email, "password", password);
    Response response = send("POST", "/api/v2/auth/signup", body, null);
    if (response.status() != 200) {
      response =
          send("POST", "/api/v2/auth/login", Map.of("email", email, "password", password), null);
    }
    response.expect(200);
    userToken = (String) Json.object(response.json()).get("token");
    return userToken;
  }

  /** POST /api/v2/organizations (NewOrganization: name). */
  public String createOrganization(String name) {
    return (String)
        Json.object(post("/api/v2/organizations", Map.of("name", name)).expect(201).json())
            .get("id");
  }

  /** POST /api/v2/projects (NewProject: organization_id, name). */
  public String createProject(String organizationId, String name) {
    Map<String, Object> body =
        Map.of("organization_id", organizationId, "name", name, "delete_bot_data_enabled", false);
    return (String) Json.object(post("/api/v2/projects", body).expect(201).json()).get("id");
  }

  /** POST /api/v2/projects/{id}/tokens (NewToken) with the client scope: a project API key. */
  public String createClientToken(String projectId) {
    Map<String, Object> body = Map.of("scopes", List.of("client"), "notes", "e2e tests");
    return (String)
        Json.object(post("/api/v2/projects/" + projectId + "/tokens", body).expect(201).json())
            .get("id");
  }

  /** POST /api/v2/projects/{id}/config?key= with a ValueFromBody&lt;string&gt; body. */
  public void setProjectConfig(String projectId, String key, String value) {
    post("/api/v2/projects/" + projectId + "/config?key=" + encode(key), Map.of("value", value))
        .expect(200);
  }

  /** DELETE /api/v2/projects/{id}/config?key= */
  public void deleteProjectConfig(String projectId, String key) {
    send("DELETE", "/api/v2/projects/" + projectId + "/config?key=" + encode(key), null, userToken)
        .expect(200);
  }

  /** GET /api/v2/projects/{id}/config: {version, settings}. */
  public Map<String, Object> getProjectConfig(String projectId) {
    return Json.object(get("/api/v2/projects/" + projectId + "/config").expect(200).json());
  }

  /**
   * POST /api/v2/projects/{id}/data?key= (project data is loaded into the event pipeline context).
   */
  public void setProjectData(String projectId, String key, String value) {
    post("/api/v2/projects/" + projectId + "/data?key=" + encode(key), Map.of("value", value))
        .expect(200);
  }

  // -------------------------------------------------------------------------------------------
  // Queries

  /** GET /api/v2/projects/{projectId}/events?filter=&limit= (full persistent events). */
  public List<Object> getEvents(String projectId, String filter, int limit) {
    String path =
        "/api/v2/projects/" + projectId + "/events?limit=" + limit + "&filter=" + encode(filter);
    return Json.array(get(path).expect(200).json());
  }

  /** GET /api/v2/projects/{projectId}/events/count?filter= */
  public long countEvents(String projectId, String filter) {
    String path = "/api/v2/projects/" + projectId + "/events/count?filter=" + encode(filter);
    return ((Number) Json.object(get(path).expect(200).json()).get("total")).longValue();
  }

  /** GET /api/v2/events/{id} */
  public Map<String, Object> getEvent(String id) {
    return Json.object(get("/api/v2/events/" + id).expect(200).json());
  }

  /** GET /api/v2/events/by-ref/{referenceId}: the events with that reference id. */
  public List<Object> getEventsByReference(String referenceId) {
    Response response = get("/api/v2/events/by-ref/" + encode(referenceId));
    if (response.status() == 404) {
      return List.of();
    }
    return Json.array(response.expect(200).json());
  }

  /** GET /api/v2/stacks/{id} */
  public Map<String, Object> getStack(String id) {
    return Json.object(get("/api/v2/stacks/" + id).expect(200).json());
  }

  /** GET /api/v2/projects/{projectId}/events/sessions?filter= (session start events). */
  public List<Object> getSessions(String projectId, String filter) {
    String path =
        "/api/v2/projects/" + projectId + "/events/sessions?limit=50&filter=" + encode(filter);
    return Json.array(get(path).expect(200).json());
  }

  public Optional<Map<String, Object>> findEvent(String projectId, String filter) {
    List<Object> events = getEvents(projectId, filter, 1);
    return events.isEmpty() ? Optional.empty() : Optional.of(Json.object(events.get(0)));
  }

  // -------------------------------------------------------------------------------------------
  // HTTP

  public Response get(String path) {
    return send("GET", path, null, userToken);
  }

  public Response post(String path, Object body) {
    return send("POST", path, body, userToken);
  }

  /** Sends a request with an arbitrary bearer token (null for anonymous). */
  public Response send(String method, String path, Object body, String token) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(60))
            .header("Accept", "application/json");
    if (token != null) {
      builder.header("Authorization", "Bearer " + token);
    }
    if (body != null) {
      String json = body instanceof String s ? s : JsonWriter.toJson(body);
      builder
          .header("Content-Type", "application/json")
          .method(method, HttpRequest.BodyPublishers.ofString(json));
    } else {
      builder.method(method, HttpRequest.BodyPublishers.noBody());
    }
    try {
      HttpResponse<String> response =
          http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      Map<String, String> headers = new LinkedHashMap<>();
      response
          .headers()
          .map()
          .forEach((name, values) -> headers.put(name.toLowerCase(), String.join(",", values)));
      return new Response(method + " " + path, response.statusCode(), response.body(), headers);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  /** An HTTP response. */
  public record Response(String request, int status, String body, Map<String, String> headers) {
    public Response expect(int expectedStatus) {
      if (status != expectedStatus) {
        throw new AssertionError(
            request + " returned HTTP " + status + " (expected " + expectedStatus + "): " + body);
      }
      return this;
    }

    public Object json() {
      return body == null || body.isBlank() ? null : Json.parse(body);
    }
  }
}
