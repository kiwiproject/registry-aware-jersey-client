package org.kiwiproject.jersey.client;

import static com.google.common.base.Preconditions.checkArgument;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotBlank;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.util.Duration;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import org.kiwiproject.registry.model.Port.PortType;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Service definition used for client connections to services. It identifies the name of a a service, the preferred
 * and minimum versions, timeouts, and connector type. The connector type is defined by {@link PortType PortType} and
 * assumes services have only those types of ports.
 * <p>
 * You can use the {@code withServiceName(String)} and {@code withConnector(PortType)} methods to make a copy of an
 * instance but using the given service name or connector, respectively. These are is useful in situations where you
 * have a {@link ServiceIdentifier} instance, for example having the default {@link PortType#APPLICATION APPLICATION}
 * connector, but you need the {@link PortType#ADMIN ADMIN} connector perhaps to check service status or health. Or in
 * situations where you have the same versions and timeouts, but need an instance with a different service name.
 */
@Value
public class ServiceIdentifier {

    @With
    String serviceName;

    String preferredVersion;
    String minimumVersion;

    @With
    PortType connector;

    Duration connectTimeout;
    Duration readTimeout;

    @JsonCreator
    @Builder(toBuilder = true)
    private ServiceIdentifier(@JsonProperty("serviceName") String serviceName,
                              @JsonProperty("preferredVersion") String preferredVersion,
                              @JsonProperty("minimumVersion") String minimumVersion,
                              @JsonProperty("connector") PortType connector,
                              @JsonProperty("connectTimeout") Duration connectTimeout,
                              @JsonProperty("readTimeout") Duration readTimeout) {

        checkArgumentNotBlank(serviceName, "Service name is required");

        this.serviceName = serviceName;
        this.preferredVersion = preferredVersion;
        this.minimumVersion = minimumVersion;
        this.connector = Optional.ofNullable(connector).orElse(PortType.APPLICATION);
        this.connectTimeout = Optional.ofNullable(connectTimeout)
                .orElse(RegistryAwareClientConstants.DEFAULT_CONNECT_TIMEOUT);
        this.readTimeout = Optional.ofNullable(readTimeout)
                .orElse(RegistryAwareClientConstants.DEFAULT_READ_TIMEOUT);

        checkTimeout("connect", this.connectTimeout);
        checkTimeout("read", this.readTimeout);
    }

    private static void checkTimeout(String name, Duration timeout) {
        checkArgumentNotNull(timeout, "%s timeout must not be null", name);
        var millis = timeout.toMilliseconds();
        checkArgument(millis <= Integer.MAX_VALUE,
                "%s timeout must be convertible to an int but %s is more than Integer.MAX_VALUE." +
                        " See Jersey API docs for CONNECT_TIMEOUT and READ_TIMEOUT in ClientProperties",
                name, millis);
    }

    /**
     * Shortcut factory method to create a new {@link ServiceIdentifier} with the given service name with application
     * connector type ({@link PortType#APPLICATION APPLICATION}), no minimum or preferred versions, and default timeout
     * values.
     *
     * @param serviceName the service name
     * @return a new instance
     */
    public static ServiceIdentifier of(String serviceName) {
        return ServiceIdentifier.builder().serviceName(serviceName).build();
    }

    /**
     * Shortcut factory method create a new {@link ServiceIdentifier} with the given service name and connector. The
     * new instance will have no minimum or preferred versions and default timeout values.
     *
     * @param serviceName the service name
     * @param connector   the type of connector, i.e. application or admin
     * @return a new instance
     */
    public static ServiceIdentifier of(String serviceName, PortType connector) {
        return ServiceIdentifier.builder()
                .serviceName(serviceName)
                .connector(connector)
                .build();
    }

    /**
     * Creates a copy of {@code original} without modifications.
     *
     * @param original the original {@link ServiceIdentifier} to copy
     * @return a new copied {@link ServiceIdentifier}
     */
    public static ServiceIdentifier copyOf(ServiceIdentifier original) {
        return original.toBuilder().build();
    }

    /**
     * Convert the Dropwizard connect timeout to millis as an int.
     * <p>
     * This is useful when setting read timeout on a <a href="https://eclipse-ee4j.github.io/jersey/">Jersey</a>
     * {@code Client}, because even though the JAX-RS APIs now accept
     * long values for timeouts, if you specify a value greater than the maximum int, then an {@link ArithmeticException}
     * will be thrown because it uses {@link Math#toIntExact(long)} to convert the value to an int.
     *
     * @return the connect timeout in millis
     * @see org.glassfish.jersey.client.ClientProperties#CONNECT_TIMEOUT
     * @see org.glassfish.jersey.client.JerseyClientBuilder#connectTimeout(long, TimeUnit)
     */
    public int getConnectTimeoutAsIntMillis() {
        return durationToIntMillis(connectTimeout);
    }

    /**
     * Convert the Dropwizard read timeout to millis as an int.
     * <p>
     * This is useful when setting read timeout on a <a href="https://eclipse-ee4j.github.io/jersey/">Jersey</a>
     * {@code Client}, because even though the JAX-RS APIs now accept
     * long values for timeouts, if you specify a value greater than the maximum int, then an {@link ArithmeticException}
     * will be thrown because it uses {@link Math#toIntExact(long)} to convert the value to an int.
     *
     * @return the read timeout in millis
     * @see org.glassfish.jersey.client.ClientProperties#READ_TIMEOUT
     * @see org.glassfish.jersey.client.JerseyClientBuilder#readTimeout(long, TimeUnit)
     */
    public int getReadTimeoutAsIntMillis() {
        return durationToIntMillis(readTimeout);
    }

    private static int durationToIntMillis(Duration duration) {
        return Math.toIntExact(duration.toMilliseconds());
    }
}
