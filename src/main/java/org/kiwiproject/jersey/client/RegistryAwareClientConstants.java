package org.kiwiproject.jersey.client;

import io.dropwizard.util.Duration;
import lombok.experimental.UtilityClass;

/**
 * Common constants across this library.
 */
@UtilityClass
public class RegistryAwareClientConstants {

    public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 5_000;
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.milliseconds(DEFAULT_CONNECT_TIMEOUT_MILLIS);

    public static final int DEFAULT_READ_TIMEOUT_MILLIS = 5_000;
    public static final Duration DEFAULT_READ_TIMEOUT = Duration.milliseconds(DEFAULT_READ_TIMEOUT_MILLIS);

    public static final int DEFAULT_SOCKET_TIMEOUT_MILLIS = 5_000;
    public static final Duration DEFAULT_SOCKET_TIMEOUT = Duration.milliseconds(DEFAULT_SOCKET_TIMEOUT_MILLIS);

}
