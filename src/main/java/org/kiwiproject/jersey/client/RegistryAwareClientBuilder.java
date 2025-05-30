package org.kiwiproject.jersey.client;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.nonNull;

import jakarta.ws.rs.core.MultivaluedMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.kiwiproject.config.provider.TlsConfigProvider;
import org.kiwiproject.jersey.client.filter.AddHeadersClientRequestFilter;
import org.kiwiproject.registry.client.RegistryClient;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;

/**
 * This class builds {@link RegistryAwareClient} instances using regular JAX-RS APIs.
 */
@Slf4j
public class RegistryAwareClientBuilder implements ClientBuilder {

    private static final String DEFAULT_TLS_INFO_MESSAGE = "No SSLContext provided; if a connection is made" +
            " via HTTPS, this client will use system default TLS via SSLConnectionSocketFactory.getSocketFactory()";

    private final JerseyClientBuilder jerseyClientBuilder = new JerseyClientBuilder();

    private boolean sslContextWasSetOnThis;
    private boolean hostnameVerifierWasSetOnThis;
    private RegistryClient registryClient;
    private Supplier<Map<String, Object>> headersSupplier;
    private Supplier<MultivaluedMap<String, Object>> headersMultivalueSupplier;

    @Override
    public ClientBuilder multipart() {
        jerseyClientBuilder.register(MultiPartFeature.class);
        return this;
    }

    @Override
    public ClientBuilder connectTimeout(int millis) {
        jerseyClientBuilder.property(ClientProperties.CONNECT_TIMEOUT, millis);
        return this;
    }

    @Override
    public ClientBuilder connectTimeout(long millis) {
        return connectTimeout(Math.toIntExact(millis));
    }

    @Override
    public ClientBuilder connectTimeout(Duration timeout) {
        return connectTimeout(timeout.toMillis());
    }

    @Override
    public ClientBuilder readTimeout(int millis) {
        jerseyClientBuilder.property(ClientProperties.READ_TIMEOUT, millis);
        return this;
    }

    @Override
    public ClientBuilder readTimeout(long millis) {
        return readTimeout(Math.toIntExact(millis));
    }

    @Override
    public ClientBuilder readTimeout(Duration timeout) {
        return readTimeout(timeout.toMillis());
    }

    @Override
    public ClientBuilder timeoutsFrom(ServiceIdentifier serviceIdentifier) {
        connectTimeout(serviceIdentifier.getConnectTimeout().toMilliseconds());
        readTimeout(serviceIdentifier.getReadTimeout().toMilliseconds());
        return this;
    }

    @Override
    public ClientBuilder hostnameVerifier(HostnameVerifier hostnameVerifier) {
        jerseyClientBuilder.hostnameVerifier(hostnameVerifier);
        hostnameVerifierWasSetOnThis = true;
        return this;
    }

    @Override
    public ClientBuilder sslContext(SSLContext sslContext) {
        jerseyClientBuilder.sslContext(sslContext);

        sslContextWasSetOnThis = true;

        return this;
    }

    @Override
    public ClientBuilder registryClient(RegistryClient registryClient) {
        this.registryClient = registryClient;
        return this;
    }

    @Override
    public ClientBuilder tlsConfigProvider(TlsConfigProvider tlsConfigProvider) {
        checkState(tlsConfigProvider.canProvide(), "TlsConfigProvider is unable to provide TLS properties");

        var tlsContextConfig = tlsConfigProvider.getTlsContextConfiguration();

        try {
            return sslContext(tlsContextConfig.toSSLContext());
        } catch (Exception e) {
            LOG.warn("Unable to convert TlsContextConfiguration to SSLContext: {}: {}",
                    e.getClass().getName(), e.getMessage());
            LOG.debug("TlsContextConfiguration conversion exception: ", e);
        }

        return this;
    }

    @Override
    public ClientBuilder property(String name, Object value) {
        jerseyClientBuilder.property(name, value);
        return this;
    }

    @Override
    public ClientBuilder registerComponentClass(Class<?> componentClass) {
        jerseyClientBuilder.register(componentClass);
        return this;
    }

    @Override
    public ClientBuilder registerComponent(Object component) {
        jerseyClientBuilder.register(component);
        return this;
    }

    @Override
    public ClientBuilder headersSupplier(Supplier<Map<String, Object>> headersSupplier) {
        this.headersSupplier = headersSupplier;
        return this;
    }

    @Override
    public ClientBuilder headersMultivaluedSupplier(Supplier<MultivaluedMap<String, Object>> headersMultivalueSupplier) {
        this.headersMultivalueSupplier = headersMultivalueSupplier;
        return this;
    }

    @Override
    public RegistryAwareClient build() {
        var configPropertyNames = jerseyClientBuilder.getConfiguration().getPropertyNames();
        setConnectTimeoutIfNotConfigured(configPropertyNames);
        setReadTimeoutIfNotConfigured(configPropertyNames);
        setNoopHostNameVerifierIfNotSet();

        if (!sslContextWasSetOnThis) {
            LOG.info(DEFAULT_TLS_INFO_MESSAGE);
        }

        var client = jerseyClientBuilder.build();

        if (nonNull(headersSupplier)) {
            client.register(AddHeadersClientRequestFilter.fromMapSupplier(headersSupplier));
        } else if (nonNull(headersMultivalueSupplier)) {
            client.register(AddHeadersClientRequestFilter.fromMultivaluedMapSupplier(headersMultivalueSupplier));
        }

        return new RegistryAwareClient(client, registryClient);
    }

    private void setConnectTimeoutIfNotConfigured(Collection<String> configPropertyNames) {
        if (!configPropertyNames.contains(ClientProperties.CONNECT_TIMEOUT)) {
            LOG.trace("Connect timeout not configured; setting global default to {}ms",
                    RegistryAwareClientConstants.DEFAULT_CONNECT_TIMEOUT_MILLIS);

            connectTimeout(RegistryAwareClientConstants.DEFAULT_CONNECT_TIMEOUT_MILLIS);
        }
    }

    private void setReadTimeoutIfNotConfigured(Collection<String> configPropertyNames) {
        if (!configPropertyNames.contains(ClientProperties.READ_TIMEOUT)) {
            LOG.trace("Read timeout not configured; setting global default to {}ms",
                    RegistryAwareClientConstants.DEFAULT_READ_TIMEOUT_MILLIS);

            readTimeout(RegistryAwareClientConstants.DEFAULT_READ_TIMEOUT_MILLIS);
        }
    }

    private void setNoopHostNameVerifierIfNotSet() {
        if (!hostnameVerifierWasSetOnThis) {
            LOG.warn("A hostname verifier has not been configured. Setting the hostname verifier to NoopHostnameVerifier.");
            jerseyClientBuilder.hostnameVerifier(new NoopHostnameVerifier());
        }
    }
}
