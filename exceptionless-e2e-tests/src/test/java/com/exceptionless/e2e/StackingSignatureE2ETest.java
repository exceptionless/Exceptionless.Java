package com.exceptionless.e2e;

import static com.exceptionless.e2e.support.Json.objectAt;
import static org.assertj.core.api.Assertions.assertThat;

import com.exceptionless.e2e.support.ExceptionlessServer;
import com.exceptionless.e2e.support.TestProject;
import com.exceptionless.e2e.support.TestProject.TestClient;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * How the server builds stack signatures for Java errors thrown inside the JDK.
 *
 * <p>The server's {@code ErrorSignature} starts at the innermost error and takes its first "user"
 * frame, where only namespaces starting with {@code System} or {@code Microsoft} are considered
 * non-user code. Java's {@code java.*}/{@code jdk.*} frames therefore count as user code, so an
 * exception thrown by the JDK is stacked on the JDK method, not the application method that called
 * it. These tests document that behaviour (companion server change #1 in issue #91) and the {@code
 * UserNamespaces} project setting that works around it.
 */
@Tag("e2e")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StackingSignatureE2ETest {
  private final String run = "e2e-" + UUID.randomUUID().toString().substring(0, 8);
  private TestProject project;

  @BeforeAll
  void createProject() {
    project = ExceptionlessServer.get().createProject("Stacking");
  }

  @Test
  @Order(1)
  void jdkFramesAreTreatedAsUserCodeByDefault() {
    submit("default-a", Quantities::parseCartQuantity);
    submit("default-b", Quantities::parseStockLevel);

    Map<String, Object> a = project.awaitEvent("tag:" + run + " tag:default-a");
    Map<String, Object> b = project.awaitEvent("tag:" + run + " tag:default-b");
    Map<String, Object> signature =
        objectAt(project.api().getStack((String) a.get("stack_id")), "signature_info");
    System.out.println("[finding] default signature for Integer.parseInt failure: " + signature);

    // The JDK frame that threw is used, so...
    assertThat(signature).containsEntry("ExceptionType", "java.lang.NumberFormatException");
    assertThat(String.valueOf(signature.get("Method")))
        .startsWith("java.lang.NumberFormatException.forInputString(");
    // ...two unrelated call sites end up in the same stack.
    assertThat(b.get("stack_id")).isEqualTo(a.get("stack_id"));
  }

  @Test
  @Order(2)
  void userNamespacesProjectConfigSettingIsIgnored() {
    // This is where the UI's "User Namespaces" project setting is saved (POST
    // /projects/{id}/config)...
    project.api().setProjectConfig(project.id(), "UserNamespaces", "com.exceptionless.e2e");
    submit("config-a", Quantities::parseCartQuantity);

    Map<String, Object> event = project.awaitEvent("tag:" + run + " tag:config-a");
    Map<String, Object> signature =
        objectAt(project.api().getStack((String) event.get("stack_id")), "signature_info");
    System.out.println("[finding] signature with UserNamespaces in project config: " + signature);
    // ...but the event pipeline only reads UserNamespaces from organization/project *data*.
    assertThat(String.valueOf(signature.get("Method")))
        .startsWith("java.lang.NumberFormatException.forInputString(");
  }

  @Test
  @Order(3)
  void userNamespacesProjectDataPicksTheApplicationFrame() {
    project.api().setProjectData(project.id(), "UserNamespaces", "com.exceptionless.e2e");
    submit("data-a", Quantities::parseCartQuantity);
    submit("data-b", Quantities::parseStockLevel);

    Map<String, Object> a = project.awaitEvent("tag:" + run + " tag:data-a");
    Map<String, Object> b = project.awaitEvent("tag:" + run + " tag:data-b");
    Map<String, Object> signature =
        objectAt(project.api().getStack((String) a.get("stack_id")), "signature_info");
    System.out.println("[finding] signature with UserNamespaces in project data: " + signature);
    assertThat(signature).containsEntry("ExceptionType", "java.lang.NumberFormatException");
    assertThat(String.valueOf(signature.get("Method")))
        .startsWith("com.exceptionless.e2e.StackingSignatureE2ETest$Quantities.parseCartQuantity(");
    assertThat(b.get("stack_id")).isNotEqualTo(a.get("stack_id"));
  }

  private void submit(String tag, Runnable failing) {
    // A fresh client each time so the client's duplicate checker can't drop the repeat.
    try (TestClient client = project.newClient(c -> {})) {
      try {
        failing.run();
        throw new AssertionError("expected an exception");
      } catch (NumberFormatException e) {
        client.client().createException(e).addTags(run, tag).submit();
      }
      client.flush();
    }
  }

  static final class Quantities {
    static void parseCartQuantity() {
      Integer.parseInt("two");
    }

    static void parseStockLevel() {
      Integer.parseInt("many");
    }
  }
}
