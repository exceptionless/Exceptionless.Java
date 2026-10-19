package com.exceptionless.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import com.exceptionless.ExceptionlessClient;
import com.exceptionless.TestClients;
import com.exceptionless.TestClients.CapturingSubmissionClient;
import com.exceptionless.internal.ErrorConverter;
import com.exceptionless.models.Event;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DuplicateCheckerPluginTest {
  private final CapturingSubmissionClient submission = new CapturingSubmissionClient();
  private final ExceptionlessClient client =
      new ExceptionlessClient(TestClients.config().submissionClient(submission));
  private final AtomicLong now = new AtomicLong(1_000_000);
  private final DuplicateCheckerPlugin plugin =
      new DuplicateCheckerPlugin(Duration.ofSeconds(30), now::get);

  @AfterEach
  void close() {
    client.close();
  }

  private static Event error(Throwable t) {
    Event event = new Event();
    event.setType("error");
    event.setError(ErrorConverter.convert(t, n -> false, List.of()));
    return event;
  }

  /** Errors created at the same call site, so they have identical stacks. */
  private static List<Event> sameErrors(int count) {
    List<Event> events = new java.util.ArrayList<>();
    for (int i = 0; i < count; i++) {
      events.add(error(new RuntimeException("duplicate")));
    }
    return events;
  }

  @Test
  void countsDuplicatesWithinTheWindowAndSendsOneMergedEvent() {
    plugin.startup(client);
    List<Event> events = sameErrors(5);
    assertThat(PluginTestSupport.run(plugin, client, events.get(0)).isCancelled()).isFalse();
    for (Event duplicate : events.subList(1, 5)) {
      assertThat(PluginTestSupport.run(plugin, client, duplicate).isCancelled()).isTrue();
    }
    plugin.flush();
    client.flush(Duration.ofSeconds(5));
    assertThat(submission.events())
        .singleElement()
        .satisfies(e -> assertThat(e.get("count")).isEqualTo(4L));
  }

  @Test
  void differentErrorsAreNotDuplicates() {
    plugin.startup(client);
    assertThat(
            PluginTestSupport.run(plugin, client, error(new RuntimeException("a"))).isCancelled())
        .isFalse();
    assertThat(
            PluginTestSupport.run(plugin, client, error(new RuntimeException("b"))).isCancelled())
        .isFalse();
    assertThat(
            PluginTestSupport.run(plugin, client, error(new IllegalStateException("a")))
                .isCancelled())
        .isFalse();
  }

  @Test
  void sameErrorAfterTheWindowIsSentAgain() {
    plugin.startup(client);
    List<Event> events = sameErrors(2);
    PluginTestSupport.run(plugin, client, events.get(0));
    now.addAndGet(31_000);
    assertThat(PluginTestSupport.run(plugin, client, events.get(1)).isCancelled()).isFalse();
  }

  @Test
  void sameMessageFromDifferentCodeIsNotADuplicate() {
    Event a = error(new RuntimeException("duplicate"));
    Event b = error(new RuntimeException("duplicate"));
    assertThat(DuplicateCheckerPlugin.hash(a)).isNotEqualTo(DuplicateCheckerPlugin.hash(b));
  }

  @Test
  void ignoresNonErrors() {
    for (int i = 0; i < 2; i++) {
      Event log = new Event();
      log.setType("log");
      log.setMessage("x");
      assertThat(PluginTestSupport.run(plugin, client, log).isCancelled()).isFalse();
    }
  }

  @Test
  void suspendFlushesMergedEvents() {
    plugin.startup(client);
    for (Event event : sameErrors(2)) {
      PluginTestSupport.run(plugin, client, event);
    }
    plugin.suspend(client);
    client.flush(Duration.ofSeconds(5));
    assertThat(submission.events()).hasSize(1);
  }

  @Test
  void hashIgnoresDateButUsesStack() {
    List<Event> events = sameErrors(2);
    Event a = events.get(0);
    a.setDate(java.time.OffsetDateTime.now());
    Event b = events.get(1);
    b.setDate(java.time.OffsetDateTime.now().minusDays(1));
    assertThat(DuplicateCheckerPlugin.hash(a)).isEqualTo(DuplicateCheckerPlugin.hash(b));
  }
}
