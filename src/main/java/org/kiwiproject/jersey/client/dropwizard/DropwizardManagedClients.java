package org.kiwiproject.jersey.client.dropwizard;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.nonNull;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;

import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.client.ssl.TlsConfiguration;
import io.dropwizard.setup.Environment;
import io.dropwizard.util.Duration;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.config.provider.TlsConfigProvider;
import org.kiwiproject.jersey.client.RegistryAwareClient;
import org.kiwiproject.jersey.client.RegistryAwareClientConstants;
import org.kiwiproject.json.JsonHelper;
import org.kiwiproject.registry.client.RegistryClient;

import javax.ws.rs.client.Client;

/**
 * Utilities to create JAX-RS {@link Client}s that are fully managed by Dropwizard.
 * <p>
 * The instances created by this factory are not aware of any service registries unless they return a
 * {@link RegistryAwareClient} will be service registry-aware. Choose wisely.
 *
 * @see JerseyClientConfiguration
 * @see JerseyClientBuilder
 * @see io.dropwizard.lifecycle.Managed
 */
@Slf4j
@UtilityClass
public class DropwizardManagedClients {

    private static final JsonHelper JSON_HELPER = JsonHelper.newDropwizardJsonHelper();
    private static final TlsConfigProvider TLS_CONFIG_PROVIDER = TlsConfigProvider.builder().build();
    private static final String[] TLS_CONFIG_IGNORED_PATHS = new String[]{ "validKeyStorePassword", "validTrustStorePassword"};
    private static final String[] JERSEY_CONFIG_TLS_IGNORED_PATHS = new String[] { "tls.validKeyStorePassword", "tls.validTrustStorePassword" };

    /**
     * Create a new Dropwizard-managed {@link Client} with given name and environment, using default timeout values
     * and a TLS configuration provided by {@link TlsConfigProvider}.
     *
     * @param clientName    name for the client being managed
     * @param environment   the Dropwizard environment
     * @return  a {@link Client} fully managed by Dropwizard
     * @throws IllegalStateException if the {@link TlsConfigProvider} cannot provide
     * @see #newDefaultJerseyClientConfiguration()
     */
    public static Client newDefaultManagedJerseyClient(String clientName, Environment environment) {
        var config = newDefaultJerseyClientConfiguration();
        return newManagedJerseyClient(clientName, environment, config);
    }

    /**
     * Create a new Dropwizard-managed {@link Client} with given name and environment, using the given configuration.
     *
     * @param clientName    name for the client being managed
     * @param environment   the Dropwizard environment
     * @param config        the configuration for the client
     * @return a {@link Client} fully managed by Dropwizard
     */
    public static Client newManagedJerseyClient(String clientName, Environment environment, JerseyClientConfiguration config) {
        return new JerseyClientBuilder(environment)
                .using(config)
                .build(clientName);
    }

    /**
     * Create a new Dropwizard-managed {@link RegistryAwareClient} with the same behavior as
     * {@link #newDefaultManagedJerseyClient(String, Environment)} but also being registry-aware.
     *
     * @param clientName        name for the client being managed
     * @param environment       the Dropwizard environment
     * @param registryClient    the registry-aware client
     * @return a {@link RegistryAwareClient} fully managed by Dropwizard
     */
    public static RegistryAwareClient newDefaultManagedRegistryAwareClient(String clientName, Environment environment, RegistryClient registryClient) {
        var config = newDefaultJerseyClientConfiguration();
        return newManagedRegistryAwareClient(clientName, environment, config, registryClient);
    }

    /**
     * Create a new Dropwizard-managed {@link RegistryAwareClient} with the same behavior as
     * {@link #newDefaultManagedJerseyClient(String, Environment)} but also being registry-aware.
     *
     * @param clientName        name for the client being managed
     * @param environment       the Dropwizard environment
     * @param config            the configuration for the client
     * @param registryClient    the registry-aware client
     * @return a {@link RegistryAwareClient} fully managed by Dropwizard
     */
    public static RegistryAwareClient newManagedRegistryAwareClient(String clientName,
                                                                    Environment environment,
                                                                    JerseyClientConfiguration config,
                                                                    RegistryClient registryClient) {

        var jerseyClient = newManagedJerseyClient(clientName, environment, config);
        return new RegistryAwareClient(jerseyClient, registryClient);
    }

    /**
     * Create a new {@link JerseyClientConfiguration} with a TLS configuration provided by {@link TlsConfigProvider}
     * and timeouts set to the Registry Aware Client default values. All other values are the defaults for {@link JerseyClientConfiguration}.
     *
     * @return new {@link JerseyClientConfiguration}
     * @throws IllegalStateException if the {@link TlsConfigProvider} cannot provide
     * @see RegistryAwareClientConstants#DEFAULT_READ_TIMEOUT
     * @see RegistryAwareClientConstants#DEFAULT_CONNECT_TIMEOUT
     * @see RegistryAwareClientConstants#DEFAULT_SOCKET_TIMEOUT
     */
    public static JerseyClientConfiguration newDefaultJerseyClientConfiguration() {
        return newJerseyClientConfiguration(
                Duration.milliseconds(RegistryAwareClientConstants.DEFAULT_READ_TIMEOUT_MILLIS),
                Duration.milliseconds(RegistryAwareClientConstants.DEFAULT_CONNECT_TIMEOUT_MILLIS),
                Duration.milliseconds(RegistryAwareClientConstants.DEFAULT_SOCKET_TIMEOUT_MILLIS),
                getTlsConfigurationFromProviderOrThrow()
        );
    }

