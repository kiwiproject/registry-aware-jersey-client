package org.kiwiproject.jersey.client.dropwizard;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.kiwiproject.jersey.client.util.JerseyTestHelpers.isFeatureRegisteredByClass;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertOkResponse;
import static org.mockito.Mockito.mock;

import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.testing.junit5.DropwizardClientExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kiwiproject.config.TlsContextConfiguration;
import org.kiwiproject.config.provider.FieldResolverStrategy;
import org.kiwiproject.config.provider.TlsConfigProvider;
import org.kiwiproject.jersey.client.RegistryAwareClient;
import org.kiwiproject.jersey.client.RegistryAwareClientConstants;
import org.kiwiproject.jersey.client.dropwizard.DropwizardManagedClientBuilder.AddHeadersOnRequestFilter;
import org.kiwiproject.registry.client.RegistryClient;
import org.kiwiproject.test.util.Fixtures;

import java.io.File;
import java.util.List;
import java.util.Map;

@DisplayName("DropwizardManagedClientBuilder")
@ExtendWith(DropwizardExtensionsSupport.class)
class DropwizardManagedClientBuilderTest {

    private static final GenericType<Map<String, List<String>>> MAP_OF_STRING_TO_LIST_OF_STRING_TYPE =
            new GenericType<>() {
            };

    @Path("/test")
    public static class TestResource {

        @GET
        @Produces("text/plain")
        public Response get() {
            return Response.ok("ok").build();
        }

        @GET
        @Path("/echo-headers")
        @Produces("application/json")
        public Response verifyHeadersWereSent(@Context HttpHeaders httpHeaders) {
            return Response.ok(httpHeaders.getRequestHeaders()).build();
        }
    }

    private String baseUri;

    //
    // This field cannot be static. Making it static results in an java.lang.IllegalArgumentException in the tests
    // which call buildManagedJerseyClient. It works the first time it is called, but all tests that call it
    // afterward fail with the message:
    //
    // "A metric named org.apache.hc.client5.http.io.HttpClientConnectionManager.jersey-client.available-connections already exists"
    //
    // This is because, if the field is static, the MetricRegistry has already been created. Therefore, we need
    // a fresh instance for each test, even though this slows the test down.
    //
    private final DropwizardClientExtension clientExtension = new DropwizardClientExtension(new TestResource());

    @BeforeEach
    void setUp() {
        baseUri = clientExtension.baseUri().toString() + "/test";
    }

    @Nested
    class BuildManagedJerseyClient {

        private static final String CLIENT_NAME = "jersey-client";

        private Client client;

        @AfterEach
        void tearDown() {
            if (nonNull(client)) {
                client.close();
            }
        }

        @Test
        void shouldThrowIllegalStateExceptionIfClientNameNotSet() {
            var builder = new DropwizardManagedClientBuilder();

            assertThatThrownBy(builder::buildManagedJerseyClient)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("A name for the managed client must be specified");
        }

        @Test
        void shouldThrowIllegalStateExceptionIfEnvironmentIsNotSet() {
            var builder = new DropwizardManagedClientBuilder().clientName(CLIENT_NAME);

            assertThatThrownBy(builder::buildManagedJerseyClient)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Dropwizard environment must be provided to create managed client");
        }

        @Test
        void shouldUseGivenJerseyClientConfiguration() {
            var config = new JerseyClientConfiguration();

            client = new DropwizardManagedClientBuilder()
                    .clientName(CLIENT_NAME)
                    .environment(clientExtension.getEnvironment())
                    .jerseyClientConfiguration(config)
                    .buildManagedJerseyClient();

            assertThat(client).isInstanceOf(Client.class);
            assertThat(isFeatureRegisteredByClass(client, AddHeadersOnRequestFilter.class)).isFalse();
        }

        @Test
        void shouldSetupDefaultJerseyClientConfigurationIfNotGiven_IgnoringTLSIfOptedOut() {
            client = new DropwizardManagedClientBuilder()
                    .clientName(CLIENT_NAME)
                    .environment(clientExtension.getEnvironment())
                    .withoutTls()
                    .buildManagedJerseyClient();

            assertThat(client).isInstanceOf(Client.class);
        }

        @Test
        void shouldSetupDefaultJerseyClientConfigurationIfNotGiven_IgnoringTLSIfConfigProviderNotGiven() {
            client = new DropwizardManagedClientBuilder()
                    .clientName(CLIENT_NAME)
                    .environment(clientExtension.getEnvironment())
                    .buildManagedJerseyClient();

            assertThat(client).isInstanceOf(Client.class);
        }

