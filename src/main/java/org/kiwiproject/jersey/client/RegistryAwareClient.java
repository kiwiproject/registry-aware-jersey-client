package org.kiwiproject.jersey.client;

import static java.util.Objects.nonNull;

import com.google.common.annotations.VisibleForTesting;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.jersey.client.exception.MissingServiceRuntimeException;
import org.kiwiproject.registry.client.RegistryClient;
import org.kiwiproject.registry.model.Port.PortType;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.registry.util.ServiceInstancePaths;

import javax.annotation.Nullable;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.WebTarget;
import java.util.Map;
import java.util.function.Supplier;

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

    /**
     * Creates a new {@link RegistryAwareClient} with the given {@link Client} and {@link RegistryClient}.
     *
     * @param client         the Jersey client to use
     * @param registryClient the registry lookup client
     */
    public RegistryAwareClient(Client client, RegistryClient registryClient) {
        this(client, registryClient, null);
    }

    /**
     * Creates a new {@link RegistryAwareClient} with the given {@link Client}, {@link RegistryClient}
     * and {@link Supplier} that will be used to automatically attach request headers to <em>all</em> requests
     * made by this client.
     * <p>
     * If {@code headersSupplier} is {@code null}, it is ignored.
     *
     * @param client          the Jersey client to use
     * @param registryClient  the registry lookup client
     * @param headersSupplier a supplier of headers to attach to requests, may be {@code null}
     */
    public RegistryAwareClient(Client client,
                               RegistryClient registryClient,
                               @Nullable Supplier<Map<String, Object>> headersSupplier) {
        this.client = client;
        this.registryClient = registryClient;

        if (nonNull(headersSupplier)) {
            this.client.register(new AddHeadersOnRequestFilter(headersSupplier));
        }
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
     * <p>
     * Note: The {@link WebTarget} returned will always be setup to access the application port on the service.
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
     * <p>
     * Note: By specifying the connector as {@link PortType#ADMIN} in {@code identifier} the {@link WebTarget} will be
     * setup to access the admin port on the service.
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
        var path = identifier.getConnector() == PortType.APPLICATION ? instance.getPaths().getHomePagePath() : "/";
        return ServiceInstancePaths.urlForPath(instance.getHostName(), instance.getPorts(), identifier.getConnector(), path);
    }

    static class AddHeadersOnRequestFilter implements ClientRequestFilter {

        private final Supplier<Map<String, Object>> headersSupplier;

        AddHeadersOnRequestFilter(Supplier<Map<String, Object>> headersSupplier) {
            this.headersSupplier = headersSupplier;
        }

        @Override
        public void filter(ClientRequestContext requestContext) {
            var headers = headersSupplier.get();
            headers.forEach((key, value) -> requestContext.getHeaders().add(key, value));
        }
    }

}
