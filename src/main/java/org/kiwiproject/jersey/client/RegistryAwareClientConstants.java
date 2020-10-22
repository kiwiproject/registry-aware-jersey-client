package org.kiwiproject.jersey.client;

import lombok.experimental.UtilityClass;

import java.time.Duration;

/**
 * Common constants across this library.
 */
@UtilityClass
public class RegistryAwareClientConstants {

    public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 5_000;
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofMillis(DEFAULT_CONNECT_TIMEOUT_MILLIS);

    public static final int DEFAULT_CONNECT_REQUEST_TIMEOUT_MILLIS = 5_000;
    public static final Duration DEFAULT_CONNECT_REQUEST_TIMEOUT = Duration.ofMillis(DEFAULT_CONNECT_REQUEST_TIMEOUT_MILLIS);

    public static final int DEFAULT_SOCKET_TIMEOUT_MILLIS = 5_000;
    public static final Duration DEFAULT_SOCKET_TIMEOUT = Duration.ofMillis(DEFAULT_SOCKET_TIMEOUT_MILLIS);

}
