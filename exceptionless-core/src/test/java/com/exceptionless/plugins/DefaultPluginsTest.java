package com.exceptionless.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import com.exceptionless.ExceptionlessClient;
import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.TestClients;
import com.exceptionless.json.JsonWriter;
import com.exceptionless.models.Event;
import com.exceptionless.models.RequestInfo;
import com.exceptionless.models.UserInfo;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DefaultPluginsTest {
  private final ExceptionlessConfiguration config = TestClients.config();
  private final ExceptionlessClient client = PluginTestSupport.client(config);

  @AfterEach
  void close() {
    client.close();
  }

  @Test
  void defaultPluginsRunInPriorityOrder() {
    List<String> names = config.getPlugins().stream().map(EventPlugin::getName).toList();
    assertThat(names)
        .containsExactly(
            "ConfigurationDefaultsPlugin",
            "ErrorPlugin",
            "ReferenceIdPlugin",
            "EventExclusionPlugin",
            "EnvironmentInfoPlugin",
            "RequestInfoPlugin",
            "IgnoreUserAgentPlugin",
            "VersionPlugin",
            "SubmissionMethodPlugin",
            "CancelSessionsWithNoUserPlugin",
            "DuplicateCheckerPlugin");
  }

  @Test
  void customPluginsCanBeAddedReplacedAndRemoved() {
    config.addPlugin("first", -1, ctx -> {});
    assertThat(config.getPlugins().get(0).getName()).isEqualTo("first");
    config.addPlugin("first", 5000, ctx -> {});
    assertThat(config.getPlugins().get(config.getPlugins().size() - 1).getName())
        .isEqualTo("first");
    config.removePlugin("first").removePlugin(DuplicateCheckerPlugin.class);
    assertThat(config.hasPlugin("first")).isFalse();
    assertThat(config.hasPlugin("DuplicateCheckerPlugin")).isFalse();
  }

  @Test
  void configurationDefaultsAddTagsAndDataWithoutOverwriting() {
    config
        .addDefaultTags("backend", " ")
        .addDefaultData("tenant", "acme")
        .addDefaultData("password", "x")
        .addDefaultData("existing", "default");
    config.addDataExclusions("password");
    Event event = new Event();
    event.getData().put("existing", "mine");
    PluginTestSupport.run(new ConfigurationDefaultsPlugin(), client, event);
    assertThat(event.getTags()).containsExactly("backend");
    assertThat(event.getData())
        .containsEntry("tenant", "acme")
        .containsEntry("existing", "mine")
        .doesNotContainKey("password");
  }

  @Test
  void configurationDefaultsAddTheEnvironmentWithoutOverwritingIt() {
    config.environment(" Production ");
    Event event = new Event();
    PluginTestSupport.run(new ConfigurationDefaultsPlugin(), client, event);
    assertThat(event.getEnvironment()).isEqualTo("Production");

    Event own = new Event();
    own.setEnvironment("qa");
    PluginTestSupport.run(new ConfigurationDefaultsPlugin(), client, own);
    assertThat(own.getEnvironment()).isEqualTo("qa");
  }

  @Test
  void configurationDefaultsKeepAnEnvironmentThatWasClearedOnPurpose() {
    config.environment("production");
    Event event = new Event();
    PluginTestSupport.run(
        new ConfigurationDefaultsPlugin(),
        client,
        event,
        new ContextData().setEnvironmentOverride(true));
    assertThat(event.getEnvironment()).isNull();
  }

  @Test
  void configurationDefaultsNormalizeObjects() {
    config.addDefaultData("info", Map.of("nested", List.of(1, 2)));
    config.userIdentity("user-1", "Ann");
    Event event = new Event();
    PluginTestSupport.run(new ConfigurationDefaultsPlugin(), client, event);
    assertThat(event.getData().get("info")).isEqualTo(Map.of("nested", List.of(1, 2)));
    assertThat(event.getUserInfo()).isEqualTo(new UserInfo("user-1", "Ann"));
  }

  @Test
  void errorPluginReportsEachExceptionOnce() {
    ErrorPlugin plugin = new ErrorPlugin();
    RuntimeException error = new RuntimeException("once");
    Event first = new Event();
    EventPluginContext ctx =
        PluginTestSupport.run(plugin, client, first, new ContextData().setException(error));
    assertThat(ctx.isCancelled()).isFalse();
    assertThat(first.getType()).isEqualTo("error");
    assertThat(first.getError().getMessage()).isEqualTo("once");
    assertThat(
            PluginTestSupport.run(
                    plugin, client, new Event(), new ContextData().setException(error))
                .isCancelled())
        .isTrue();
    assertThat(
            PluginTestSupport.run(
                    plugin,
                    client,
                    new Event(),
                    new ContextData().setException(new RuntimeException("once")))
                .isCancelled())
        .isFalse();
  }

  @Test
  void errorPluginTreatsWrappersAndCausesOfReportedExceptionsAsDuplicates() {
    ErrorPlugin plugin = new ErrorPlugin();
    RuntimeException cause = new RuntimeException("root");
    assertThat(
            PluginTestSupport.run(
                    plugin, client, new Event(), new ContextData().setException(cause))
                .isCancelled())
        .isFalse();
    RuntimeException wrapper = new RuntimeException("wrapped", cause);
    assertThat(
            PluginTestSupport.run(
                    plugin, client, new Event(), new ContextData().setException(wrapper))
                .isCancelled())
        .isTrue();

    RuntimeException inner = new RuntimeException("inner");
    RuntimeException outer = new RuntimeException("outer", inner);
    assertThat(
            PluginTestSupport.run(
                    plugin, client, new Event(), new ContextData().setException(outer))
                .isCancelled())
        .isFalse();
    assertThat(
            PluginTestSupport.run(
                    plugin, client, new Event(), new ContextData().setException(inner))
                .isCancelled())
        .isTrue();
  }

  @Test
  void errorPluginAttachesModulesWhenEnabled() {
    config.includeModules(true);
    Event event = new Event();
    PluginTestSupport.run(
        new ErrorPlugin(), client, event, new ContextData().setException(new RuntimeException()));
    assertThat(event.getError().getModules()).isNotEmpty();
    assertThat(event.getError().getModules())
        .anySatisfy(m -> assertThat(m.name()).contains("junit"));
  }

  @Test
  void referenceIdsOnlyForErrors() {
    ReferenceIdPlugin plugin = new ReferenceIdPlugin();
    Event error = event("error");
    PluginTestSupport.run(plugin, client, error);
    assertThat(error.getReferenceId()).hasSize(16);
    Event log = event("log");
    PluginTestSupport.run(plugin, client, log);
    assertThat(log.getReferenceId()).isNull();
    Event explicit = event("error");
    explicit.setReferenceId("my-own-id");
    PluginTestSupport.run(plugin, client, explicit);
    assertThat(explicit.getReferenceId()).isEqualTo("my-own-id");
    config.useReferenceIds(false);
    Event disabled = event("error");
    PluginTestSupport.run(plugin, client, disabled);
    assertThat(disabled.getReferenceId()).isNull();
  }

  @Test
  void environmentInfoRespectsPrivacy() {
    Event event = new Event();
    PluginTestSupport.run(new EnvironmentInfoPlugin(), client, event);
    assertThat(event.getEnvironmentInfo().getProcessorCount()).isPositive();
    assertThat(event.getEnvironmentInfo().getRuntimeVersion()).startsWith("Java ");
    assertThat(event.getEnvironmentInfo().getOsName()).isNotBlank();
    assertThat(event.getEnvironmentInfo().getInstallId()).isEqualTo(client.getInstallId());
    assertThat(event.getEnvironmentInfo().getMachineName()).isNotBlank();

    config.includePrivateInformation(false);
    Event privateEvent = new Event();
    PluginTestSupport.run(new EnvironmentInfoPlugin(), client, privateEvent);
    assertThat(privateEvent.getEnvironmentInfo().getMachineName()).isNull();
    assertThat(privateEvent.getEnvironmentInfo().getIpAddress()).isNull();
    assertThat(JsonWriter.toJson(privateEvent.getEnvironmentInfo()))
        .contains("\"o_s_name\"")
        .doesNotContain("machine_name");
  }

  private static Event event(String type) {
    Event event = new Event();
    event.setType(type);
    return event;
  }

  private static Event withRequest(RequestInfo request) {
    Event event = new Event();
    event.setRequestInfo(request);
    return event;
  }

  private static Event withUserAgent(String userAgent) {
    RequestInfo request = new RequestInfo();
    request.setUserAgent(userAgent);
    return withRequest(request);
  }

  private static RequestInfo request() {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put("Authorization", List.of("Bearer secret"));
    headers.put("cookie", List.of("a=b"));
    headers.put("Accept", List.of("text/html", "application/json"));
    headers.put("X-Password-Hint", List.of("x"));
    headers.put("X-Long", List.of("y".repeat(5000)));
    Map<String, String> cookies = new LinkedHashMap<>();
    cookies.put("session", "abc");
    cookies.put("password", "x");
    Map<String, String> query = new LinkedHashMap<>();
    query.put("q", "search");
    query.put("passwordReset", "1");
    RequestInfo request = new RequestInfo();
    request.setHttpMethod("POST");
    request.setPath("/orders");
    request.setClientIpAddress("10.0.0.1");
    request.setUserAgent("Mozilla/5.0");
    request.setHeaders(headers);
    request.setCookies(cookies);
    request.setQueryString(query);
    request.setPostData(new LinkedHashMap<>(Map.of("name", "Ann", "password", "x")));
    return request;
  }

  @Test
  void requestInfoIsSanitized() {
    config.addDataExclusions("*password*");
    Event event = new Event();
    PluginTestSupport.run(
        new RequestInfoPlugin(), client, event, new ContextData().setRequestInfo(request()));
    RequestInfo request = event.getRequestInfo();
    assertThat(request.getHeaders()).containsOnlyKeys("Accept", "X-Long");
    assertThat(request.getHeaders().get("Accept")).containsExactly("text/html", "application/json");
    assertThat(request.getHeaders().get("X-Long").get(0))
        .hasSize(RequestInfoPlugin.MAX_VALUE_LENGTH);
    assertThat(request.getCookies()).containsOnlyKeys("session");
    assertThat(request.getQueryString()).containsOnlyKeys("q");
    assertThat(request.getPostData()).isEqualTo(Map.of("name", "Ann"));
    assertThat(request.getClientIpAddress()).isEqualTo("10.0.0.1");
    assertThat(JsonWriter.toJson(request))
        .contains("\"http_method\":\"POST\"")
        .contains("\"client_ip_address\"")
        .contains("\"query_string\"")
        .contains("\"headers\":{\"Accept\":[\"text/html\",\"application/json\"]");
  }

  @Test
  void requestInfoRespectsPrivacySwitches() {
    config.includePrivateInformation(false);
    Event event = withRequest(request());
    PluginTestSupport.run(new RequestInfoPlugin(), client, event);
    RequestInfo request = event.getRequestInfo();
    assertThat(request.getHeaders()).isNull();
    assertThat(request.getCookies()).isNull();
    assertThat(request.getQueryString()).isNull();
    assertThat(request.getPostData()).isNull();
    assertThat(request.getClientIpAddress()).isNull();
    assertThat(request.getPath()).isEqualTo("/orders");
  }

  @Test
  void serverCanTurnOffPrivateInformation() {
    config.applyServerSettings(Map.of("@@IncludePrivateInformation", "false"));
    assertThat(config.isIncludePrivateInformation()).isFalse();
    assertThat(config.isIncludeCookies()).isFalse();
    Event event = withRequest(request());
    PluginTestSupport.run(new RequestInfoPlugin(), client, event);
    assertThat(event.getRequestInfo().getCookies()).isNull();
  }

  @Test
  void botsAreIgnored() {
    config.addUserAgentBotPatterns("*bot*");
    config.applyServerSettings(Map.of("@@UserAgentBotPatterns", "*crawler*"));
    IgnoreUserAgentPlugin plugin = new IgnoreUserAgentPlugin();
    assertThat(PluginTestSupport.run(plugin, client, withUserAgent("Googlebot/2.1")).isCancelled())
        .isTrue();
    assertThat(PluginTestSupport.run(plugin, client, withUserAgent("SomeCrawler")).isCancelled())
        .isTrue();
    assertThat(PluginTestSupport.run(plugin, client, withUserAgent("Mozilla/5.0")).isCancelled())
        .isFalse();
    assertThat(PluginTestSupport.run(plugin, client, new Event()).isCancelled()).isFalse();
  }

  @Test
  void versionComesFromConfiguration() {
    config.version("1.2.3");
    Event event = new Event();
    PluginTestSupport.run(new VersionPlugin(), client, event);
    assertThat(event.getVersion()).isEqualTo("1.2.3");
    Event explicit = new Event();
    explicit.setVersion("9.9");
    PluginTestSupport.run(new VersionPlugin(), client, explicit);
    assertThat(explicit.getVersion()).isEqualTo("9.9");
  }

  @Test
  void submissionMethodComesFromContext() {
    Event event = new Event();
    PluginTestSupport.run(
        new SubmissionMethodPlugin(),
        client,
        event,
        new ContextData().setSubmissionMethod("servletFilter"));
    assertThat(event.getSubmissionMethod()).isEqualTo("servletFilter");
  }

  @Test
  void sessionsWithoutUserAreCancelled() {
    CancelSessionsWithNoUserPlugin plugin = new CancelSessionsWithNoUserPlugin();
    assertThat(PluginTestSupport.run(plugin, client, event("session")).isCancelled()).isTrue();
    Event withUser = event("session");
    withUser.setUserInfo(new UserInfo("u1"));
    assertThat(PluginTestSupport.run(plugin, client, withUser).isCancelled()).isFalse();
    Event withSessionId = event("session");
    withSessionId.setReferenceId("0123456789abcdef");
    assertThat(PluginTestSupport.run(plugin, client, withSessionId).isCancelled()).isFalse();
    assertThat(PluginTestSupport.run(plugin, client, event("log")).isCancelled()).isFalse();
  }

  @Test
  void sessionIdManagement() {
    SessionIdManagementPlugin plugin = new SessionIdManagementPlugin();
    Event start = event("session");
    PluginTestSupport.run(plugin, client, start);
    String sessionId = start.getReferenceId();
    assertThat(sessionId).matches("[0-9a-f]{32}");
    assertThat(config.getCurrentSessionIdentifier()).isEqualTo(sessionId);

    Event log = event("log");
    PluginTestSupport.run(plugin, client, log);
    assertThat(log.getSessionId()).isEqualTo(sessionId);
    assertThat(log.getData()).containsEntry("@ref:session", sessionId);

    Event end = event("sessionend");
    PluginTestSupport.run(plugin, client, end);
    assertThat(end.getReferenceId()).isEqualTo(sessionId);
    assertThat(config.getCurrentSessionIdentifier()).isNull();
  }
}
