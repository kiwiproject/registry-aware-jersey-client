package org.kiwiproject.jersey.client;

import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotBlank;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.util.Duration;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import org.kiwiproject.registry.model.Port;

import java.util.Optional;

/**
 * Service definition used for the client connection
 */
@Value
public class ServiceIdentifier {

    @With
    String serviceName;

    String preferredVersion;
    String minimumVersion;
    Port.PortType connector;
    Duration connectTimeout;
    Duration readTimeout;

    @JsonCreator
    @Builder(toBuilder = true)
    private ServiceIdentifier(@JsonProperty("serviceName") String serviceName,
                             @JsonProperty("preferredVersion") String preferredVersion,
                             @JsonProperty("minimumVersion") String minimumVersion,
                             @JsonProperty("connector") Port.PortType connector,
                             @JsonProperty("connectTimeout") Duration connectTimeout,
                             @JsonProperty("readTimeout") Duration readTimeout) {

        checkArgumentNotBlank(serviceName, "Service name is required");

        this.serviceName = serviceName;
        this.preferredVersion = preferredVersion;
        this.minimumVersion = minimumVersion;
        this.connector = Optional.ofNullable(connector).orElse(Port.PortType.APPLICATION);
        this.connectTimeout = Optional.ofNullable(connectTimeout).orElse(RegistryAwareClientConstants.DEFAULT_CONNECT_TIMEOUT);
        this.readTimeout = Optional.ofNullable(readTimeout).orElse(RegistryAwareClientConstants.DEFAULT_CONNECT_REQUEST_TIMEOUT);
    }

}
