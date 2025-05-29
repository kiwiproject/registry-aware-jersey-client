package org.kiwiproject.jersey.client.dropwizard;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;

import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.core.setup.Environment;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.MultivaluedMap;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.kiwiproject.config.TlsContextConfiguration;
import org.kiwiproject.config.provider.TlsConfigProvider;
import org.kiwiproject.jersey.client.RegistryAwareClient;
import org.kiwiproject.jersey.client.RegistryAwareClientConstants;
import org.kiwiproject.jersey.client.filter.AddHeadersClientRequestFilter;
import org.kiwiproject.registry.client.RegistryClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Builder used for building either raw {@link Client} instances or {@link RegistryAwareClient} instances that are fully
 * managed by Dropwizard.
 *
 * @see JerseyClientBuilder
 */
@Slf4j
public class DropwizardManagedClientBuilder {

    private String clientName;
    private Environment environment;
    private RegistryClient registryClient;
    private JerseyClientConfiguration jerseyClientConfiguration;
    private TlsConfigProvider tlsConfigProvider;
    private boolean tlsOptedOut;
    private Supplier<Map<String, Object>> headersSupplier;
    private Supplier<MultivaluedMap<String, Object>> headersMultivalueSupplier;

    private final Map<String, Object> properties;
    private final List<Class<?>> componentClasses;
    private final List<Object> components;

    public DropwizardManagedClientBuilder() {
        properties = new LinkedHashMap<>();
        components = new ArrayList<>();
        componentClasses = new ArrayList<>();
    }

    /**
     * Sets the name of the client to be managed.
     *
     * @param clientName The name of the client
     * @return this builder
     */
    public DropwizardManagedClientBuilder clientName(String clientName) {
        this.clientName = clientName;
        return this;
    }

    /**
     * Sets the Dropwizard environment to set up the management of the client.
     *
     * @param environment the Dropwizard environment
     * @return this builder
     */
    public DropwizardManagedClientBuilder environment(Environment environment) {
        this.environment = environment;
        return this;
    }

    /**
     * Sets the {@link RegistryClient} to use for {@link RegistryAwareClient}.
     * <p>
     * NOTE: This is not used for a raw {@link Client} instance
     *
     * @param registryClient the {@link RegistryClient} to use for resolving services
     * @return this builder
     */
    public DropwizardManagedClientBuilder registryClient(RegistryClient registryClient) {
        this.registryClient = registryClient;
        return this;
    }

    /**
     * Sets a {@link JerseyClientConfiguration} that is pre-populated with client configuration.
     *
     * @param jerseyClientConfiguration the client configuration
     * @return this builder
     */
    public DropwizardManagedClientBuilder jerseyClientConfiguration(JerseyClientConfiguration jerseyClientConfiguration) {
        this.jerseyClientConfiguration = jerseyClientConfiguration;
        return this;
    }

    /**
     * Sets the TLS configuration that the client will use.
     *
     * @param tlsConfig the TLS configuration for the client
     * @return this builder
     */
    public DropwizardManagedClientBuilder tlsContextConfiguration(TlsContextConfiguration tlsConfig) {
        checkArgumentNotNull(tlsConfig, "tlsConfig must not be null");
        var tlsProvider = TlsConfigProvider.builder()
                .tlsContextConfigurationSupplier(() -> tlsConfig)
                .build();
        return tlsConfigProvider(tlsProvider);
    }

    /**
     * Sets the {@link TlsConfigProvider} that is used to resolve TLS properties for the client.
     *
     * @param tlsConfigProvider the provider of TLS properties
     * @return this builder
     */
    public DropwizardManagedClientBuilder tlsConfigProvider(TlsConfigProvider tlsConfigProvider) {
        this.tlsConfigProvider = tlsConfigProvider;
        return this;
    }

    /**
     * Sets up the builder to OPT OUT of TLS configuration. While we think this shouldn't be done, we want to make sure
     * we support it since Jersey does.
     *
     * @return this builder.
     */
    public DropwizardManagedClientBuilder withoutTls() {
        this.tlsOptedOut = true;
        return this;
    }

    /**
     * The given {@link Supplier} will be used to attach headers to <em>all</em> requests that
     * the built {@link Client} or {@link RegistryAwareClient} instance sends.
     * <p>
     * Use this when you only need to set a single value for each header.
     * <p>
     * Only one of {@code headersSupplier} or {@code headersMultivalueSupplier} should be set.
     *
     * @param headersSupplier a supplier of headers to attach to requests
     * @return this builder
     * @see #headersMultivalueSupplier(Supplier)
     */
    public DropwizardManagedClientBuilder headersSupplier(Supplier<Map<String, Object>> headersSupplier) {
        this.headersSupplier = headersSupplier;
        return this;
    }

    /**
     * The given {@link Supplier} will be used to attach headers to <em>all</em> requests that
     * the built {@link Client} or {@link RegistryAwareClient} instance sends.
     * <p>
     * Use this when you need to set multiple values for the same header.
     * <p>
     * Only one of {@code headersSupplier} or {@code headersMultivalueSupplier} should be set.
     *
     * @param headersMultivalueSupplier a supplier of headers to attach to requests
     * @return this builder
     * @see #headersSupplier(Supplier)
     */
    public DropwizardManagedClientBuilder headersMultivalueSupplier(
            Supplier<MultivaluedMap<String, Object>> headersMultivalueSupplier) {
        this.headersMultivalueSupplier = headersMultivalueSupplier;
        return this;
    }

