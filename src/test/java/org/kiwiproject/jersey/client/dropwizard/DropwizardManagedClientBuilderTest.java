package org.kiwiproject.jersey.client.dropwizard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.testing.junit5.DropwizardClientExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;

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

    private static final DropwizardClientExtension CLIENT_EXTENSION = new DropwizardClientExtension(new TestResource());

    @Nested
    class BuildManagedJerseyClient {

        private static final String CLIENT_NAME = "jersey-client";

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

            var client = new DropwizardManagedClientBuilder()
                    .clientName(CLIENT_NAME)
                    .environment(CLIENT_EXTENSION.getEnvironment())
                    .jerseyClientConfiguration(config)
                    .buildManagedJerseyClient();

            // TODO: Need to figure out a way to verify that the config I provided was used
            assertThat(client).isInstanceOf(Client.class);
        }

        @Test
        void shouldSetupDefaultJerseyClientConfigurationIfNotGiven_IgnoringTLSIfOptedOut() {
            var client = new DropwizardManagedClientBuilder()
                    .clientName(CLIENT_NAME)
                    .environment(CLIENT_EXTENSION.getEnvironment())
                    .withoutTls()
                    .buildManagedJerseyClient();

            // TODO: Need to figure out a way to verify that TLS was not setup
            assertThat(client).isInstanceOf(Client.class);
        }

        @Test
        void shouldSetupDefaultJerseyClientConfigurationIfNotGiven_IgnoringTLSIfConfigProviderNotGiven() {
            var client = new DropwizardManagedClientBuilder()
                    .clientName(CLIENT_NAME)
                    .environment(CLIENT_EXTENSION.getEnvironment())
                    .buildManagedJerseyClient();

            // TODO: Need to figure out a way to verify that TLS was not setup
            assertThat(client).isInstanceOf(Client.class);
        }

        @Test
        void shouldSetupDefaultJerseyClientConfigurationIfNotGiven_IgnoringTLSIfConfigProviderCannotProvide() {
            var client = new DropwizardManagedClientBuilder()
                    .clientName(CLIENT_NAME)
                    .environment(CLIENT_EXTENSION.getEnvironment())
                    .buildManagedJerseyClient();

            // TODO: Need to figure out a way to verify that TLS was not setup
            assertThat(client).isInstanceOf(Client.class);
        }
    }
}