        @Test
        void shouldSetupDefaultJerseyClientConfigurationIfNotGiven_IgnoringTLSIfConfigProviderCannotProvide() {
            client = new DropwizardManagedClientBuilder()
                    .clientName(CLIENT_NAME)
                    .environment(clientExtension.getEnvironment())
                    .tlsConfigProvider(TlsConfigProvider.builder().build())
                    .buildManagedJerseyClient();

            assertThat(client).isInstanceOf(Client.class);
        }

        @Test
        void shouldSetupDefaultJerseyClientConfigurationWithTls() {
            var keyStorePath = getUnitTestKeyStorePath();
            var tlsConfigProvider = TlsConfigProvider.builder()
                    .trustStorePathResolverStrategy(FieldResolverStrategy.<String>builder()
                            .explicitValue(keyStorePath)
                            .build())
                    .trustStorePasswordResolverStrategy(FieldResolverStrategy.<String>builder()
                            .explicitValue("password")
                            .build())
                    .build();

            client = new DropwizardManagedClientBuilder()
                    .clientName(CLIENT_NAME)
                    .environment(clientExtension.getEnvironment())
                    .tlsConfigProvider(tlsConfigProvider)
                    .buildManagedJerseyClient();

            assertThat(client).isInstanceOf(Client.class);
        }

