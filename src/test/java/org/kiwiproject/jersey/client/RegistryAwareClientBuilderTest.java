package org.kiwiproject.jersey.client;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import org.glassfish.jersey.client.ClientProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kiwiproject.config.TlsContextConfiguration;
import org.kiwiproject.config.provider.FieldResolverStrategy;
import org.kiwiproject.config.provider.TlsConfigProvider;
import org.kiwiproject.test.util.Fixtures;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

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
        var verifier = new HostnameVerifier(){
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };

        client = builder.hostnameVerifier(verifier).build();
        assertThat(client.getHostnameVerifier()).isSameAs(verifier);
    }

    @Test
    void shouldSetSSLContextIfNotConfigured() {
        client = builder.build();

        assertThat(client.getSslContext()).isNotNull();
    }

    @Test
    void shouldAcceptGivenSSLContext() {
        var path = Fixtures.fixturePath("RegistryAwareClientBuilderTest/unitteststore.jks").toAbsolutePath().toString();
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
        var path = Fixtures.fixturePath("RegistryAwareClientBuilderTest/unitteststore.jks").toAbsolutePath().toString();
        var provider = TlsConfigProvider.builder()
                .trustStorePathResolverStrategy(FieldResolverStrategy.<String>builder().explicitValue(path).build())
                .trustStorePasswordResolverStrategy(FieldResolverStrategy.<String>builder().explicitValue("password").build())
                .build();

        client = builder.tlsConfigProvider(provider).build();

        assertThat(client.getSslContext()).isNotNull();
    }

    // TODO: Add tests for multipart and registryClient
}
