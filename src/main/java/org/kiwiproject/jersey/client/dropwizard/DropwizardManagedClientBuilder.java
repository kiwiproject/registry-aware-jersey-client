package org.kiwiproject.jersey.client.dropwizard;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.base.KiwiStrings.f;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Builder used for building either raw {@link Client} instances or {@link RegistryAwareClient}
 * instances that Dropwizard fully manages (i.e., calling start/stop).
 * <p>
 * This builder is single-use; calling any {@code build*} method finalizes the builder.
 * <p>
 * This requires {@code io.dropwizard:dropwizard-client} as a dependency.
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
    private final List<Consumer<JerseyClientBuilder>> customizers;
    private final AtomicBoolean built;

    public DropwizardManagedClientBuilder() {
        properties = new LinkedHashMap<>();
        components = new ArrayList<>();
        componentClasses = new ArrayList<>();
        customizers = new ArrayList<>();
        built = new AtomicBoolean();
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
     * <p>
     * Note that this only applies when you supply your own {@link JerseyClientConfiguration}.
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
     * @return this builder
     * @see JerseyClientBuilder#withProvider(Object)
     * @see jakarta.ws.rs.client.ClientBuilder#register(Object)
     */
    public DropwizardManagedClientBuilder registerComponent(Object component) {
        checkArgumentNotNull(component, "component must not be null");
        components.add(component);
        return this;
    }

    /**
     * Provides the ability to customize the {@link JerseyClientBuilder} that this builder
     * uses when creating the final client instance.
     * <p>
     * Be careful when using this method, since callers could accidentally override existing
     * configuration such as the {@link JerseyClientConfiguration}, {@link Environment},
     * properties, or providers which were configured on this builder.
     * <p>
     * This method is intended to be used to provide additional configuration of the
     * {@link JerseyClientBuilder}, for example, by calling the various {@code using} methods
     * which are not exposed here such as {@link JerseyClientBuilder#using(javax.net.ssl.HostnameVerifier)} or
     * {@link JerseyClientBuilder#using(org.apache.hc.client5.http.DnsResolver)}.
     * <p>
     * You can add multiple customizers, and they will execute in the order they were added.
     * Customizers always run after the builder is otherwise fully configured. This means
     * it is possible to override previously set configuration properties or providers (components or
     * component classes), either by accident or intentionally. If the customizer sets a property or provider
     * that was already configured, the customizer’s value takes effect.
     * <p>
     * If the customizer throws an exception, the {@code build} methods will throw an
     * {@link IllegalStateException} that wraps the original cause. In addition, a throwing customizer
     * short-circuits later customizers.
     * <p>
     * <em>Do not call {@code build} on the provided {@code JerseyClientBuilder}.</em>
     * The Jersey client should be built by calling one of the {@code build} methods in this class.
     *
     * @param customizer a consumer that accepts a {@link JerseyClientBuilder}
     * @return this builder
     * @since 2.4.0
     */
    public DropwizardManagedClientBuilder customize(Consumer<JerseyClientBuilder> customizer) {
        checkArgumentNotNull(customizer, "customizer must not be null");
        customizers.add(customizer);
        return this;
    }

    /**
     * Creates a new Dropwizard-managed {@link Client}.
     * <p>
     * Properties and providers are applied before customizers;
     * customizers run immediately before building the {@link Client}.
     * <p>
     * This is a single-use build method. Calling this method more than once will
     * result in an {@link IllegalStateException}.
     *
     * @return the newly created {@link Client} managed by Dropwizard
     * @throws IllegalStateException if {@code clientName} or {@code environment} is not specified
     */
    public Client buildManagedJerseyClient() {
        checkState(built.compareAndSet(false, true),
                "Client was already built using this builder. This is a single-use builder.");

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
        applyJerseyClientBuilderCustomizers(builder);
        var client = builder.build(clientName);

        registerHeadersClientRequestFilter(client);

        return client;
    }

    /**
     * Applies the list of customizers to the provided JerseyClientBuilder.
     *
     * @param builder the JerseyClientBuilder to which the customizers will be applied
     */
    private void applyJerseyClientBuilderCustomizers(JerseyClientBuilder builder) {
        if (customizers.isEmpty()) {
            return;
        }

        LOG.debug("Applying {} JerseyClientBuilder customizations", customizers.size());

        var snapshot = List.copyOf(customizers);
        for (int index = 0; index < snapshot.size(); index++) {
            var customizer = snapshot.get(index);
            applyCustomizer(builder, customizer, index);
        }
    }

    /**
     * Applies a single customizer to the given {@link JerseyClientBuilder}.
     * Wraps any exception thrown by the customizer in an {@link IllegalStateException}
     * that includes the 0-based index and client name for easier debugging.
     *
     * @param builder    The {@link JerseyClientBuilder} to customize; must not be null.
     * @param customizer A {@link Consumer} that accepts the {@link JerseyClientBuilder} and performs
     *                   the required customization; must not be null.
     * @param index      The 0-based index of the customizer in the list of customizers; used for descriptive error messages.
     * @throws IllegalStateException If the customizer throws an exception during execution, wrapping the original cause.
     */
    private void applyCustomizer(JerseyClientBuilder builder, Consumer<JerseyClientBuilder> customizer, int index) {
        try {
            customizer.accept(builder);
        } catch (Exception e) {
            var message = f(
                    "Customizer at index {} (0-based) failed while configuring JerseyClientBuilder for client {}",
                    index, clientName);
            throw new IllegalStateException(message, e);
        }
    }

    private void registerHeadersClientRequestFilter(Client client) {
        var bothSet = nonNull(headersSupplier) && nonNull(headersMultivalueSupplier);
        checkState(!bothSet, "Only one of headersSupplier or headersMultivalueSupplier may be set");

        AddHeadersClientRequestFilter.createAndRegister(client, headersSupplier, headersMultivalueSupplier);
    }

    /**
     * Create a new Dropwizard-managed {@link RegistryAwareClient} with the same behavior as
     * {@link #buildManagedJerseyClient()} but also being registry-aware.
     * <p>
     * This is a single-use build method. Calling this method more than once will
     * result in an {@link IllegalStateException}.
     *
     * @return the newly created {@link RegistryAwareClient} managed by Dropwizard
     * @throws IllegalStateException if clientName, environment, or registryClient is not specified
     * @implNote This delegates to {@link #buildManagedJerseyClient()} to build the Jersey {@code Client}
     * and then builds the {@link RegistryAwareClient}.
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
