package org.kiwiproject.jersey.client;

import static org.kiwiproject.base.KiwiStrings.f;

import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.registry.client.RegistryClient;
import org.kiwiproject.registry.model.ServiceInstance;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import java.util.Optional;

/**
 * An extension of the JAX-RS Wink Client) interface that provides additional {@code target(...)) methods
 * that will find service instances from a registry, e.g. Consul.
 */
@SuppressWarnings("WeakerAccess")
@Slf4j
public class RegistryAwareClient implements Client {

    // Suppress "URIs should not be hardcoded" since this is just a part of the path, and its our standard status path
    @SuppressWarnings("java:S1075")
    private static final String STATUS_URL_PATH = "/ping";

    @Delegate
    private final Client client;

    private final RegistryClient registryClient;

    public RegistryAwareClient(Client client, RegistryClient registryClient) {
        this.client = client;
        this.registryClient = registryClient;
    }

    /**
     * Return the underlying "raw" JAX-RS {@link Client} instance. Generally won't be needed but this provides an
     * "escape hatch" if it is needed for some reason. Use wisely, sparingly, or not at all...
     */
    public Client client() {
        return client;
    }

    /**
     * Provide a {@link WebTarget} by looking up a service in the registry using the given service name. Finds the latest
     * available version. If more than one instance is found, then one of them is randomly chosen.
     *
     * @param serviceName the service name in the registry
     * @return a {@link WebTarget} for a randomly selected service instance
     */
    public WebTarget targetForService(String serviceName) {
        return targetForService(new ServiceIdentifier(serviceName));
    }

    /**
     * Provide a {@link WebTarget) by looking up a service in the registry using the given service identifier.
     * If more than one instance is found, then one of them is randomly chosen.
     *
     * @param identifier uniquely identifies the service
     * @return a {@link WebTarget} for a randomly selected service instance
     */
    public WebTarget targetForService(ServiceIdentifier identifier) {
        var instanceQuery = RegistryClient.InstanceQuery.builder()
                .serviceName(identifier.getServiceName())
                .preferredVersion(identifier.getPreferredVersion())
                .minimumVersion(identifier.getMinimumVersion())
                .build();

        LOG.trace("Find instances with name {}, preferredVersion {}, minimumVersion {}",
                instanceQuery.getServiceName(), instanceQuery.getPreferredVersion(), instanceQuery.getMinimumVersion());

        var uri = registryClient.findServiceInstanceBy(instanceQuery)
                .map(instance -> buildInstanceUri(identifier, instance))
                .orElseThrow(() -> newMissingServiceRuntimeException(identifier));

        return client.target(uri);
    }

    private static String buildInstanceUri(ServiceIdentifier identifier, ServiceInstance instance) {
        return (identifier.getConnector() == ServiceIdentifier.Connector.ADMIN) ? parseAdminUrl(instance) : instance.getPaths().getHomePagePath();
    }

    private static MissingServiceRuntimeException newMissingServiceRuntimeException(ServiceIdentifier identifier) {
        var message = f("No service instances found with name {}, preferred version {}, min version {}",
                identifier.getServiceName(),
                Optional.ofNullable(identifier.getPreferredVersion()).orElse("<latest>"),
                Optional.ofNullable(identifier.getMinimumVersion()).orElse("<none>")
        );

        return new MissingServiceRuntimeException(message);
    }

    private static String parseAdminUrl(ServiceInstance instance) {
        var url = instance.getPaths().getStatusPath();
        var end = url.lastIndexOf(STATUS_URL_PATH);
        return url.substring(0, end);
    }

    static class MissingServiceRuntimeException extends RuntimeException {
        public MissingServiceRuntimeException(String message) {
            super(message);
        }
    }
}
