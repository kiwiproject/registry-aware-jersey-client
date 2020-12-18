package org.kiwiproject.jersey.client;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;

import io.dropwizard.util.Duration;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kiwiproject.config.TlsContextConfiguration;
import org.kiwiproject.config.provider.FieldResolverStrategy;
import org.kiwiproject.config.provider.TlsConfigProvider;
import org.kiwiproject.jersey.client.RegistryAwareClient.AddHeadersOnRequestFilter;
import org.kiwiproject.registry.client.RegistryClient;
import org.kiwiproject.test.util.Fixtures;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.util.Map;

@DisplayName("RegistryAwareClientBuilder")
class RegistryAwareClientBuilderTest {

    private RegistryAwareClientBuilder builder;
    private RegistryAwareClient client;

    @BeforeEach
    void setUp() {
        builder = new RegistryAwareClientBuilder();
    }

    @AfterEach
    void tearDown() {
        if (nonNull(client)) {
            client.close();
        }
    }

    @Test
    void shouldSetDefaultTimeoutsIfNotConfigured() {
        client = builder.build();

        assertThat(client.getConfiguration().getProperties())
                .contains(
                        entry(ClientProperties.CONNECT_TIMEOUT, 5_000),
                        entry(ClientProperties.READ_TIMEOUT, 5_000)
                );
    }

    @Test
    void shouldHonorConfiguredTimeouts() {
        client = builder
                .connectTimeout(1_000)
                .readTimeout(2_000)
                .build();

        assertThat(client.getConfiguration().getProperties())
                .contains(
                        entry(ClientProperties.CONNECT_TIMEOUT, 1_000),
                        entry(ClientProperties.READ_TIMEOUT, 2_000)
                );
    }

    @Test
    void shouldSetTimeoutsFromServiceIdentifier() {
        var serviceId = ServiceIdentifier.builder()
                .serviceName("test-service")
                .connectTimeout(Duration.seconds(2))
                .readTimeout(Duration.seconds(3))
                .build();

        client = builder
                .timeoutsFrom(serviceId)
                .hostnameVerifier(new NoopHostnameVerifier())
                .build();

        assertThat(client.getConfiguration().getProperties())
                .contains(
                        entry(ClientProperties.CONNECT_TIMEOUT, 2_000),
                        entry(ClientProperties.READ_TIMEOUT, 3_000)
                );
    }

    @Test
    void shouldBuildWithLongValuedTimeoutsSetToMaxIntegerValue_EvenThoughThisWouldBeDumbInPractice() {
        assertThatCode(() -> builder
                .connectTimeout((long) Integer.MAX_VALUE)
                .readTimeout((long) Integer.MAX_VALUE)
                .build())
                .doesNotThrowAnyException();
    }

    @Test
    void shouldThrowExceptionIfLongConnectTimeoutValuesExceedMaxIntegerValue() {
        assertThatThrownBy(() -> builder.connectTimeout(Integer.MAX_VALUE + 1L))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void shouldThrowExceptionIfLongReadTimeoutValuesExceedMaxIntegerValue() {
        assertThatThrownBy(() -> builder.readTimeout(Integer.MAX_VALUE + 1L))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void shouldAcceptGivenHostnameVerifier() {
        var verifier = new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };

        client = builder.hostnameVerifier(verifier).build();
        assertThat(client.getHostnameVerifier()).isSameAs(verifier);
    }

    @Test
    void shouldSetNoopHostnameVerifierIfNotConfigured() {
        client = builder.build();

        assertThat(client.getHostnameVerifier()).isInstanceOf(NoopHostnameVerifier.class);
    }

    @Test
    void shouldSetSSLContextIfNotConfigured() {
        client = builder.build();

        assertThat(client.getSslContext()).isNotNull();
    }

    @Test
    void shouldAcceptGivenSSLContext() {
        var path = getUnitTestKeyStorePath();
        var sslContext = TlsContextConfiguration.builder()
                .trustStorePath(path)
                .trustStorePassword("password")
                .build()
                .toSSLContext();

        client = builder.sslContext(sslContext).build();

        assertThat(client.getSslContext()).isSameAs(sslContext);
    }

    @Test
    void shouldBuildContextFromTlsConfigProviderIfGiven() {
        var path = getUnitTestKeyStorePath();
        var provider = TlsConfigProvider.builder()
                .trustStorePathResolverStrategy(FieldResolverStrategy.<String>builder().explicitValue(path).build())
                .trustStorePasswordResolverStrategy(FieldResolverStrategy.<String>builder().explicitValue("password").build())
                .build();

        client = builder.tlsConfigProvider(provider).build();

        assertThat(client.getSslContext()).isNotNull();
    }

    private static String getUnitTestKeyStorePath() {
        return Fixtures.fixturePath("RegistryAwareClientBuilderTest/unitteststore.jks").toAbsolutePath().toString();
    }

    @Test
    void shouldAcceptGivenRegistryClient() {
        var registryClient = mock(RegistryClient.class);

        client = builder.registryClient(registryClient).build();

        assertThat(client.getRegistryClient()).isSameAs(registryClient);
    }

    @Test
    void shouldRegisterMultipartFeatureWhenRequested() {
        client = builder.multipart().build();

        assertThat(isFeatureRegistered(client, MultiPartFeature.class)).isTrue();
    }

    @Test
    void shouldNotRegisterHeadersSupplierWhenNull() {
        client = builder.build();

        assertThat(isFeatureRegistered(client, AddHeadersOnRequestFilter.class)).isFalse();
    }

    @Test
    void shouldRegisterHeadersSupplierWhenNonNull() {
        client = builder.headersSupplier(() -> Map.of("X-Custom-Value", "Foo-42")).build();

        assertThat(isFeatureRegistered(client, AddHeadersOnRequestFilter.class)).isTrue();
    }

    private static boolean isFeatureRegistered(RegistryAwareClient client, Class<?> component) {
        return client.getConfiguration().isRegistered(component);
    }
}
