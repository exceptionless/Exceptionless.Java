package com.exceptionless.exceptionlessclient.queue;

import com.exceptionless.exceptionlessclient.exceptions.BadEventDataException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Java6Assertions.assertThatThrownBy;

public class EventValidatorTest {
  @Test
  public void itShouldFailIfIdentifierIsLessThanMinimumLength() {
    assertThatThrownBy(() -> EventValidator.validateIdentifier("123"))
        .isInstanceOf(BadEventDataException.class)
        .hasMessage("Value must contain between 8 and 100 characters, found: [123]");
  }

  @Test
  public void itShouldFailIfIdentifierIsGreaterThanMaximumLength() {
    assertThatThrownBy(
            () ->
                EventValidator.validateIdentifier(
                    "12345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890"))
        .isInstanceOf(BadEventDataException.class)
        .hasMessage(
            "Value must contain between 8 and 100 characters, found: [12345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890]");
  }

  @Test
  public void itShouldFailIfIdentifierContainsUnwantedValues() {
    assertThatThrownBy(() -> EventValidator.validateIdentifier("12345678?!"))
        .isInstanceOf(BadEventDataException.class)
        .hasMessage(
            "Value should contain only alphanumeric characters and hyphen sign, found: [12345678?!]");
  }

  @Test
  public void itShouldNotFailForAValidIdentifier() {
    EventValidator.validateIdentifier("12aA-34bB");
  }

  @Test
  public void itShouldFailForAWrongLatitudeValue() {
    assertThatThrownBy(() -> EventValidator.validateGeo(-92, 100))
        .isInstanceOf(BadEventDataException.class)
        .hasMessage("Latitude should be between -90 and 90, found: [-92]");

    assertThatThrownBy(() -> EventValidator.validateGeo(95, 100))
        .isInstanceOf(BadEventDataException.class)
        .hasMessage("Latitude should be between -90 and 90, found: [95]");
  }

  @Test
  public void itShouldFailForAWrongLongitudeValue() {
    assertThatThrownBy(() -> EventValidator.validateGeo(0, -182))
        .isInstanceOf(BadEventDataException.class)
        .hasMessage("Longitude should be between -180 and 180, found: [-182]");

    assertThatThrownBy(() -> EventValidator.validateGeo(0, 185))
            .isInstanceOf(BadEventDataException.class)
            .hasMessage("Longitude should be between -180 and 180, found: [185]");
  }

  @Test
  public void itShouldNotFailForAValidGeo() {
    EventValidator.validateGeo(88,-178);
  }
}
