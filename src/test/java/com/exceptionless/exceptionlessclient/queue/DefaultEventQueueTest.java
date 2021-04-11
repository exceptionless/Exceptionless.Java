package com.exceptionless.exceptionlessclient.queue;

import com.exceptionless.exceptionlessclient.TestFixtures;
import com.exceptionless.exceptionlessclient.configuration.Configuration;
import com.exceptionless.exceptionlessclient.exceptions.ClientException;
import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.submission.SubmissionResponse;
import com.exceptionless.exceptionlessclient.storage.InMemoryStorage;
import com.exceptionless.exceptionlessclient.storage.InMemoryStorageProvider;
import com.exceptionless.exceptionlessclient.submission.DefaultSubmissionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DefaultEventQueueTest {
  @Mock private InMemoryStorageProvider storageProvider;
  @Mock private DefaultSubmissionClient submissionClient;
  @Mock private BiConsumer<List<Event>, SubmissionResponse> testHandler;
  private DefaultEventQueue queue;
  private InMemoryStorage<Event> storage;
  private Event event;

  @BeforeEach
  public void setup() {
    Configuration configuration =
        TestFixtures.aDefaultConfiguration().submissionBatchSize(1).build();

    storage = InMemoryStorage.<Event>builder().build();
    doReturn(storage).when(storageProvider).getQueue();

    event = Event.builder().build();
    queue =
        DefaultEventQueue.builder()
            .storageProvider(storageProvider)
            .submissionClient(submissionClient)
            .configuration(configuration)
            .processingIntervalInSecs(
                3600) // We don't want the automatic timer to run in between tests by default
            .build();
  }

  @Test
  public void itCanEnqueueSuccessfully() {
    queue.enqueue(event);

    assertThat(storage.peek().getValue()).isEqualTo(event);
  }

  @Test
  public void itCanProcessABatchSuccessfully() {
    storage.save(event);

    SubmissionResponse response =
        SubmissionResponse.builder().message("test-message").statusCode(200).build();
    doReturn(response).when(submissionClient).postEvents(List.of(event), false);

    queue.onEventsPosted(testHandler);
    queue.process(false);

    assertThat(storage.peek()).isNull();
    verify(testHandler, times(1)).accept(List.of(event), response);
  }

  @Test
  public void itShouldNotProcessIfCurrentlyProcessingEvents()
      throws ExecutionException, InterruptedException {
    storage.save(event);
    storage.save(event);

    SubmissionResponse response =
        SubmissionResponse.builder().message("test-message").statusCode(200).build();
    doAnswer(
            invocationOnMock -> {
              Thread.sleep(1000);
              return response;
            })
        .when(submissionClient)
        .postEvents(List.of(event), false);

    queue.onEventsPosted(testHandler);
    Future<?> future = Executors.newSingleThreadExecutor().submit(() -> queue.process(false));
    queue.process(false);
    future.get();

    assertThat(storage.get(2)).hasSize(1); // Only one is processed
    verify(testHandler, times(1)).accept(List.of(event), response);
  }

  @Test
  public void itShouldNotPostEmptyEvents() {
    queue.onEventsPosted(testHandler);
    queue.process(false);

    verifyZeroInteractions(submissionClient);
    verifyZeroInteractions(testHandler);
  }

  @Test
  public void itShouldSuspendProcessingOnClientException() {
    storage.save(event);

    doThrow(new ClientException("test")).when(submissionClient).postEvents(List.of(event), false);

    queue.onEventsPosted(testHandler);
    queue.process(false);

    assertThat(queue.isProcessingCurrentlySuspended()).isTrue();
    assertThat(storage.peek().getValue()).isEqualTo(event);
    verifyZeroInteractions(testHandler);
  }

  @Test
  public void itShouldSuspendProcessingIfServiceIsUnavailable() {
    storage.save(event);

    SubmissionResponse response =
        SubmissionResponse.builder().message("test-message").statusCode(503).build();
    doReturn(response).when(submissionClient).postEvents(List.of(event), false);

    queue.onEventsPosted(testHandler);
    queue.process(false);

    assertThat(queue.isProcessingCurrentlySuspended()).isTrue();
    assertThat(storage.peek().getValue()).isEqualTo(event);
    verify(testHandler, times(1)).accept(List.of(event), response);
  }

  @Test
  public void itShouldSuspendAndDiscardProcessingAndClearQueueIfNoPayment() {
    storage.save(event);

    SubmissionResponse response =
        SubmissionResponse.builder().message("test-message").statusCode(402).build();
    doReturn(response).when(submissionClient).postEvents(List.of(event), false);

    queue.onEventsPosted(testHandler);
    queue.process(false);

    assertThat(queue.isProcessingCurrentlySuspended()).isTrue();
    assertThat(storage.peek()).isNull(); // queue is cleared
    verify(testHandler, times(1)).accept(List.of(event), response);

    queue.enqueue(event);

    assertThat(storage.peek()).isNull(); // discarding events
  }

  @Test
  public void itShouldSuspendProcessingAndClearQueueIfUnableToAuthenticate() {
    storage.save(event);

    SubmissionResponse response =
        SubmissionResponse.builder().message("test-message").statusCode(401).build();
    doReturn(response).when(submissionClient).postEvents(List.of(event), false);

    queue.onEventsPosted(testHandler);
    queue.process(false);

    assertThat(queue.isProcessingCurrentlySuspended()).isTrue();
    assertThat(storage.peek()).isNull(); // queue is cleared
    verify(testHandler, times(1)).accept(List.of(event), response);
  }

  @Test
  public void itShouldSuspendProcessingAndClearQueueForNotFoundResponse() {
    storage.save(event);

    SubmissionResponse response =
        SubmissionResponse.builder().message("test-message").statusCode(404).build();
    doReturn(response).when(submissionClient).postEvents(List.of(event), false);

    queue.onEventsPosted(testHandler);
    queue.process(false);

    assertThat(queue.isProcessingCurrentlySuspended()).isTrue();
    assertThat(storage.peek()).isNull(); // queue is cleared
    verify(testHandler, times(1)).accept(List.of(event), response);
  }

  @Test
  public void itShouldSuspendProcessingAndClearQueueForBadRequest() {
    storage.save(event);

    SubmissionResponse response =
        SubmissionResponse.builder().message("test-message").statusCode(400).build();
    doReturn(response).when(submissionClient).postEvents(List.of(event), false);

    queue.onEventsPosted(testHandler);
    queue.process(false);

    assertThat(queue.isProcessingCurrentlySuspended()).isTrue();
    assertThat(storage.peek()).isNull(); // queue is cleared
    verify(testHandler, times(1)).accept(List.of(event), response);
  }

  @Test
  public void itShouldSuspendProcessingByDefault() {
    storage.save(event);

    SubmissionResponse response =
        SubmissionResponse.builder().message("test-message").statusCode(-1).build();
    doReturn(response).when(submissionClient).postEvents(List.of(event), false);

    queue.onEventsPosted(testHandler);
    queue.process(false);

    assertThat(queue.isProcessingCurrentlySuspended()).isTrue();
    assertThat(storage.peek().getValue()).isEqualTo(event);
    verify(testHandler, times(1)).accept(List.of(event), response);
  }

  @Test
  public void itShouldReduceSubmissionBatchSizeIfRequestEntitiesAreTooLarge() {
    Configuration configuration =
        TestFixtures.aDefaultConfiguration().submissionBatchSize(3).build();
    queue =
        DefaultEventQueue.builder()
            .storageProvider(storageProvider)
            .submissionClient(submissionClient)
            .configuration(configuration)
            .processingIntervalInSecs(
                3600) // We don't want the automatic timer to run in between tests by default
            .build();
    for (int i = 0; i < 10; i++) {
      storage.save(event);
    }

    SubmissionResponse response =
        SubmissionResponse.builder().message("test-message").statusCode(413).build();
    doReturn(response).when(submissionClient).postEvents(anyList(), anyBoolean());

    queue.onEventsPosted(testHandler);
    queue.process(false);

    assertThat(storage.get(10)).hasSize(10);
    verify(testHandler, times(1)).accept(anyList(), eq(response));

    queue.process(false);

    // One invocation with full batch
    verify(submissionClient, times(1))
        .postEvents(argThat(argument -> argument.size() == 3), anyBoolean());
    // One invocation with reduced batch
    verify(submissionClient, times(1))
        .postEvents(argThat(argument -> argument.size() == 2), anyBoolean());
  }

  @Test
  public void itShouldDiscardEventsIfItCantReduceSubmissionSizeAndRequestEntitiesAreTooLarge() {
    for (int i = 0; i < 10; i++) {
      storage.save(event);
    }

    SubmissionResponse response =
        SubmissionResponse.builder().message("test-message").statusCode(413).build();
    doReturn(response).when(submissionClient).postEvents(anyList(), anyBoolean());

    queue.onEventsPosted(testHandler);
    queue.process(false);

    assertThat(storage.get(10)).hasSize(9);
    verify(testHandler, times(1)).accept(anyList(), eq(response));
  }

  @Test
  public void itShouldResetSubmissionBatchSizeOnNextSuccessfulResponse() {
    Configuration configuration =
        TestFixtures.aDefaultConfiguration().submissionBatchSize(3).build();
    queue =
        DefaultEventQueue.builder()
            .storageProvider(storageProvider)
            .submissionClient(submissionClient)
            .configuration(configuration)
            .processingIntervalInSecs(
                3600) // We don't want the automatic timer to run in between tests by default
            .build();
    for (int i = 0; i < 10; i++) {
      storage.save(event);
    }

    doReturn(SubmissionResponse.builder().message("test-message").statusCode(413).build())
        .when(submissionClient)
        .postEvents(anyList(), anyBoolean());
    queue.process(false);

    doReturn(SubmissionResponse.builder().message("test-message").statusCode(200).build())
        .when(submissionClient)
        .postEvents(anyList(), anyBoolean());
    queue.process(false);
    queue.process(false);

    // Two invocations with full batch; First with the default size and next after a successful
    // response
    verify(submissionClient, times(2))
        .postEvents(argThat(argument -> argument.size() == 3), anyBoolean());
    // One invocation with reduced batch
    verify(submissionClient, times(1))
        .postEvents(argThat(argument -> argument.size() == 2), anyBoolean());
  }

  @Test
  public void itShouldProcessEventsUsingTimer() throws InterruptedException {
    storage.save(event);
    SubmissionResponse response =
        SubmissionResponse.builder().message("test-message").statusCode(200).build();
    doReturn(response).when(submissionClient).postEvents(List.of(event), false);

    Configuration configuration =
        TestFixtures.aDefaultConfiguration().submissionBatchSize(1).build();
    queue =
        DefaultEventQueue.builder()
            .storageProvider(storageProvider)
            .submissionClient(submissionClient)
            .configuration(configuration)
            .processingIntervalInSecs(1)
            .build();
    queue.onEventsPosted(testHandler);

    // Need more time due to synchronization
    Thread.sleep(2000);
    assertThat(storage.peek()).isNull();
    verify(testHandler, times(1)).accept(List.of(event), response);
  }
}
