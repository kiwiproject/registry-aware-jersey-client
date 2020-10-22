package org.kiwiproject.jersey.client;

import static java.util.Objects.isNull;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Duration;

@SuppressWarnings("WeakerAccess")
@NoArgsConstructor
@Getter
@Setter
@ToString
public class ServiceIdentifier {
    public enum Connector {
        ADMIN, APPLICATION
    }

    private String serviceName;
    private String preferredVersion;
    private String minimumVersion;

    private Connector connector = Connector.APPLICATION;
    private Duration connectTimeout = RegistryAwareClientConstants.DEFAULT_CONNECT_TIMEOUT;
    private Duration readTimeout = RegistryAwareClientConstants.DEFAULT_CONNECT_REQUEST_TIMEOUT;

    public ServiceIdentifier(String serviceName) {
        this(serviceName, null, Connector.APPLICATION);
    }

    public ServiceIdentifier(String serviceName, String preferredVersion) {
        this(serviceName, preferredVersion, Connector.APPLICATION);
    }

    public ServiceIdentifier(String serviceName, Connector connector) {
        this(serviceName, null, connector);
    }

    public ServiceIdentifier(String serviceName, String preferredVersion, Connector connector) {
        this.serviceName = serviceName;
        this.preferredVersion = preferredVersion;
        this.connector = connector;
    }

    public ServiceIdentifier(String serviceName, Duration connectTimeout, Duration readTimeout) {
        this.serviceName = serviceName;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    public ServiceIdentifier(String serviceName, Connector connector, Duration connectTimeout, Duration readTimeout) {
        this.serviceName = serviceName;
        this.connector = connector;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    public ServiceIdentifier(String serviceName,
                             String preferredVersion,
                             Connector connector,
                             Duration connectTimeout,
                             Duration readTimeout) {
        this.serviceName = serviceName;
        this.preferredVersion = preferredVersion;
        this.connector = connector;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    public ServiceIdentifier(String serviceName,
                             String preferredVersion,
                             String minimumVersion,
                             Connector connector,
                             Duration connectTimeout,
                             Duration readTimeout) {
        this.serviceName = serviceName;
        this.preferredVersion = preferredVersion;
        this.minimumVersion = minimumVersion;
        this.connector = connector;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    /**
     * Make a copy of {@code original).
     */
    public static ServiceIdentifier copy0f(ServiceIdentifier original) {
        checkArgumentNotNull(original);

        var copy = new ServiceIdentifier();
        copy.serviceName = original.getServiceName();
        copy.preferredVersion = original.getPreferredVersion();
        copy.minimumVersion = original.getMinimumVersion();
        copy.connector = original.getConnector();
        copy.connectTimeout = original.getConnectTimeout();
        copy.readTimeout = original.getReadTimeout();
        return copy;
    }

    /**
     * Return a boolean indicating whether this {@link ServiceIdentifier) prefers the latest available version
     * of the service it describes. A non-existent (null) {@code version} indicates latest version.
     */
    public boolean isPreferLatestVersion() {
        return isNull(preferredVersion);
    }

    /**
     * Creates a copy of this instance but with the specified {@code serviceName}.
     *
     * @implNote Intentionally not using Lombok's {@code Wither) because it seems to require an all arguments
     * constructor, and that causes problems when trying to configure a instances of {@link ServiceIdentifier)
     * instances from a YAML configuration file, e.g in Dropwizard, when only some of the properties are specified.
     */
    @SuppressWarnings("WeakerAccess")
    public ServiceIdentifier withServiceName(String newServiceName) {
        ServiceIdentifier serviceId = new ServiceIdentifier();
        serviceId.setServiceName(newServiceName);
        serviceId.setPreferredVersion(preferredVersion);
        serviceId.setConnector(connector);
        serviceId.setConnectTimeout(connectTimeout);
        serviceId.setReadTimeout(readTimeout);
        return serviceId;
    }
}