    private static TlsConfiguration getTlsConfigurationFromProviderOrThrow() {
        checkState(TLS_CONFIG_PROVIDER.canProvide(), "TlsConfigProvider cannot provide TLS properties");
        return TLS_CONFIG_PROVIDER.getTlsContextConfiguration().toDropwizardTlsConfiguration();
    }

    /**
     * Create a new {@link JerseyClientConfiguration} with default Dropwizard values, except for the timeouts
     * and {@link TlsConfiguration}, which are set to the given parameter values.
     *
     * @param readTimeout       read timeout for connections
     * @param connectTimeout    connection timeout for connections
     * @param timeout           total socket timeout for connections
     * @param tlsConfig         the tls Configuration
     * @return  a new {@link JerseyClientConfiguration}
     */
    public static JerseyClientConfiguration newJerseyClientConfiguration(Duration readTimeout,
                                                                         Duration connectTimeout,
                                                                         Duration timeout,
                                                                         TlsConfiguration tlsConfig) {
        var config = newJerseyClientConfiguration(readTimeout, connectTimeout, timeout);
        config.setTlsConfiguration(tlsConfig);
        return config;
    }

    /**
     * Create a new {@link JerseyClientConfiguration} with default Dropwizard values, except for the timeouts
     * and {@link TlsConfiguration}, which are set to the given parameter values.
     *
     * @param readTimeout       read timeout for connections
     * @param connectTimeout    connection timeout for connections
     * @param timeout           total socket timeout for connections
     * @return  a new {@link JerseyClientConfiguration}
     */
    public static JerseyClientConfiguration newJerseyClientConfiguration(Duration readTimeout, Duration connectTimeout, Duration timeout) {
        var config = new JerseyClientConfiguration();
        config.setConnectionRequestTimeout(readTimeout);
        config.setConnectionTimeout(connectTimeout);
        config.setTimeout(timeout);
        return config;
    }

    /**
     * Create a copy of the given {@link JerseyClientConfiguration}, ensuring there is a {@link TlsConfiguration}.
     * If {@code original} already has a {@link TlsConfiguration}, that will be used. Otherwise, a new one will
     * be created and populated using a {@link TlsConfigProvider}.
     *
     * @param original a JerseyClientConfiguration with or without TLS configuration
     * @return a copy of {@code original} that will contain a {@link TlsConfiguration}
     */
    public static JerseyClientConfiguration copyOfWithTlsConfiguration(JerseyClientConfiguration original) {
        return copyOfWithTlsConfiguration(original, TlsConfigProvider.builder().build());
    }

    static JerseyClientConfiguration copyOfWithTlsConfiguration(JerseyClientConfiguration original, TlsConfigProvider tlsConfigProvider) {
        checkArgumentNotNull(original);

        if (nonNull(original.getTlsConfiguration())) {
            LOG.info("Original JerseyClientConfiguration has a TlsConfiguration, so will use that configuration. " +
                    "Returning copy of original JerseyClientConfiguration");
            return copyOf(original);
        }

        if (tlsConfigProvider.canNotProvide()) {
            LOG.warn("Original JerseyClientConfiguration does not have a TlsConfiguration, but the TlsConfigProvider cannot provide." +
                    " Returning copy of original JerseyClientConfiguration");
            return copyOf(original);
        }

        var tlsConfig = tlsConfigProvider.getTlsContextConfiguration().toDropwizardTlsConfiguration();

        LOG.info("Original JerseyClientConfiguration does not have aa TlsConfiguration, and TlsConfigProvider can provide." +
                " Configuring JerseyClientConfiguration with TlsConfiguration provided by TlsConfigProvider");

        return copyOfReplacingTlsConfiguration(original, tlsConfig);
    }

    /**
     * Create a copy of the given {@link JerseyClientConfiguration}, but replacing any existing {@link TlsConfiguration}
     * with a copy of the given {@code newTlsConfiguration}
     *
     * @param original              a JerseyClientConfiguration with or without TLS configuration
     * @param newTlsConfiguration   the {@link TlsConfiguration} that will be set into the returned value
     * @return a copy of {@code original}, but containing {@code newTlsConfiguration} as the TLS configuration
     */
    public static JerseyClientConfiguration copyOfReplacingTlsConfiguration(JerseyClientConfiguration original,
                                                                            TlsConfiguration newTlsConfiguration) {
        var copy = copyOf(original);

        // Very small optimization to make sure someone didn't just pass in the same exact TlsConfiguration object
        if (newTlsConfiguration == original.getTlsConfiguration()) {
            return copy;
        }

        var newTlsConfigCopy = JSON_HELPER.copyIgnoringPaths(newTlsConfiguration, TlsConfiguration.class, TLS_CONFIG_IGNORED_PATHS);
        copy.setTlsConfiguration(newTlsConfigCopy);
        return copy;
    }

    /**
     * Create a copy of the given {@link JerseyClientConfiguration}.
     *
     * @param original a JerseyClientConfiguration
     * @return a copy of {@code original}
     */
    public static JerseyClientConfiguration copyOf(JerseyClientConfiguration original) {
        checkArgumentNotNull(original);

        return JSON_HELPER.copyIgnoringPaths(original, JerseyClientConfiguration.class, JERSEY_CONFIG_TLS_IGNORED_PATHS);
    }
}
