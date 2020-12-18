package org.kiwiproject.jersey.client.dropwizard;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.testing.junit5.DropwizardClientExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kiwiproject.config.TlsContextConfiguration;
import org.kiwiproject.config.provider.FieldResolverStrategy;
import org.kiwiproject.config.provider.TlsConfigProvider;
import org.kiwiproject.jersey.client.RegistryAwareClient;
import org.kiwiproject.jersey.client.RegistryAwareClientConstants;
import org.kiwiproject.registry.client.RegistryClient;
import org.kiwiproject.test.util.Fixtures;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;
import java.io.File;

@DisplayName("DropwizardManagedClientBuilder")
@ExtendWith(DropwizardExtensionsSupport.class)
@ExtendWith(SoftAssertionsExtension.class)
class DropwizardManagedClientBuilderTest {

    @Path("/test")
    @Produces("text/plain")
    public static class TestResource {

        @GET
        public Response get() {
            return Response.ok("ok").build();
        }
    }

    private final DropwizardClientExtension CLIENT_EXTENSION = new DropwizardClientExtension(new TestResource());

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
                    .environment(CLIENT_EXTENSION.getEnvironment())
                    .jerseyClientConfiguration(config)
                    .buildManagedJerseyClient();

            assertThat(client).isInstanceOf(Client.class);
        }

        @Test
        void shouldSetupDefaultJerseyClientConfigurationIfNotGiven_IgnoringTLSIfOptedOut() {
            client = new DropwizardManagedClientBuilder()
                    .clientName(CLIENT_NAME)
                    .environment(CLIENT_EXTENSION.getEnvironment())
                    .withoutTls()
                    .buildManagedJerseyClient();

            assertThat(client).isInstanceOf(Client.class);
        }

        @Test
        void shouldSetupDefaultJerseyClientConfigurationIfNotGiven_IgnoringTLSIfConfigProviderNotGiven() {
            client = new DropwizardManagedClientBuilder()
                    .clientName(CLIENT_NAME)
                    .environment(CLIENT_EXTENSION.getEnvironment())
                    .buildManagedJerseyClient();

            assertThat(client).isInstanceOf(Client.class);
        }

        @Test
        void shouldSetupDefaultJerseyClientConfigurationIfNotGiven_IgnoringTLSIfConfigProviderCannotProvide() {
            client = new DropwizardManagedClientBuilder()
                    .clientName(CLIENT_NAME)
                    .environment(CLIENT_EXTENSION.getEnvironment())
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
                    .environment(CLIENT_EXTENSION.getEnvironment())
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
                                    .environment(CLIENT_EXTENSION.getEnvironment())
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
                    .environment(CLIENT_EXTENSION.getEnvironment())
                    .tlsContextConfiguration(tlsConfig)
                    .buildManagedJerseyClient();

            assertThat(client).isInstanceOf(Client.class);
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
                    .environment(CLIENT_EXTENSION.getEnvironment());

            assertThatThrownBy(builder::buildManagedRegistryAwareClient)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Registry Client is required for a Registry Aware Client to be created");
        }

        @Test
        void shouldBuildRegistryAwareClient() {
            client = new DropwizardManagedClientBuilder()
                    .clientName(CLIENT_NAME)
                    .environment(CLIENT_EXTENSION.getEnvironment())
                    .registryClient(mock(RegistryClient.class))
                    .buildManagedRegistryAwareClient();

            assertThat(client).isInstanceOf(RegistryAwareClient.class);
        }
    }

    @Nested
    class NewDefaultJerseyClientConfiguration {

        @Test
        void shouldReturnNewJerseyClientConfiguration_WithoutTLS_WhenProviderNotProvided(SoftAssertions softly) {
            var config = DropwizardManagedClientBuilder.newDefaultJerseyClientConfiguration();

            softly.assertThat(config.getConnectionRequestTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_CONNECTION_POOL_TIMEOUT);
            softly.assertThat(config.getConnectionTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_CONNECT_TIMEOUT);
            softly.assertThat(config.getTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_READ_TIMEOUT);
            softly.assertThat(config.getTlsConfiguration()).isNull();
        }

        @Test
        void shouldReturnNewJerseyClientConfiguration_WithoutTLS_WhenProviderIsNull(SoftAssertions softly) {
            var config = DropwizardManagedClientBuilder.newDefaultJerseyClientConfiguration(null);

            softly.assertThat(config.getConnectionRequestTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_CONNECTION_POOL_TIMEOUT);
            softly.assertThat(config.getConnectionTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_CONNECT_TIMEOUT);
            softly.assertThat(config.getTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_READ_TIMEOUT);
            softly.assertThat(config.getTlsConfiguration()).isNull();
        }

        @Test
        void shouldReturnNewJerseyClientConfiguration_WithoutTLS_WhenProviderCannotProvide(SoftAssertions softly) {
            var provider = TlsConfigProvider.builder().build();

            var config = DropwizardManagedClientBuilder.newDefaultJerseyClientConfiguration(provider);

            softly.assertThat(config.getConnectionRequestTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_CONNECTION_POOL_TIMEOUT);
            softly.assertThat(config.getConnectionTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_CONNECT_TIMEOUT);
            softly.assertThat(config.getTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_READ_TIMEOUT);
            softly.assertThat(config.getTlsConfiguration()).isNull();
        }

        @Test
        void shouldReturnNewJerseyClientConfiguration_WithTls_WhenProviderCanProvide(SoftAssertions softly) {
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

            softly.assertThat(config.getConnectionRequestTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_CONNECTION_POOL_TIMEOUT);
            softly.assertThat(config.getConnectionTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_CONNECT_TIMEOUT);
            softly.assertThat(config.getTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_READ_TIMEOUT);
            softly.assertThat(config.getTlsConfiguration()).isNotNull();
            softly.assertThat(config.getTlsConfiguration().getTrustStorePath()).isEqualTo(new File(keystorePath));
            softly.assertThat(config.getTlsConfiguration().getTrustStorePassword()).isEqualTo(keystorePassword);
        }
    }

    private static String getUnitTestKeyStorePath() {
        return Fixtures.fixturePath("RegistryAwareClientBuilderTest/unitteststore.jks").toAbsolutePath().toString();
    }
}
