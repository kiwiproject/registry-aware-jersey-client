package org.kiwiproject.jersey.client;

import org.kiwiproject.config.provider.TlsConfigProvider;
import org.kiwiproject.registry.client.RegistryClient;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

/**
 * Configure and create {@link RegistryAwareClient} instances using a fluent builder interface. Use {@link ClientBuilders}
 * to get a builder instance.
 * <p>
 * Inspired by {@link javax.ws.rs.client.ClientBuilder}
 */
public interface ClientBuilder {

    /**
     * Enables the multipart feature on the client
     * @return this builder
     */
    ClientBuilder multipart();

    /**
     * Sets the connect timeout for the client
     *
     * @param millis length of timeout in millis
     * @return this builder
     */
    ClientBuilder connectTimeout(int millis);

    /**
     * Sets the connect timeout for the client
     *
     * @param millis length of timeout in millis
     * @return this builder
     */
    ClientBuilder connectTimeout(long millis);

    /**
     * Sets the read timeout for the client
     *
     * @param millis length of timeout in millis
     * @return this builder
     */
    ClientBuilder readTimeout(int millis);

    /**
     * Sets the read timeout for the client
     *
     * @param millis length of timeout in millis
     * @return this builder
     */
    ClientBuilder readTimeout(long millis);

    /**
     * Sets the hostname verifier for the client
     *
     * @param hostnameVerifier The {@link HostnameVerifier} to use
     * @return this builder
     */
    ClientBuilder hostnameVerifier(HostnameVerifier hostnameVerifier);

    /**
     * Sets the SSL context for the client
     *
     * @param sslContext The {@link SSLContext} to use
     * @return this builder
     */
    ClientBuilder sslContext(SSLContext sslContext);

    /**
     * Provides a {@link TlsConfigProvider} to use to provide TLS configuration
     *
     * @param tlsConfigProvider The config provider to provide TLS settings
     * @return this builder
     */
    ClientBuilder tlsConfigProvider(TlsConfigProvider tlsConfigProvider);

    /**
     * Provides the {@link RegistryClient} to use to find services
     *
     * @param registryClient The {@link RegistryClient} responsible for looking up services to connect to
     * @return this builder
     */
    ClientBuilder registryClient(RegistryClient registryClient);

    /**
     * Builds the {@link RegistryAwareClient} with the configured options
     *
     * @return a {@link RegistryAwareClient}
     */
    RegistryAwareClient build();

}
