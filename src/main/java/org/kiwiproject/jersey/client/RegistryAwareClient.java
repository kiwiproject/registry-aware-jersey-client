package org.kiwiproject.jersey.client;

import static java.util.Objects.nonNull;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;

import com.google.common.annotations.VisibleForTesting;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.WebTarget;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.kiwiproject.base.KiwiDeprecated;
import org.kiwiproject.jersey.client.exception.MissingServiceRuntimeException;
import org.kiwiproject.jersey.client.filter.AddHeadersClientRequestFilter;
import org.kiwiproject.registry.client.RegistryClient;
import org.kiwiproject.registry.model.Port.PortType;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.registry.util.ServiceInstancePaths;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * An extension of the JAX-RS {@link Client} interface that provides additional {@code target(...)} methods
 * that will find service instances from a registry, e.g., Consul.
 */
@Slf4j
public class RegistryAwareClient implements Client, AutoCloseable {

    @Delegate
    private final Client client;

    @VisibleForTesting
    @Getter(AccessLevel.PACKAGE)
    private final RegistryClient registryClient;
    
    private final AtomicBoolean closed;

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
     * <p>
     * As of 2.3.0, this constructor is deprecated because it mutates the {@link Client} when a non-null
     * {@code headersSupplier} is provided by registering a {@link AddHeadersClientRequestFilter}. Instead,
     * use {@link #RegistryAwareClient(Client, RegistryClient)} and pass in a fully-configured {@link Client}.
     * This avoids potential issues where a caller provides a {@link Client} but might not expect that client
     * to be modified.
     *
     * @param client          the Jersey client to use
     * @param registryClient  the registry lookup client
     * @param headersSupplier a supplier of headers to attach to requests, may be {@code null}
     * @deprecated use {@link #RegistryAwareClient(Client, RegistryClient)} and provide a fully-configured {@link Client}
     */
    @Deprecated(since = "2.3.0")
    @KiwiDeprecated(replacedBy = "#RegistryAwareClient(Client, RegistryClient)")
    public RegistryAwareClient(Client client,
                               RegistryClient registryClient,
                               @Nullable Supplier<Map<String, Object>> headersSupplier) {
        this.client = requireNotNull(client, "client must not be null");
        this.registryClient = requireNotNull(registryClient, "registryClient must not be null");
        this.closed = new AtomicBoolean();

        if (nonNull(headersSupplier)) {
            this.client.register(AddHeadersClientRequestFilter.fromMapSupplier(headersSupplier));
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

    @Override
    public void close() {
        client.close();
        closed.set(true);
    }

    /**
     * Check whether this client has been closed. 
     *
     * @return true if this client has been closed, otherwise false
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Provide a {@link WebTarget} by looking up a service in the registry using the given service name. Finds the
     * latest available version. If more than one instance is found, then one of them is randomly chosen.
     * <p>
     * Note: The returned {@link WebTarget} will always be set up to access the application port on the service.
     *
     * @param serviceName the service name in the registry
     * @return a {@link WebTarget} for a randomly selected service instance
     * @see #targetForService(String, PortType)
     * @see #targetForService(ServiceIdentifier)
     */
    public WebTarget targetForService(String serviceName) {
        return targetForService(serviceName, PortType.APPLICATION);
    }

    /**
     * Provide a {@link WebTarget} by looking up a service in the registry using the given service name and
     * {@link PortType} . Finds the latest available version. If more than one instance is found, then one of them
     * is randomly chosen.
     *
     * @param serviceName the service name in the registry
     * @param portType    the type of port to connect to
     * @return a {@link WebTarget} for a randomly selected service instance
     * @see #targetForService(ServiceIdentifier)
     */
    public WebTarget targetForService(String serviceName, PortType portType) {
        var serviceIdentifier = ServiceIdentifier.builder()
                .serviceName(serviceName)
                .connector(portType)
                .build();

        return targetForService(serviceIdentifier);
    }

    /**
     * Provide a {@link WebTarget} by looking up a service in the registry using the given {@link ServiceIdentifier} and
     * {@link org.kiwiproject.registry.model.Port.PortType PortType}. Finds the latest available version. If more than one
     * instance is found, then one of them is randomly chosen.
     *
     * @param originalIdentifier    the original identifier that will be adjusted with the given port type
     * @param portType              the port type to use for the {@link WebTarget}
     * @return a {@link WebTarget} for a randomly selected service instance
     * @see #targetForService(ServiceIdentifier)
     */
    public WebTarget targetForService(ServiceIdentifier originalIdentifier, PortType portType) {
        checkArgumentNotNull(originalIdentifier, "Original ServiceIdentifier must not be null");
        var serviceIdentifier = originalIdentifier.withConnector(portType);

        return targetForService(serviceIdentifier);
    }

    /**
     * Provide a {@link WebTarget} by looking up a service in the registry using the given service identifier.
     * If more than one instance is found, then one of them is randomly chosen.
     * <p>
     * Note: By specifying the connector as {@link PortType#ADMIN} in {@code identifier} the {@link WebTarget} will be
     * set up to access the admin port on the service.
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

        traceLogInstanceQuery(instanceQuery);

        var uri = registryClient.findServiceInstanceBy(instanceQuery)
                .map(instance -> buildInstanceUri(identifier, instance))
                .orElseThrow(() -> MissingServiceRuntimeException.from(identifier));

        return client.target(uri);
    }

    /**
     * Provide a {@link WebTarget} by looking up a service in the registry using the given service identifier.
     * If more than one instance is found, then one of them is randomly chosen. The given {@code pathResolver} function allows
     * a path to be chosen from the {@link ServiceInstance} and added to the {@link WebTarget} path.
     *
     * @param original      the original {@link ServiceIdentifier} used to look up a service
     * @param portType      the port type (APPLICATION or ADMIN) to use for the WebTarget port
     * @param pathResolver  a function to resolve the path to use from the ServiceInstance
     * @return a {@link WebTarget} for a randomly selected service instance
     */
    public WebTarget targetForService(ServiceIdentifier original, PortType portType, Function<ServiceInstance, String> pathResolver) {
        var identifier = original.withConnector(portType);

        var instanceQuery = RegistryClient.InstanceQuery.builder()
                .serviceName(identifier.getServiceName())
                .preferredVersion(identifier.getPreferredVersion())
                .minimumVersion(identifier.getMinimumVersion())
                .build();

        traceLogInstanceQuery(instanceQuery);

        var serviceInstance = registryClient.findServiceInstanceBy(instanceQuery)
                .orElseThrow(() -> MissingServiceRuntimeException.from(identifier));

        var uri = buildInstanceUri(identifier, serviceInstance);

        return client.target(uri)
                .path(pathResolver.apply(serviceInstance));
    }

    private static void traceLogInstanceQuery(RegistryClient.InstanceQuery instanceQuery) {
        LOG.trace("Find instances with name {}, preferredVersion {}, minimumVersion {}",
                instanceQuery.getServiceName(), instanceQuery.getPreferredVersion(), instanceQuery.getMinimumVersion());
    }

    private static String buildInstanceUri(ServiceIdentifier identifier, ServiceInstance instance) {
        var path = identifier.getConnector() == PortType.APPLICATION ? instance.getPaths().getHomePagePath() : "/";
        return ServiceInstancePaths.urlForPath(instance.getHostName(), instance.getPorts(), identifier.getConnector(), path);
    }

}
