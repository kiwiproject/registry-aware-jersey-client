package org.kiwiproject.jersey.client.dropwizard;

import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.kiwiproject.base.KiwiStrings.splitOnCommas;
import static org.kiwiproject.jersey.client.util.JerseyTestHelpers.isFeatureRegisteredByClass;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertOkResponse;
import static org.mockito.Mockito.mock;

import com.codahale.metrics.NoopMetricRegistry;
import com.codahale.metrics.jersey3.MetricsFeature;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.client.ssl.TlsConfiguration;
import io.dropwizard.testing.junit5.DropwizardClientExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.logging.LoggingFeature;
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
import org.kiwiproject.jersey.client.filter.AddHeadersClientRequestFilter;
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

        @GET
        @Path("/echo-multi-valued-headers")
        public Response verifyMultivaluedHeadersWereSent(@Context HttpHeaders httpHeaders) {
            // This is necessary because Jersey provides multivalued headers as
            // a comma-separated list of values.
            MultivaluedMap<String, String> requestHeaders = httpHeaders.getRequestHeaders();
            Map<String, List<String>> headers = requestHeaders.entrySet()
                    .stream()
                    .collect(toMap(Map.Entry::getKey, TestResource::flattenHeaders));
            return Response.ok(headers).build();
        }

        private static List<String> flattenHeaders(Map.Entry<String, List<String>> entry) {
            return entry.getValue().stream()
                    .flatMap(val -> splitOnCommas(val).stream())
                    .toList();
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
            assertThat(isFeatureRegisteredByClass(client, AddHeadersClientRequestFilter.class)).isFalse();
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

            assertThat(isFeatureRegisteredByClass(client, AddHeadersClientRequestFilter.class)).isTrue();

            var entity = makeEchoHeadersRequest(client);
            assertThat(entity).contains(
                    entry("Header-1", List.of("Value-1")),
                    entry("Header-2", List.of("Value-2")),
                    entry("Header-3", List.of("Value-3"))
            );
        }

        @Test
        void shouldUseGivenHeadersMultivalueSupplier() {
            client = new DropwizardManagedClientBuilder()
                    .clientName(CLIENT_NAME)
                    .environment(clientExtension.getEnvironment())
                    .headersMultivalueSupplier(() -> {
                        var headers = new MultivaluedHashMap<String, Object>();
                        headers.putSingle("Header-1", "Value-1");
                        headers.addAll("Header-2", List.of("Value-2a", "Value-2b"));
                        headers.addAll("Header-3", List.of("Value-3a", "Value-3b", "Value-3c"));
                        return headers;
                    })
                    .buildManagedJerseyClient();

            assertThat(isFeatureRegisteredByClass(client, AddHeadersClientRequestFilter.class)).isTrue();

            var entity = makeEchoMultivaluedHeadersRequest(client);
            assertThat(entity).contains(
                    entry("Header-1", List.of("Value-1")),
                    entry("Header-2", List.of("Value-2a", "Value-2b")),
                    entry("Header-3", List.of("Value-3a", "Value-3b", "Value-3c"))
            );
        }

        @Test
        void shouldAddProperties() {
            client = new DropwizardManagedClientBuilder()
                    .clientName(CLIENT_NAME)
                    .environment(clientExtension.getEnvironment())
                    .property(ClientProperties.CONNECT_TIMEOUT, 500)
                    .property(ClientProperties.READ_TIMEOUT, 750)
                    .property(ClientProperties.FOLLOW_REDIRECTS, false)
                    .property(ClientProperties.REQUEST_ENTITY_PROCESSING, "BUFFERED")
                    .property(ClientProperties.CHUNKED_ENCODING_SIZE, 8_192)
                    .buildManagedJerseyClient();

            assertThat(client).isInstanceOf(Client.class);

            var configuration = client.getConfiguration();
            assertAll(
                    () -> assertThat(configuration.getProperty(ClientProperties.CONNECT_TIMEOUT)).isEqualTo(500),
                    () -> assertThat(configuration.getProperty(ClientProperties.READ_TIMEOUT)).isEqualTo(750),
                    () -> assertThat(configuration.getProperty(ClientProperties.FOLLOW_REDIRECTS)).isEqualTo(false),
                    () -> assertThat(configuration.getProperty(ClientProperties.REQUEST_ENTITY_PROCESSING)).isEqualTo("BUFFERED"),
                    () -> assertThat(configuration.getProperty(ClientProperties.CHUNKED_ENCODING_SIZE)).isEqualTo(8_192)
            );
        }

        @Test
        void shouldRegisterComponentClasses() {
            client = new DropwizardManagedClientBuilder()
                    .clientName(CLIENT_NAME)
                    .environment(clientExtension.getEnvironment())
                    .registerComponentClass(MetricsFeature.class)
                    .registerComponentClass(LoggingFeature.class)
                    .buildManagedJerseyClient();

            assertThat(client).isInstanceOf(Client.class);

            var configuration = client.getConfiguration();
            assertThat(configuration.getClasses()).contains(MetricsFeature.class, LoggingFeature.class);
        }

        @Test
        void shouldRegisterComponents() {
            var metricsFeature = new MetricsFeature(new NoopMetricRegistry());
            var loggingFeature = new LoggingFeature();
            client = new DropwizardManagedClientBuilder()
                    .clientName(CLIENT_NAME)
                    .environment(clientExtension.getEnvironment())
                    .registerComponent(metricsFeature)
                    .registerComponent(loggingFeature)
                    .buildManagedJerseyClient();

            assertThat(client).isInstanceOf(Client.class);

            var configuration = client.getConfiguration();
            assertThat(configuration.getInstances()).contains(metricsFeature, loggingFeature);
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
            assertThat(isFeatureRegisteredByClass(client, AddHeadersClientRequestFilter.class)).isFalse();
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

            assertThat(isFeatureRegisteredByClass(client, AddHeadersClientRequestFilter.class)).isTrue();

            var entity = makeEchoHeadersRequest(client);
            assertThat(entity).contains(
                    entry("Header-A", List.of("Value-A")),
                    entry("Header-B", List.of("Value-B"))
            );
        }

        @Test
        void shouldUseGivenHeadersMultivalueSupplier() {
            client = new DropwizardManagedClientBuilder()
                    .clientName(CLIENT_NAME)
                    .environment(clientExtension.getEnvironment())
                    .registryClient(mock(RegistryClient.class))
                    .headersMultivalueSupplier(() -> {
                        var headers = new MultivaluedHashMap<String, Object>();
                        headers.putSingle("Header-1", "Value-1");
                        headers.addAll("Header-2", List.of("Value-2a", "Value-2b"));
                        headers.addAll("Header-3", List.of("Value-3a", "Value-3b", "Value-3c"));
                        return headers;
                    })
                    .buildManagedRegistryAwareClient();

            assertThat(isFeatureRegisteredByClass(client, AddHeadersClientRequestFilter.class)).isTrue();

            var entity = makeEchoMultivaluedHeadersRequest(client);
            assertThat(entity).contains(
                    entry("Header-1", List.of("Value-1")),
                    entry("Header-2", List.of("Value-2a", "Value-2b")),
                    entry("Header-3", List.of("Value-3a", "Value-3b", "Value-3c"))
            );
        }
    }

    private Map<String, List<String>> makeEchoHeadersRequest(Client client) {
        return makeEchoHeadersRequest(client, "/echo-headers");
    }

    private Map<String, List<String>> makeEchoMultivaluedHeadersRequest(Client client) {
        return makeEchoHeadersRequest(client, "/echo-multi-valued-headers");
    }

    private Map<String, List<String>> makeEchoHeadersRequest(Client client, String path) {
        var response = client.target(baseUri)
                .path(path)
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
                    () -> assertThat(config.getTlsConfiguration())
                            .isNotNull()
                            .extracting(TlsConfiguration::getTrustStorePath, TlsConfiguration::getTrustStorePassword)
                            .containsExactly(new File(keystorePath), keystorePassword)
            );
        }
    }

    private static String getUnitTestKeyStorePath() {
        return Fixtures.fixturePath("RegistryAwareClientBuilderTest/unitteststore.jks").toAbsolutePath().toString();
    }
}
