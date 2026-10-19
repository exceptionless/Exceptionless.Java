package com.exceptionless.e2e;

import static com.exceptionless.e2e.support.Json.array;
import static com.exceptionless.e2e.support.Json.arrayAt;
import static com.exceptionless.e2e.support.Json.at;
import static com.exceptionless.e2e.support.Json.object;
import static com.exceptionless.e2e.support.Json.objectAt;
import static org.assertj.core.api.Assertions.assertThat;

import com.exceptionless.ExceptionlessClient;
import com.exceptionless.LogLevel;
import com.exceptionless.e2e.support.Await;
import com.exceptionless.e2e.support.ExceptionlessServer;
import com.exceptionless.e2e.support.JsonDiff;
import com.exceptionless.e2e.support.TestProject;
import com.exceptionless.e2e.support.TestProject.TestClient;
import com.exceptionless.models.RequestInfo;
import com.exceptionless.submission.SubmissionResponse;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Submits one of each kind of event with the Java client and checks what the real server stored.
 */
@Tag("e2e")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventWireFormatE2ETest {
  private static final int BATCH_SIZE = 300;
  private static final String CHROME_UA =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.110 Safari/537.36";

  private final String run = "e2e-" + UUID.randomUUID().toString().substring(0, 8);
  private TestProject project;
  private TestClient testClient;
  private String errorReferenceId;
  private SubmissionResponse userDescriptionResponse;

  @BeforeAll
  void submitEvents() throws Exception {
    project = ExceptionlessServer.get().createProject("Wire format");
    testClient = project.newClient(c -> c.version("1.2.3").addDefaultTags("java-e2e"));
    ExceptionlessClient client = testClient.client();

    // 1. Error with a cause chain, a suppressed exception and custom exception properties.
    OrderProcessingException error = Orders.process("ord-42");
    error.addSuppressed(new IllegalArgumentException("cleanup failed"));
    errorReferenceId =
        client
            .createException(error)
            .addTags(run, "error")
            .markAsCritical()
            .setProperty("order", Map.of("id", "ord-42", "total", 99.95))
            .submit()
            .getEvent()
            .getReferenceId();

    // 2. Log with level, source, tags and custom properties of several shapes.
    Map<String, Object> nested = new LinkedHashMap<>();
    nested.put("enabled", true);
    nested.put("limits", Map.of("daily", 5, "monthly", 100));
    client
        .createLog("com.example.checkout.CheckoutService", "Payment authorized", LogLevel.INFO)
        .addTags(run, "log", "payments")
        .setProperty(
            "customer", new Customer("Jane Doe", "jane@example.com", new Address("Dallas", "TX")))
        .setProperty("settings", nested)
        .setProperty("items", List.of("sku-1", "sku-2", "sku-3"))
        .setProperty("amount", new BigDecimal("12.50"))
        .setProperty("attempt", 3)
        .setValue(12.5)
        .submit();

    // 3. Feature usage.
    client.createFeatureUsage("checkout.express").addTags(run, "feature").submit();

    // 4. 404 with full request info.
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put("Accept", List.of("text/html", "application/xhtml+xml"));
    headers.put("X-Request-Id", List.of("req-123"));
    headers.put("Authorization", List.of("Bearer secret")); // removed by the client
    RequestInfo request = new RequestInfo();
    request.setHttpMethod("GET");
    request.setSecure(true);
    request.setHost("shop.example.com");
    request.setPort(443);
    request.setPath("/products/missing");
    request.setReferrer("https://www.example.com/search");
    request.setClientIpAddress("203.0.113.10");
    request.setUserAgent(CHROME_UA);
    request.setHeaders(headers);
    request.setCookies(Map.of("theme", "dark", "visitor", "v-1"));
    request.setQueryString(Map.of("q", "shoes", "page", "2"));
    client
        .createNotFound("/products/missing")
        .setRequestInfo(request)
        .addTags(run, "notfound")
        .submit();

    // 5. User identity and user description.
    client
        .createLog("UserFeedback", "Feedback submitted", LogLevel.WARN)
        .setUserIdentity("user-123", "Jane Doe")
        .setUserDescription("jane@example.com", "The checkout page froze")
        .addTags(run, "user")
        .submit();

    // 6. Manual stacking: two different messages, one stack.
    for (int i = 1; i <= 2; i++) {
      client
          .createLog("Billing", "Invoice " + i + " failed to send", LogLevel.ERROR)
          .setManualStackingKey("invoice-delivery-" + run, "Invoice delivery failures")
          .addTags(run, "manual", "manual-" + i)
          .submit();
    }

    // 7. A large batch, compressed and split by the client.
    for (int i = 0; i < BATCH_SIZE; i++) {
      client
          .createLog("Batch", "Batch event " + i + " " + "x".repeat(200), LogLevel.DEBUG)
          .addTags(run, "batch")
          .setProperty("index", i)
          .submit();
    }

    testClient.flush();

    // 8. Attach a user description to the error by its reference id.
    userDescriptionResponse =
        client
            .updateUserEmailAndDescription(
                errorReferenceId, "support@example.com", "It broke after I paid")
            .get();
    testClient.savePayloads("wire-format");
  }

  @AfterAll
  void close() {
    if (testClient != null) {
      testClient.close();
    }
  }

  @Test
  void clientRequestsWereAccepted() {
    assertThat(testClient.recorder().calls("events"))
        .isNotEmpty()
        .allSatisfy(call -> assertThat(call.status()).as(call.message()).isEqualTo(202));
    assertThat(userDescriptionResponse.statusCode())
        .as(userDescriptionResponse.message())
        .isEqualTo(202);
  }

  @Test
  void errorRoundTripsWithoutLosingFields() {
    Map<String, Object> stored = project.awaitEvent("tag:" + run + " tag:error");
    Map<String, Object> sent = testClient.sentEventWithTag("error");
    assertThat(JsonDiff.diff(sent, stored)).as("fields the server dropped or changed").isEmpty();
  }

  @Test
  void errorHasStackTraceCauseChainAndModules() {
    Map<String, Object> event = project.awaitEvent("tag:" + run + " tag:error");
    assertThat(event).containsEntry("type", "error");
    assertThat(event.get("message")).isEqualTo("Unable to process order ord-42");
    assertThat(array(event.get("tags"))).contains(run, "error", "java-e2e", "Critical");
    assertThat(event.get("reference_id")).isEqualTo(errorReferenceId);

    Map<String, Object> error = objectAt(event, "data", "@error");
    assertThat(error).containsEntry("type", OrderProcessingException.class.getName());
    assertThat(error).containsEntry("message", "Unable to process order ord-42");

    Map<String, Object> frame = object(arrayAt(error, "stack_trace").get(0));
    assertThat(frame)
        .containsEntry("declaring_namespace", "com.exceptionless.e2e")
        .containsEntry("declaring_type", "EventWireFormatE2ETest$Orders")
        .containsEntry("name", "process")
        .containsEntry("file_name", "EventWireFormatE2ETest.java")
        .containsKey("line_number");
    assertThat(((Number) frame.get("line_number")).intValue()).isPositive();

    // Custom exception properties.
    Map<String, Object> ext = objectAt(error, "data", "@ext");
    assertThat(ext).containsEntry("orderId", "ord-42").containsEntry("attempts", 3L);

    // Cause chain.
    Map<String, Object> inner = objectAt(error, "inner");
    assertThat(inner)
        .containsEntry("type", "java.lang.IllegalStateException")
        .containsEntry("message", "Inventory unavailable for ord-42");
    assertThat(object(arrayAt(inner, "stack_trace").get(0))).containsEntry("name", "reserve");
    Map<String, Object> innermost = objectAt(inner, "inner");
    assertThat(innermost).containsEntry("type", InventoryException.class.getName());

    // Modules (jars on the class path) are attached to the outermost error.
    List<Object> modules = arrayAt(error, "modules");
    assertThat(modules).isNotEmpty();
    assertThat(modules)
        .anySatisfy(m -> assertThat(object(m)).containsKeys("module_id", "name", "version"));
    assertThat(modules)
        .anySatisfy(m -> assertThat(String.valueOf(object(m).get("name"))).contains("junit"));

    assertThat(event.get("data"))
        .satisfies(d -> assertThat(object(d)).containsEntry("@version", "1.2.3"));
    assertThat(objectAt(event, "data", "order")).containsEntry("id", "ord-42");
  }

  @Test
  void errorIsStackedOnTheInnermostUserFrame() {
    Map<String, Object> event = project.awaitEvent("tag:" + run + " tag:error");
    Map<String, Object> stack = project.api().getStack((String) event.get("stack_id"));
    Map<String, Object> signature = objectAt(stack, "signature_info");
    // The server walks the cause chain from the innermost error and takes its first "user" frame.
    assertThat(signature).containsEntry("ExceptionType", InventoryException.class.getName());
    assertThat(String.valueOf(signature.get("Method")))
        .startsWith("com.exceptionless.e2e.EventWireFormatE2ETest$Inventory.check(");
    assertThat(stack.get("title")).isEqualTo("Unable to process order ord-42");

    // The server flags the frame it used.
    Map<String, Object> innermost = objectAt(event, "data", "@error", "inner", "inner");
    assertThat(object(arrayAt(innermost, "stack_trace").get(0)))
        .containsEntry("is_signature_target", true);
  }

  @Test
  void environmentInfoIsStored() {
    Map<String, Object> event = project.awaitEvent("tag:" + run + " tag:error");
    Map<String, Object> environment = objectAt(event, "data", "@environment");
    assertThat(environment)
        .containsKeys(
            "processor_count",
            "total_physical_memory",
            "available_physical_memory",
            "process_name",
            "process_id",
            "process_memory_size",
            "thread_name",
            "thread_id",
            "architecture",
            "o_s_name",
            "o_s_version",
            "machine_name",
            "install_id",
            "runtime_version",
            "ip_address");
    assertThat(environment.get("o_s_name")).isEqualTo(System.getProperty("os.name"));
    assertThat(((Number) environment.get("processor_count")).intValue())
        .isEqualTo(Runtime.getRuntime().availableProcessors());
    assertThat(String.valueOf(environment.get("runtime_version")))
        .contains(System.getProperty("java.version"));
    assertThat(environment.get("install_id")).isEqualTo(testClient.client().getInstallId());
    assertThat(environment.get("process_id"))
        .isEqualTo(String.valueOf(ProcessHandle.current().pid()));
  }

  @Test
  void submissionClientIsRecognized() {
    Map<String, Object> event = project.awaitEvent("tag:" + run + " tag:error");
    Map<String, Object> submissionClient = objectAt(event, "data", "@submission_client");
    assertThat(submissionClient).containsEntry("user_agent", "exceptionless-java");
    assertThat(String.valueOf(submissionClient.get("version")))
        .matches("\\d+\\.\\d+(\\.\\d+){0,2}");
  }

  @Test
  void referenceIdLookupAndUserDescription() {
    List<Object> byRef =
        Await.value(
            "event by reference id",
            Await.EVENTS,
            () -> project.api().getEventsByReference(errorReferenceId),
            l -> !l.isEmpty());
    assertThat(object(byRef.get(0))).containsEntry("reference_id", errorReferenceId);

    // The user description is applied by a background job once the event exists.
    Map<String, Object> event =
        Await.value(
            "the user description on the error",
            Await.EVENTS,
            () -> project.api().getEvent((String) object(byRef.get(0)).get("id")),
            e -> at(e, "data", "@user_description") != null);
    assertThat(objectAt(event, "data", "@user_description"))
        .containsEntry("email_address", "support@example.com")
        .containsEntry("description", "It broke after I paid");
  }

  @Test
  void logRoundTripsWithLevelSourceTagsAndProperties() {
    Map<String, Object> stored = project.awaitEvent("tag:" + run + " tag:log");
    assertThat(JsonDiff.diff(testClient.sentEventWithTag("log"), stored))
        .as("fields the server dropped or changed")
        .isEmpty();

    assertThat(stored)
        .containsEntry("type", "log")
        .containsEntry("source", "com.example.checkout.CheckoutService")
        .containsEntry("message", "Payment authorized");
    assertThat(new BigDecimal(String.valueOf(stored.get("value")))).isEqualByComparingTo("12.5");
    assertThat(array(stored.get("tags"))).contains(run, "log", "payments", "java-e2e");
    Map<String, Object> data = object(stored.get("data"));
    assertThat(data).containsEntry("@level", "info");
    assertThat(objectAt(data, "customer")).containsEntry("name", "Jane Doe");
    assertThat(objectAt(data, "customer", "address")).containsEntry("city", "Dallas");
    assertThat(objectAt(data, "settings", "limits")).containsEntry("daily", 5L);
    assertThat(arrayAt(data, "items")).containsExactly("sku-1", "sku-2", "sku-3");
    assertThat(data).containsEntry("attempt", 3L);
  }

  @Test
  void featureUsageIsStored() {
    Map<String, Object> stored = project.awaitEvent("tag:" + run + " tag:feature");
    assertThat(JsonDiff.diff(testClient.sentEventWithTag("feature"), stored)).isEmpty();
    assertThat(stored).containsEntry("type", "usage").containsEntry("source", "checkout.express");
  }

  @Test
  void notFoundRequestInfoIsStored() {
    Map<String, Object> stored = project.awaitEvent("tag:" + run + " tag:notfound");
    Map<String, Object> sent = testClient.sentEventWithTag("notfound");
    // The server drops @environment from 404s (NotFoundPlugin); compare the rest.
    Map<String, Object> sentWithoutEnvironment = new LinkedHashMap<>(sent);
    Map<String, Object> sentData = new LinkedHashMap<>(object(sent.get("data")));
    sentData.remove("@environment");
    sentWithoutEnvironment.put("data", sentData);
    assertThat(JsonDiff.diff(sentWithoutEnvironment, stored))
        .as("fields the server dropped or changed")
        .isEmpty();

    assertThat(stored).containsEntry("type", "404").containsEntry("source", "/products/missing");
    Map<String, Object> request = objectAt(stored, "data", "@request");
    assertThat(request)
        .containsEntry("http_method", "GET")
        .containsEntry("is_secure", true)
        .containsEntry("host", "shop.example.com")
        .containsEntry("port", 443L)
        .containsEntry("path", "/products/missing")
        .containsEntry("referrer", "https://www.example.com/search")
        .containsEntry("client_ip_address", "203.0.113.10")
        .containsEntry("user_agent", CHROME_UA);
    assertThat(objectAt(request, "headers"))
        .containsEntry("Accept", List.of("text/html", "application/xhtml+xml"))
        .doesNotContainKey("Authorization");
    assertThat(objectAt(request, "cookies")).containsEntry("theme", "dark");
    assertThat(objectAt(request, "query_string"))
        .containsEntry("q", "shoes")
        .containsEntry("page", "2");
    // Derived by the server from the user agent.
    assertThat(objectAt(request, "data"))
        .containsEntry("@browser", "Chrome")
        .containsEntry("@os", "Windows")
        .containsEntry("@is_bot", false);
  }

  @Test
  void userIdentityAndDescriptionAreStored() {
    Map<String, Object> stored = project.awaitEvent("tag:" + run + " tag:user");
    assertThat(JsonDiff.diff(testClient.sentEventWithTag("user"), stored)).isEmpty();
    assertThat(objectAt(stored, "data", "@user"))
        .containsEntry("identity", "user-123")
        .containsEntry("name", "Jane Doe");
    assertThat(objectAt(stored, "data", "@user_description"))
        .containsEntry("email_address", "jane@example.com")
        .containsEntry("description", "The checkout page froze");
    assertThat(objectAt(stored, "data")).containsEntry("@level", "warn");
  }

  @Test
  void manualStackingKeyGroupsEventsWithTitle() {
    List<Object> events = project.awaitEvents("tag:" + run + " tag:manual", 2);
    assertThat(events).hasSize(2);
    String stackId = (String) object(events.get(0)).get("stack_id");
    assertThat(object(events.get(1)).get("stack_id")).isEqualTo(stackId);
    Map<String, Object> stack = project.api().getStack(stackId);
    assertThat(stack.get("title")).isEqualTo("Invoice delivery failures");
    assertThat(objectAt(stack, "signature_info"))
        .containsEntry("ManualStackingKey", "invoice-delivery-" + run);
    assertThat(objectAt(object(events.get(0)), "data", "@stack"))
        .containsEntry("title", "Invoice delivery failures");
  }

  @Test
  void largeBatchIsAcceptedCompletely() {
    long batchRequests = testClient.recorder().calls("events").size();
    long count =
        Await.value(
            BATCH_SIZE + " batch events",
            Await.EVENTS,
            () -> project.api().countEvents(project.id(), "tag:" + run + " tag:batch"),
            c -> c >= BATCH_SIZE);
    assertThat(count).isEqualTo(BATCH_SIZE);
    // More events than one batch holds, so the client had to split them.
    assertThat(batchRequests).isGreaterThan(1);
  }

  // ---------------------------------------------------------------------------------------------
  // Test fixtures

  static final class Orders {
    static OrderProcessingException process(String orderId) {
      try {
        Inventory.reserve(orderId);
        throw new AssertionError("unreachable");
      } catch (IllegalStateException e) {
        return new OrderProcessingException("Unable to process order " + orderId, e, orderId, 3);
      }
    }
  }

  static final class Inventory {
    static void reserve(String orderId) {
      try {
        check(orderId);
      } catch (InventoryException e) {
        throw new IllegalStateException("Inventory unavailable for " + orderId, e);
      }
    }

    static void check(String orderId) throws InventoryException {
      throw new InventoryException("Out of stock: " + orderId);
    }
  }

  static final class InventoryException extends Exception {
    InventoryException(String message) {
      super(message);
    }
  }

  // Must be public: the client only reads custom properties from public exception classes.
  public static final class OrderProcessingException extends RuntimeException {
    private final String orderId;
    private final int attempts;

    OrderProcessingException(String message, Throwable cause, String orderId, int attempts) {
      super(message, cause);
      this.orderId = orderId;
      this.attempts = attempts;
    }

    public String getOrderId() {
      return orderId;
    }

    public int getAttempts() {
      return attempts;
    }
  }

  public static final class Customer {
    private final String name;
    private final String email;
    private final Address address;

    Customer(String name, String email, Address address) {
      this.name = name;
      this.email = email;
      this.address = address;
    }

    public String getName() {
      return name;
    }

    public String getEmail() {
      return email;
    }

    public Address getAddress() {
      return address;
    }
  }

  public static final class Address {
    private final String city;
    private final String state;

    Address(String city, String state) {
      this.city = city;
      this.state = state;
    }

    public String getCity() {
      return city;
    }

    public String getState() {
      return state;
    }
  }
}
