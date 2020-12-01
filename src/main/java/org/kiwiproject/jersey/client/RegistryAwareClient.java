package org.kiwiproject.jersey.client;

import com.google.common.annotations.VisibleForTesting;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.jersey.client.exception.MissingServiceRuntimeException;
import org.kiwiproject.registry.client.RegistryClient;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.registry.util.ServiceInstancePaths;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;

/**
 * An extension of the JAX-RS {@link Client} interface that provides additional {@code target(...)} methods
 * that will find service instances from a registry, e.g. Consul.
 */
@Slf4j
public class RegistryAwareClient implements Client {

    @Delegate
    private final Client client;

    @VisibleForTesting
    @Getter(AccessLevel.PACKAGE)
    private final RegistryClient registryClient;

    public RegistryAwareClient(Client client, RegistryClient registryClient) {
        this.client = client;
        this.registryClient = registryClient;
    }

    /**
     * Return the underlying "raw" JAX-RS {@link Client} instance. Generally won't be needed but this provides an
     * "escape hatch" if it is needed for some reason. Use wisely, sparingly, or not at all...
     *
     * @return the underlying "raw" JAX-RS {@link Client}
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
     * Provide a {@link WebTarget} by looking up a service in the registry using the given service identifier.
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
                .orElseThrow(() -> MissingServiceRuntimeException.from(identifier));

        return client.target(uri);
    }

    private static String buildInstanceUri(ServiceIdentifier identifier, ServiceInstance instance) {
        var path = identifier.getConnector() == Port.PortType.APPLICATION ? instance.getPaths().getHomePagePath() : "/";
        return ServiceInstancePaths.urlForPath(instance.getHostName(), instance.getPorts(), identifier.getConnector(), path);
    }

}
