package org.kiwiproject.jersey.client;

import org.kiwiproject.config.provider.TlsConfigProvider;
import org.kiwiproject.registry.client.RegistryClient;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Configure and create {@link RegistryAwareClient} instances using a fluent builder interface. Use {@link ClientBuilders}
 * to get a builder instance.
 * <p>
 * Inspired by {@link jakarta.ws.rs.client.ClientBuilder}.
 */
public interface ClientBuilder {

    /**
     * Enables the multipart feature on the client.
     *
     * @return this builder
     */
    ClientBuilder multipart();

    /**
     * Sets the connect timeout for the client.
     *
     * @param millis length of timeout in millis
     * @return this builder
     */
    ClientBuilder connectTimeout(int millis);

    /**
     * Sets the connect timeout for the client.
     *
     * @param millis length of timeout in millis
     * @return this builder
     */
    ClientBuilder connectTimeout(long millis);

    /**
     * Sets the connection timeout for the client.
     *
     * @param timeout the duration of the connection timeout; must not be null
     * @return this builder
     */
    ClientBuilder connectTimeout(Duration timeout);

    /**
     * Sets the read timeout for the client.
     *
     * @param millis length of timeout in millis
     * @return this builder
     */
    ClientBuilder readTimeout(int millis);

    /**
     * Sets the read timeout for the client.
     *
     * @param millis length of timeout in millis
     * @return this builder
     */
    ClientBuilder readTimeout(long millis);

    /**
     * Sets the read timeout for the client.
     *
     * @param timeout the duration of the read timeout; must not be null
     * @return this builder
     */
    ClientBuilder readTimeout(Duration timeout);

    /**
     * Sets the connect and read timeouts for the client from a {@link ServiceIdentifier}. This is most useful
     * when you already have a {@link ServiceIdentifier} for a specific service, and you want to create a new
     * {@link RegistryAwareClient} for use making requests to that service. This in no way precludes using the
     * client to connect to any other service, but is a convenience when you are using the "bulkhead" pattern.
     * <p>
     * See Michael Nygard's excellent book <a href="https://pragprog.com/titles/mnee2/release-it-second-edition/">Release It!</a>
     * for information on stability patterns including bulkheads. Microsoft also has a good description in their
     * <a href="https://docs.microsoft.com/en-us/azure/architecture/patterns/bulkhead">Azure design patterns</a>.
     *
     * @param serviceIdentifier defines the read and connect timeouts to set on the client
     * @return this builder
     */
    ClientBuilder timeoutsFrom(ServiceIdentifier serviceIdentifier);

    /**
     * Sets the hostname verifier for the client.
     *
     * @param hostnameVerifier The {@link HostnameVerifier} to use
     * @return this builder
     */
    ClientBuilder hostnameVerifier(HostnameVerifier hostnameVerifier);

    /**
     * Sets the SSL context for the client.
     *
     * @param sslContext The {@link SSLContext} to use
     * @return this builder
     */
    ClientBuilder sslContext(SSLContext sslContext);

    /**
     * Provides a {@link TlsConfigProvider} to use to provide TLS configuration.
     *
     * @param tlsConfigProvider The config provider to provide TLS settings
     * @return this builder
     */
    ClientBuilder tlsConfigProvider(TlsConfigProvider tlsConfigProvider);

    /**
     * Provides the {@link RegistryClient} to use to find services.
     *
     * @param registryClient The {@link RegistryClient} responsible for looking up services to connect to
     * @return this builder
     */
    ClientBuilder registryClient(RegistryClient registryClient);

    /**
     * The given {@link Supplier} will be used to attach headers to <em>all</em> requests that
     * the built {@link RegistryAwareClient} instance sends.
     *
     * @param headersSupplier a {@link Supplier} that creates a header map
     * @return this builder
     */
    ClientBuilder headersSupplier(Supplier<Map<String, Object>> headersSupplier);

    /**
     * Builds the {@link RegistryAwareClient} with the configured options.
     *
     * @return a {@link RegistryAwareClient}
     */
    RegistryAwareClient build();

}
