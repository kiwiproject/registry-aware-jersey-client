package org.kiwiproject.jersey.client;

import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotBlank;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.util.Duration;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import org.kiwiproject.registry.model.Port.PortType;

import java.util.Optional;

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
}
