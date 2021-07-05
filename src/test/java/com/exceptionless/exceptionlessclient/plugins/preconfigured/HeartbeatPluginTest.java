package com.exceptionless.exceptionlessclient.plugins.preconfigured;

import com.exceptionless.exceptionlessclient.TestFixtures;
import com.exceptionless.exceptionlessclient.configuration.Configuration;
import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.submission.DefaultSubmissionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class HeartbeatPluginTest {
  @Mock private DefaultSubmissionClient submissionClient;
  private Configuration configuration;
  private HeartbeatPlugin plugin;

  @BeforeEach
  public void setup() {
    plugin = HeartbeatPlugin.builder().heartbeatIntervalInSecs(1).build();
    configuration =
        TestFixtures.aDefaultConfigurationManager().submissionClient(submissionClient).build();
  }

  @Test
  public void itShouldNotSubmitHeartbeatForNoUserIdentity() throws InterruptedException {
    plugin.run(EventPluginContext.from(Event.builder().build()), configuration);

    Thread.sleep(1500);
    verifyZeroInteractions(submissionClient);
  }

  @Test
  public void itShouldSendHeartBeatForAUserIdentity() throws InterruptedException {
    plugin.run(
        EventPluginContext.from(Event.builder().userIdentity("test-identity").build()),
            configuration);

    Thread.sleep(1500);
    verify(submissionClient, times(1)).sendHeartBeat("test-identity", false);
  }

  @Test
  public void itShouldResetHeartBeatTimerForANewUserIdentity() throws InterruptedException {
    plugin.run(
        EventPluginContext.from(Event.builder().userIdentity("test-identity").build()),
            configuration);
    Thread.sleep(1500);
    plugin.run(
        EventPluginContext.from(Event.builder().userIdentity("test-identity2").build()),
            configuration);
    Thread.sleep(1500);

    verify(submissionClient, times(1)).sendHeartBeat("test-identity", false);
    verify(submissionClient, times(1)).sendHeartBeat("test-identity2", false);
  }
}