    /**
     * Sets a custom property on the client builder.
     * <p>
     * <strong>WARNING</strong>:
     * See the warning in the Javadocs of {@link JerseyClientBuilder#withProperty(String, Object)}.
     * In other words, prefer using {@link JerseyClientConfiguration} for setting properties using
     * the {@link #jerseyClientConfiguration(JerseyClientConfiguration)} method.
     *
     * @param name  the name of the property to set
     * @param value the value of the property to set
     * @return this builder
     * @see JerseyClientBuilder#withProperty(String, Object)
     * @see jakarta.ws.rs.client.ClientBuilder#property(String, Object)
     * @see #jerseyClientConfiguration(JerseyClientConfiguration)
     */
    public DropwizardManagedClientBuilder property(String name, Object value) {
        properties.put(name, value);
        return this;
    }

    /**
     * Registers a component class with the client builder.
     *
     * @param componentClass the class of the component to register; must not be null
     * @return this builder
     * @see JerseyClientBuilder#withProvider(Class)
     * @see jakarta.ws.rs.client.ClientBuilder#register(Class)
     */
    public DropwizardManagedClientBuilder registerComponentClass(Class<?> componentClass) {
        checkArgumentNotNull(componentClass, "componentClass must not be null");
        componentClasses.add(componentClass);
        return this;
    }

    /**
     * Registers an instance of a component with the client builder.
     *
     * @param component The component to register
     * @see JerseyClientBuilder#withProvider(Object)
     * @see jakarta.ws.rs.client.ClientBuilder#register(Object)
     */
    public DropwizardManagedClientBuilder registerComponent(Object component) {
        checkArgumentNotNull(component, "component must not be null");
        components.add(component);
        return this;
    }

    /**
     * Creates a new Dropwizard-managed {@link Client}.
     *
     * @return the newly created {@link Client} managed by Dropwizard
     * @throws IllegalStateException if {@code clientName} or {@code environment} is not specified
     */
    public Client buildManagedJerseyClient() {
        checkState(isNotBlank(clientName), "A name for the managed client must be specified");
        checkState(nonNull(environment), "Dropwizard environment must be provided to create managed client");

        if (isNull(jerseyClientConfiguration)) {
            var provider = tlsOptedOut ? null : tlsConfigProvider;
            jerseyClientConfiguration(newDefaultJerseyClientConfiguration(provider));
        }

        var builder = new JerseyClientBuilder(environment).using(jerseyClientConfiguration);
        properties.forEach(builder::withProperty);
        componentClasses.forEach(builder::withProvider);
        components.forEach(builder::withProvider);
        var client = builder.build(clientName);

        if (nonNull(headersSupplier)) {
            client.register(AddHeadersClientRequestFilter.fromMapSupplier(headersSupplier));
        } else if (nonNull(headersMultivalueSupplier)) {
            client.register(AddHeadersClientRequestFilter.fromMultivaluedMapSupplier(headersMultivalueSupplier));
        }

        return client;
    }

    /**
     * Create a new Dropwizard-managed {@link RegistryAwareClient} with the same behavior as
     * {@link #buildManagedJerseyClient()} but also being registry-aware.
     *
     * @return the newly created {@link RegistryAwareClient} managed by Dropwizard
     * @throws IllegalStateException if clientName, environment, or registryClient is not specified
     */
    public RegistryAwareClient buildManagedRegistryAwareClient() {
        checkState(nonNull(registryClient), "Registry Client is required for a Registry Aware Client to be created");

        var jerseyClient = buildManagedJerseyClient();
        return new RegistryAwareClient(jerseyClient, registryClient);
    }

    /**
     * Creates a new {@link JerseyClientConfiguration} to use as a default that is <em>NOT</em> configured for TLS.
     *
     * @return a {@link JerseyClientConfiguration} with default values set
     */
    public static JerseyClientConfiguration newDefaultJerseyClientConfiguration() {
        return newDefaultJerseyClientConfiguration(null);
    }

    /**
     * Creates a new {@link JerseyClientConfiguration} to use as a default that has TLS configured <em>only if</em> the
     * given provider is not null and can provide.
     *
     * @param tlsConfigProvider a {@link TlsConfigProvider} used to provide TLS settings
     * @return a {@link JerseyClientConfiguration} with default values set
     */
    public static JerseyClientConfiguration newDefaultJerseyClientConfiguration(
            @Nullable TlsConfigProvider tlsConfigProvider) {

        var config = new JerseyClientConfiguration();
        config.setConnectionRequestTimeout(RegistryAwareClientConstants.DEFAULT_CONNECTION_POOL_TIMEOUT);
        config.setConnectionTimeout(RegistryAwareClientConstants.DEFAULT_CONNECT_TIMEOUT);
        config.setTimeout(RegistryAwareClientConstants.DEFAULT_READ_TIMEOUT);

        if (isNull(tlsConfigProvider)) {
            return config;
        }

        if (tlsConfigProvider.canProvide()) {
            config.setTlsConfiguration(tlsConfigProvider.getTlsContextConfiguration().toDropwizardTlsConfiguration());
        } else {
            LOG.warn("TlsConfigProvider.canProvide() returned false; " +
                    "custom TlsConfiguration cannot be set for default JerseyClientConfiguration");
        }

        return config;
    }
}
