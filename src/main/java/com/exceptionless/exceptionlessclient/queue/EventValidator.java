package com.exceptionless.exceptionlessclient.queue;

import com.exceptionless.exceptionlessclient.exceptions.BadEventDataException;

public final class EventValidator {
  private static final int VALID_IDENTIFIER_MIN_LENGTH = 8;
  private static final int VALID_IDENTIFIER_MAX_LENGTH = 100;

  private EventValidator() {}

  public static void validateIdentifier(String value) {
    if (value.length() < VALID_IDENTIFIER_MIN_LENGTH
        || value.length() > VALID_IDENTIFIER_MAX_LENGTH) {
      throw new BadEventDataException(
          String.format("Value must contain between 8 and 100 characters, found: [%s]", value));
    }

    if (!value.chars().allMatch(ch -> Character.isLetterOrDigit(ch) || ch == '-')) {
      throw new BadEventDataException(
          String.format(
              "Value should contain only alphanumeric characters and hyphen sign, found: [%s]",
              value));
    }
  }

  public static void validateGeo(int latitude, int longitude) {
    if (latitude < -90 || latitude > 90) {
      throw new BadEventDataException(
          String.format("Latitude should be between -90 and 90, found: [%s]", latitude));
    }

    if (longitude < -180 || longitude > 180) {
      throw new BadEventDataException(
          String.format("Longitude should be between -180 and 180, found: [%s]", longitude));
    }
  }
}
