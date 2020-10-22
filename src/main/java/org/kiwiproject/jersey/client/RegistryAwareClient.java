package org.kiwiproject.jersey.client;

import static org.kiwiproject.base.KiwiStrings.f;

import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.registry.client.RegistryClient;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.registry.util.ServiceInstancePaths;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import java.util.Optional;

/**
 * An extension of the JAX-RS {@link Client} interface that provides additional {@code target(...)) methods
 * that will find service instances from a registry, e.g. Consul.
 */
@Slf4j
public class RegistryAwareClient implements Client {

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
        return targetForService(ServiceIdentifier.builder().serviceName(serviceName).build());
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
        var path = identifier.getConnector() == ServiceIdentifier.Connector.APPLICATION ? instance.getPaths().getHomePagePath() : "/";
        return ServiceInstancePaths.urlForPath(instance.getHostName(), instance.getPorts(), Port.PortType.valueOf(identifier.getConnector().name()), path);
    }

    private static MissingServiceRuntimeException newMissingServiceRuntimeException(ServiceIdentifier identifier) {
        var message = f("No service instances found with name {}, preferred version {}, min version {}",
                identifier.getServiceName(),
                Optional.ofNullable(identifier.getPreferredVersion()).orElse("<latest>"),
                Optional.ofNullable(identifier.getMinimumVersion()).orElse("<none>")
        );

        return new MissingServiceRuntimeException(message);
    }

    static class MissingServiceRuntimeException extends RuntimeException {
        public MissingServiceRuntimeException(String message) {
            super(message);
        }
    }
}