        @Test
        void shouldNotAllowNullTlsContextConfiguration() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() ->
                            new DropwizardManagedClientBuilder()
                                    .clientName(CLIENT_NAME)
                                    .environment(clientExtension.getEnvironment())
                                    .tlsContextConfiguration(null)
                                    .buildManagedJerseyClient())
                    .withMessage("tlsConfig must not be null");
        }

        @Test
        void shouldSetupDefaultJerseyClientConfigurationWithTlsContextConfiguration() {
            var keyStorePath = getUnitTestKeyStorePath();
            var tlsConfig = TlsContextConfiguration.builder()
                    .trustStorePath(keyStorePath)
                    .trustStorePassword("password")
                    .build();

            client = new DropwizardManagedClientBuilder()
                    .clientName(CLIENT_NAME)
                    .environment(clientExtension.getEnvironment())
                    .tlsContextConfiguration(tlsConfig)
                    .buildManagedJerseyClient();

            assertThat(client).isInstanceOf(Client.class);
        }

        @Test
        void shouldUseGivenHeadersSupplier() {
            client = new DropwizardManagedClientBuilder()
                    .clientName(CLIENT_NAME)
                    .environment(clientExtension.getEnvironment())
                    .headersSupplier(() ->
                            Map.of(
                                    "Header-1", "Value-1",
                                    "Header-2", "Value-2",
                                    "Header-3", "Value-3"
                            ))
                    .buildManagedJerseyClient();

            assertThat(isFeatureRegisteredByClass(client, AddHeadersOnRequestFilter.class)).isTrue();

            var entity = makeEchoHeadersRequest(client);
            assertThat(entity).contains(
                    entry("Header-1", List.of("Value-1")),
                    entry("Header-2", List.of("Value-2")),
                    entry("Header-3", List.of("Value-3"))
            );
        }
    }

    @Nested
    class BuildManagedRegistryAwareClient {

        private static final String CLIENT_NAME = "registryAwareClient";

        private RegistryAwareClient client;

        @AfterEach
        void tearDown() {
            if (nonNull(client)) {
                client.close();
            }
        }

        @Test
        void shouldThrowIllegalStateExceptionIfRegistryClientNotSet() {
            var builder = new DropwizardManagedClientBuilder()
                    .clientName(CLIENT_NAME)
                    .environment(clientExtension.getEnvironment());

            assertThatThrownBy(builder::buildManagedRegistryAwareClient)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Registry Client is required for a Registry Aware Client to be created");
        }

        @Test
        void shouldBuildRegistryAwareClient() {
            client = new DropwizardManagedClientBuilder()
                    .clientName(CLIENT_NAME)
                    .environment(clientExtension.getEnvironment())
                    .registryClient(mock(RegistryClient.class))
                    .buildManagedRegistryAwareClient();

            assertThat(client).isInstanceOf(RegistryAwareClient.class);
            assertThat(isFeatureRegisteredByClass(client, AddHeadersOnRequestFilter.class)).isFalse();
        }

        @Test
        void shouldUseGivenHeadersSupplier() {
            client = new DropwizardManagedClientBuilder()
                    .clientName(CLIENT_NAME)
                    .environment(clientExtension.getEnvironment())
                    .registryClient(mock(RegistryClient.class))
                    .headersSupplier(() -> Map.of(
                            "Header-A", "Value-A",
                            "Header-B", "Value-B"
                    ))
                    .buildManagedRegistryAwareClient();

            assertThat(isFeatureRegisteredByClass(client, AddHeadersOnRequestFilter.class)).isTrue();

            var entity = makeEchoHeadersRequest(client);
            assertThat(entity).contains(
                    entry("Header-A", List.of("Value-A")),
                    entry("Header-B", List.of("Value-B"))
            );
        }
    }

    private Map<String, List<String>> makeEchoHeadersRequest(Client client) {
        var response = client.target(baseUri + "/echo-headers")
                .request()
                .get();

        assertOkResponse(response);

        return response.readEntity(MAP_OF_STRING_TO_LIST_OF_STRING_TYPE);
    }

    @Nested
    class NewDefaultJerseyClientConfiguration {

        @Test
        void shouldReturnNewJerseyClientConfiguration_WithoutTLS_WhenProviderNotProvided() {
            var config = DropwizardManagedClientBuilder.newDefaultJerseyClientConfiguration();

            assertAll(
                    () -> assertThat(config.getConnectionRequestTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_CONNECTION_POOL_TIMEOUT),
                    () -> assertThat(config.getConnectionTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_CONNECT_TIMEOUT),
                    () -> assertThat(config.getTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_READ_TIMEOUT),
                    () -> assertThat(config.getTlsConfiguration()).isNull()
            );
        }

        @Test
        void shouldReturnNewJerseyClientConfiguration_WithoutTLS_WhenProviderIsNull() {
            var config = DropwizardManagedClientBuilder.newDefaultJerseyClientConfiguration(null);

            assertAll(
                    () -> assertThat(config.getConnectionRequestTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_CONNECTION_POOL_TIMEOUT),
                    () -> assertThat(config.getConnectionTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_CONNECT_TIMEOUT),
                    () -> assertThat(config.getTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_READ_TIMEOUT),
                    () -> assertThat(config.getTlsConfiguration()).isNull()
            );
        }

        @Test
        void shouldReturnNewJerseyClientConfiguration_WithoutTLS_WhenProviderCannotProvide() {
            var provider = TlsConfigProvider.builder().build();

            var config = DropwizardManagedClientBuilder.newDefaultJerseyClientConfiguration(provider);

            assertAll(
                    () -> assertThat(config.getConnectionRequestTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_CONNECTION_POOL_TIMEOUT),
                    () -> assertThat(config.getConnectionTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_CONNECT_TIMEOUT),
                    () -> assertThat(config.getTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_READ_TIMEOUT),
                    () -> assertThat(config.getTlsConfiguration()).isNull()
            );
        }

        @Test
        void shouldReturnNewJerseyClientConfiguration_WithTls_WhenProviderCanProvide() {
            var keystorePath = getUnitTestKeyStorePath();
            var keystorePassword = "password";

            var tlsConfigProvider = TlsConfigProvider.builder()
                    .trustStorePathResolverStrategy(FieldResolverStrategy.<String>builder()
                            .explicitValue(keystorePath)
                            .build())
                    .trustStorePasswordResolverStrategy(FieldResolverStrategy.<String>builder()
                            .explicitValue(keystorePassword)
                            .build())
                    .build();

            var config = DropwizardManagedClientBuilder.newDefaultJerseyClientConfiguration(tlsConfigProvider);

            assertAll(
                    () -> assertThat(config.getConnectionRequestTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_CONNECTION_POOL_TIMEOUT),
                    () -> assertThat(config.getConnectionTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_CONNECT_TIMEOUT),
                    () -> assertThat(config.getTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_READ_TIMEOUT),
                    () -> assertThat(config.getTlsConfiguration()).isNotNull(),
                    () -> assertThat(config.getTlsConfiguration().getTrustStorePath()).isEqualTo(new File(keystorePath)),
                    () -> assertThat(config.getTlsConfiguration().getTrustStorePassword()).isEqualTo(keystorePassword)
            );
        }
    }

    private static String getUnitTestKeyStorePath() {
        return Fixtures.fixturePath("RegistryAwareClientBuilderTest/unitteststore.jks").toAbsolutePath().toString();
    }
}
