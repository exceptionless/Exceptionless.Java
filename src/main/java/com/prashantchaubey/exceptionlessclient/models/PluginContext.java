package com.prashantchaubey.exceptionlessclient.models;

import com.prashantchaubey.exceptionlessclient.models.base.Model;
import com.prashantchaubey.exceptionlessclient.models.enums.PluginContextKey;
import com.prashantchaubey.exceptionlessclient.models.services.RequestInfo;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;
import java.util.Map;

@SuperBuilder
@Getter
public class PluginContext extends Model {
  private boolean eventCancelled;

  public static PluginContextBuilderImpl builder() {
    return new PluginContextBuilderImpl();
  }

  public void addRequestInfo(RequestInfo requestInfo) {
    data.put(PluginContextKey.REQUEST_INFO.value(), requestInfo);
  }

  public boolean hasException() {
    return data.get(PluginContextKey.EXCEPTION.value()) != null;
  }

  public Exception getException() {
    return (Exception) data.get(PluginContextKey.EXCEPTION.value());
  }

  public boolean isUnhandledError() {
    return data.containsKey(PluginContextKey.IS_UNHANDLED_ERROR.value())
        && (boolean) data.get(PluginContextKey.IS_UNHANDLED_ERROR.value());
  }

  public String getSubmissionMethod() {
    return (String) data.get(PluginContextKey.SUBMISSION_METHOD.value());
  }

  public static final class PluginContextBuilderImpl
      extends PluginContextBuilder<PluginContext, PluginContextBuilderImpl> {
    // lombok builder create private fields in the builder class so we can't access `data` from
    // `Model` even though it is `protected`. So we will use this object as an proxy.
    private Map<String, Object> data = new HashMap<>();

    public PluginContextBuilderImpl exception(Exception exception) {
      data.put(PluginContextKey.EXCEPTION.value(), exception);
      return super.data(data);
    }

    public PluginContextBuilderImpl markAsUnhandledError() {
      data.put(PluginContextKey.IS_UNHANDLED_ERROR.value(), true);
      return super.data(data);
    }

    public PluginContextBuilderImpl submissionMethod(String method) {
      data.put(PluginContextKey.SUBMISSION_METHOD.value(), method);
      return super.data(data);
    }

    @Override
    public PluginContextBuilderImpl data(Map<String, Object> data) {
      this.data = data;
      return super.data(data);
    }

    public PluginContext build() {
      return new PluginContext(this);
    }
  }
}
