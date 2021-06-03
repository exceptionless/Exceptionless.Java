package com.exceptionless.exceptionlessclient.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.beans.PropertyChangeListener;
import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class PrivateInformationInclusionsTest {
  @Mock private PropertyChangeListener listener;

  @Test
  public void itCanSendChangesToListeners() {
    PrivateInformationInclusions privateInformationInclusions =
        PrivateInformationInclusions.builder().build();
    privateInformationInclusions.addPropertyChangeListener(listener);

    privateInformationInclusions.applyToAll(true);
    privateInformationInclusions.setQueryString(false);
    privateInformationInclusions.setPostData(false);
    privateInformationInclusions.setCookies(false);
    privateInformationInclusions.setIpAddress(false);
    privateInformationInclusions.setMachineName(false);
    privateInformationInclusions.setUserName(false);

    List<String> properties =
        List.of(
            "all", "queryString", "postData", "cookies", "ipAddress", "machineName", "userName");
    List<Boolean> oldValues = List.of(false, true, true, true, true, true, true);
    List<Boolean> newValues = List.of(true, false, false, false, false, false, false);

    properties.forEach(
        property ->
            verify(listener, times(1))
                .propertyChange(
                    argThat(
                        event ->
                            event.getPropertyName().equals(property)
                                && event
                                    .getOldValue()
                                    .equals(oldValues.get(properties.indexOf(property)))
                                && event
                                    .getNewValue()
                                    .equals(newValues.get(properties.indexOf(property))))));
  }
}
