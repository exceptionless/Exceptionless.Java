package com.exceptionless.exceptionlessclient.plugins.preconfigured;

import com.exceptionless.exceptionlessclient.configuration.ConfigurationManager;
import com.exceptionless.exceptionlessclient.logging.LogIF;
import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.models.services.RequestInfo;
import com.exceptionless.exceptionlessclient.plugins.EventPluginIF;
import com.exceptionless.exceptionlessclient.services.RequestInfoGetArgs;
import com.exceptionless.exceptionlessclient.utils.Utils;
import lombok.Builder;

import java.net.http.HttpRequest;

public class RequestInfoPlugin implements EventPluginIF {
  private LogIF log;

  @Builder
  public RequestInfoPlugin(LogIF log) {
    this.log = log;
  }

  @Override
  public int getPriority() {
    return 70;
  }

  @Override
  public void run(
          EventPluginContext eventPluginContext, ConfigurationManager configurationManager) {
    Event event = eventPluginContext.getEvent();
    if (event.getRequestInfo().isPresent()) {
      return;
    }
    HttpRequest request = eventPluginContext.getContext().getRequest();
    if (request == null) {
      return;
    }
    RequestInfo requestInfo =
        configurationManager
            .getRequestInfoCollector()
            .getRequestInfo(
                request,
                RequestInfoGetArgs.builder()
                    .exclusions(configurationManager.getDataExclusions())
                    .includeCookies(
                        configurationManager.getPrivateInformationInclusions().getCookies())
                    .includeIpAddress(
                        configurationManager.getPrivateInformationInclusions().getIpAddress())
                    .includePostData(
                        configurationManager.getPrivateInformationInclusions().getPostData())
                    .includeQueryString(
                        configurationManager.getPrivateInformationInclusions().getQueryString())
                    .build());

    if (configurationManager.getUserAgentBotPatterns().stream()
        .anyMatch(pattern -> Utils.match(requestInfo.getUserAgent(), pattern))) {
      log.info("Cancelling event as the request user agent matches a known bot pattern");
      eventPluginContext.getContext().setEventCancelled(true);
      return;
    }

    event.addRequestInfo(requestInfo);
  }
}
