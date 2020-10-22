package org.kiwiproject.jersey.client;

import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotBlank;

import lombok.Builder;
import lombok.Getter;
import lombok.With;

import java.time.Duration;

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

    @Builder.Default
    private Connector connector = Connector.APPLICATION;

    @Builder.Default
    private Duration connectTimeout = RegistryAwareClientConstants.DEFAULT_CONNECT_TIMEOUT;

    @Builder.Default
    private Duration readTimeout = RegistryAwareClientConstants.DEFAULT_CONNECT_REQUEST_TIMEOUT;

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
        this.connector = connector;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

}
