package com.exceptionless.models;

import static org.assertj.core.api.Assertions.assertThat;

import com.exceptionless.ExceptionlessClient;
import com.exceptionless.TestClients;
import com.exceptionless.TestClients.CapturingSubmissionClient;
import com.exceptionless.TestJson;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Guards the wire format: every property the client writes must exist on the server model
 * (Exceptionless.Core/Models, serialized with snake_case naming). The server silently ignores
 * unknown properties, so a typo here would drop data without any error.
 */
class WireContractTest {
  private static final Set<String> EVENT =
      Set.of(
          "environment",
          "type",
          "source",
          "date",
          "tags",
          "message",
          "geo",
          "value",
          "count",
          "data",
          "reference_id");
  private static final Set<String> ERROR =
      Set.of("message", "type", "code", "data", "inner", "stack_trace", "target_method", "modules");
  private static final Set<String> STACK_FRAME =
      Set.of(
          "file_name",
          "line_number",
          "column",
          "is_signature_target",
          "declaring_namespace",
          "declaring_type",
          "name",
          "module_id",
          "data",
          "generic_arguments",
          "parameters");
  private static final Set<String> MODULE =
      Set.of("module_id", "name", "version", "is_entry", "created_date", "modified_date", "data");
  private static final Set<String> REQUEST =
      Set.of(
          "user_agent",
          "http_method",
          "is_secure",
          "host",
          "port",
          "path",
          "referrer",
          "client_ip_address",
          "headers",
          "cookies",
          "post_data",
          "query_string",
          "data");
  private static final Set<String> ENVIRONMENT =
      Set.of(
          "processor_count",
          "total_physical_memory",
          "available_physical_memory",
          "command_line",
          "process_name",
          "process_id",
          "process_memory_size",
          "thread_name",
          "thread_id",
          "architecture",
          "o_s_name",
          "o_s_version",
          "ip_address",
          "machine_name",
          "install_id",
          "runtime_version",
          "data");
  private static final Set<String> USER = Set.of("identity", "name", "data");
  private static final Set<String> USER_DESCRIPTION =
      Set.of("email_address", "description", "data");
  private static final Set<String> MANUAL_STACKING = Set.of("title", "signature_data");

  @Test
  void everyPropertyExistsOnTheServerModel() {
    CapturingSubmissionClient submission = new CapturingSubmissionClient();
    try (ExceptionlessClient client =
        new ExceptionlessClient(
            TestClients.config().includeModules(true).submissionClient(submission))) {
      Map<String, List<String>> headers = new LinkedHashMap<>();
      headers.put("Accept", List.of("text/html"));
      RequestInfo request = new RequestInfo();
      request.setHttpMethod("GET");
      request.setSecure(true);
      request.setHost("h");
      request.setPort(443);
      request.setPath("/p");
      request.setReferrer("r");
      request.setUserAgent("ua");
      request.setClientIpAddress("1.2.3.4");
      request.setHeaders(headers);
      request.setCookies(Map.of("c", "v"));
      request.setQueryString(Map.of("q", "v"));
      request.setPostData(Map.of("f", "v"));
      client
          .createException(new IllegalStateException("outer", new RuntimeException("inner")))
          .setRequestInfo(request)
          .setUserIdentity("id", "name")
          .setUserDescription("a@b.c", "d")
          .setManualStackingKey("key", "title")
          .setEnvironment("production")
          .setGeo(1, 2)
          .setValue(3)
          .setCount(2)
          .addTags("t")
          .setMessage("m")
          .setSource("s")
          .submit();
      client.flush(Duration.ofSeconds(5));
    }
    Map<String, Object> event = submission.events().get(0);
    assertThat(event.keySet()).isSubsetOf(EVENT);
    Map<String, Object> data = TestJson.object(event.get("data"));

    Map<String, Object> error = TestJson.object(data.get("@error"));
    checkError(error);
    assertThat(TestJson.objects(error.get("modules")))
        .isNotEmpty()
        .allSatisfy(m -> assertThat(m.keySet()).isSubsetOf(MODULE));

    Map<String, Object> request = TestJson.object(data.get("@request"));
    Map<String, Object> environment = TestJson.object(data.get("@environment"));
    assertThat(request.keySet())
        .isSubsetOf(REQUEST)
        .contains("is_secure", "client_ip_address", "query_string", "http_method");
    assertThat(environment.keySet())
        .isSubsetOf(ENVIRONMENT)
        .contains("o_s_name", "o_s_version", "process_id");
    assertThat(TestJson.object(data.get("@user")).keySet()).isSubsetOf(USER);
    assertThat(TestJson.object(data.get("@user_description")).keySet())
        .isSubsetOf(USER_DESCRIPTION);
    assertThat(TestJson.object(data.get("@stack")).keySet()).isSubsetOf(MANUAL_STACKING);

    // Types the server is strict about.
    assertThat(request.get("headers")).isEqualTo(Map.of("Accept", List.of("text/html")));
    assertThat(request.get("cookies")).isEqualTo(Map.of("c", "v"));
    assertThat(request.get("query_string")).isEqualTo(Map.of("q", "v"));
    assertThat(environment.get("process_id")).isInstanceOf(String.class);
    assertThat(environment.get("thread_id")).isInstanceOf(String.class);
    assertThat(event.get("count")).isEqualTo(2L);
    assertThat(event.get("environment")).isEqualTo("production");
    assertThat(event.get("date"))
        .asString()
        .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?([+-]\\d{2}:\\d{2}|Z)");
  }

  private static void checkError(Map<String, Object> error) {
    assertThat(error.keySet()).isSubsetOf(ERROR);
    assertThat(TestJson.objects(error.get("stack_trace")))
        .isNotEmpty()
        .allSatisfy(f -> assertThat(f.keySet()).isSubsetOf(STACK_FRAME));
    if (error.get("inner") != null) {
      checkError(TestJson.object(error.get("inner")));
    }
  }
}
