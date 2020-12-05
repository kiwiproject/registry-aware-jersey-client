package org.kiwiproject.jersey.client.dropwizard;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.testing.junit5.DropwizardClientExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

@ExtendWith(DropwizardExtensionsSupport.class)
@DisplayName("DropwizardManagedClientBuilder")
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
            var tlsConfigProvider = TlsConfigProvider.builder()
                    .trustStorePathResolverStrategy(FieldResolverStrategy.<String>builder()
                            .explicitValue(Fixtures.fixturePath("RegistryAwareClientBuilderTest/unitteststore.jks").toAbsolutePath().toString())
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
        void shouldReturnNewJerseyClientConfiguration_WithoutTLS_WhenProviderNotProvided() {
            var config = DropwizardManagedClientBuilder.newDefaultJerseyClientConfiguration();

            assertThat(config.getConnectionRequestTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_CONNECTION_POOL_TIMEOUT);
            assertThat(config.getConnectionTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_CONNECT_TIMEOUT);
            assertThat(config.getTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_READ_TIMEOUT);
            assertThat(config.getTlsConfiguration()).isNull();
        }

        @Test
        void shouldReturnNewJerseyClientConfiguration_WithoutTLS_WhenProviderIsNull() {
            var config = DropwizardManagedClientBuilder.newDefaultJerseyClientConfiguration(null);

            assertThat(config.getConnectionRequestTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_CONNECTION_POOL_TIMEOUT);
            assertThat(config.getConnectionTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_CONNECT_TIMEOUT);
            assertThat(config.getTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_READ_TIMEOUT);
            assertThat(config.getTlsConfiguration()).isNull();
        }

        @Test
        void shouldReturnNewJerseyClientConfiguration_WithoutTLS_WhenProviderCannotProvide() {
            var provider = TlsConfigProvider.builder().build();

            var config = DropwizardManagedClientBuilder.newDefaultJerseyClientConfiguration(provider);

            assertThat(config.getConnectionRequestTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_CONNECTION_POOL_TIMEOUT);
            assertThat(config.getConnectionTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_CONNECT_TIMEOUT);
            assertThat(config.getTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_READ_TIMEOUT);
            assertThat(config.getTlsConfiguration()).isNull();
        }

        @Test
        void shouldReturnNewJerseyClientConfiguration_WithTls_WhenProviderCanProvide() {
            var keystorePath = Fixtures.fixturePath("RegistryAwareClientBuilderTest/unitteststore.jks").toAbsolutePath().toString();
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

            assertThat(config.getConnectionRequestTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_CONNECTION_POOL_TIMEOUT);
            assertThat(config.getConnectionTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_CONNECT_TIMEOUT);
            assertThat(config.getTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_READ_TIMEOUT);
            assertThat(config.getTlsConfiguration()).isNotNull();
            assertThat(config.getTlsConfiguration().getTrustStorePath()).isEqualTo(new File(keystorePath));
            assertThat(config.getTlsConfiguration().getTrustStorePassword()).isEqualTo(keystorePassword);
        }
    }
}
