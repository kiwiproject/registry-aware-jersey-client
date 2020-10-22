package org.kiwiproject.jersey.client;

import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotBlank;

import lombok.Builder;
import lombok.Getter;
import lombok.With;

import java.time.Duration;
import java.util.Optional;

/**
 * Service definition used for the client connection
 */
@Getter
public class ServiceIdentifier {
    // TODO: Should we just use service-discovery-client's PortType?
    public enum Connector {
        ADMIN, APPLICATION
    }

    @With
    private final String serviceName;

    private final String preferredVersion;
    private final String minimumVersion;
    private final Connector connector;
    private final Duration connectTimeout;
    private final Duration readTimeout;

    @Builder(toBuilder = true)
    public ServiceIdentifier(String serviceName,
                             String preferredVersion,
                             String minimumVersion,
                             Connector connector,
                             Duration connectTimeout,
                             Duration readTimeout) {

        checkArgumentNotBlank(serviceName);

        this.serviceName = serviceName;
        this.preferredVersion = preferredVersion;
        this.minimumVersion = minimumVersion;
        this.connector = Optional.ofNullable(connector).orElse(Connector.APPLICATION);
        this.connectTimeout = Optional.ofNullable(connectTimeout).orElse(RegistryAwareClientConstants.DEFAULT_CONNECT_TIMEOUT);
        this.readTimeout = Optional.ofNullable(readTimeout).orElse(RegistryAwareClientConstants.DEFAULT_CONNECT_REQUEST_TIMEOUT);
    }

}
