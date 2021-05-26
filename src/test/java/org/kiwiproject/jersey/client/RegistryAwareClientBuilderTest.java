package org.kiwiproject.jersey.client;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.kiwiproject.jersey.client.util.JerseyTestHelpers.isFeatureRegistered;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.kiwiproject.registry.NoOpRegistryClient;
import org.kiwiproject.registry.client.RegistryClient;
import org.kiwiproject.security.SSLContextException;
import org.kiwiproject.test.junit.jupiter.ClearBoxTest;
import org.kiwiproject.test.util.Fixtures;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.util.Map;

@DisplayName("RegistryAwareClientBuilder")
class RegistryAwareClientBuilderTest {

    private RegistryAwareClientBuilder builder;
    private RegistryAwareClient client;
    private RegistryClient registryClient;

    @BeforeEach
    void setUp() {
        builder = new RegistryAwareClientBuilder();
        registryClient = new NoOpRegistryClient();
    }

    @AfterEach
    void tearDown() {
        if (nonNull(client)) {
            client.close();
        }
    }

    @Test
    void shouldRequireRegistryClient() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> builder.build())
                .withMessage("registryClient must not be null");
    }

    @Test
    void shouldSetDefaultTimeoutsIfNotConfigured() {
        client = builder.registryClient(registryClient).build();

        assertThat(client.getConfiguration().getProperties())
                .contains(
                        entry(ClientProperties.CONNECT_TIMEOUT, 5_000),
                        entry(ClientProperties.READ_TIMEOUT, 5_000)
                );
    }

    @Test
    void shouldHonorConfiguredTimeouts() {
        client = builder
                .registryClient(registryClient)
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
                .registryClient(registryClient)
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
                .registryClient(registryClient)
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

        client = builder.registryClient(registryClient).hostnameVerifier(verifier).build();
        assertThat(client.getHostnameVerifier()).isSameAs(verifier);
    }

    @Test
    void shouldSetNoopHostnameVerifierIfNotConfigured() {
        client = builder.registryClient(registryClient).build();

        assertThat(client.getHostnameVerifier()).isInstanceOf(NoopHostnameVerifier.class);
    }

    @Test
    void shouldSetSSLContextIfNotConfigured() {
        client = builder.registryClient(registryClient).build();

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

        client = builder.registryClient(registryClient).sslContext(sslContext).build();

        assertThat(client.getSslContext()).isSameAs(sslContext);
    }

    @Test
    void shouldBuildContextFromTlsConfigProviderIfGiven() {
        var path = getUnitTestKeyStorePath();
        var provider = TlsConfigProvider.builder()
                .trustStorePathResolverStrategy(FieldResolverStrategy.<String>builder().explicitValue(path).build())
                .trustStorePasswordResolverStrategy(FieldResolverStrategy.<String>builder().explicitValue("password").build())
                .build();

        client = builder.registryClient(registryClient).tlsConfigProvider(provider).build();

        assertThat(client.getSslContext()).isNotNull();
    }

    @ClearBoxTest
    void shouldNotThrowException_WhenTlsConfigProvider_ThrowsExceptionConvertingToSSLContext() {
        var tlsConfig = mock(TlsContextConfiguration.class);
        when(tlsConfig.toSSLContext()).thenThrow(new SSLContextException("Error creating SSLContext"));

        var tlsConfigProvider = mock(TlsConfigProvider.class);
        when(tlsConfigProvider.canProvide()).thenReturn(true);
        when(tlsConfigProvider.getTlsContextConfiguration()).thenReturn(tlsConfig);

        assertThatCode(() -> builder.registryClient(registryClient).tlsConfigProvider(tlsConfigProvider).build())
                .doesNotThrowAnyException();
        verify(tlsConfig).toSSLContext();

        var client = builder.tlsConfigProvider(tlsConfigProvider).build();
        assertThat(client.getSslContext())
                .describedAs("Should still have a non-null, default SSLContext")
                .isNotNull();
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
        client = builder.registryClient(registryClient).multipart().build();

        assertThat(isFeatureRegistered(client, MultiPartFeature.class)).isTrue();
    }

    @Test
    void shouldNotRegisterHeadersSupplierWhenNull() {
        client = builder.registryClient(registryClient).build();

        assertThat(isFeatureRegistered(client, AddHeadersOnRequestFilter.class)).isFalse();
    }

    @Test
    void shouldRegisterHeadersSupplierWhenNonNull() {
        client = builder
                .registryClient(registryClient)
                .headersSupplier(() -> Map.of("X-Custom-Value", "Foo-42"))
                .build();

        assertThat(isFeatureRegistered(client, AddHeadersOnRequestFilter.class)).isTrue();
    }

}
