package org.kiwiproject.jersey.client.dropwizard;

import static org.assertj.core.api.Assertions.assertThat;

import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.testing.junit5.DropwizardClientExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kiwiproject.base.DefaultEnvironment;
import org.kiwiproject.base.KiwiEnvironment;
import org.kiwiproject.jersey.client.RegistryAwareClientConstants;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.concurrent.atomic.AtomicInteger;

@DisplayName("DropwizardManagedClients")
@ExtendWith(DropwizardExtensionsSupport.class)
class DropwizardManagedClientsTest {

    @Path("/test")
    @Produces("text/plain")
    public static class TestResource {
        private static final KiwiEnvironment KIWI_ENV = new DefaultEnvironment();

        static final AtomicInteger processingTimeMillis = new AtomicInteger();

        @GET
        public Response get() {
            var millis = processingTimeMillis.get();

            if (millis > 0) {
                KIWI_ENV.sleepQuietly(millis);
            }

            return Response.ok("OK").build();
        }
    }

    private static final DropwizardClientExtension CLIENT = new DropwizardClientExtension(new TestResource());

    @AfterEach
    void tearDown() {
        TestResource.processingTimeMillis.set(0);
    }

    @Nested
    class NewDefaultJerseyClientConfiguration {
        private JerseyClientConfiguration config;

        @BeforeEach
        void setUp() {
            config = DropwizardManagedClients.newDefaultJerseyClientConfiguration();
        }

        @Test
        void shouldSetReadTimeout() {
            assertThat(config.getConnectionRequestTimeout().toMilliseconds())
                    .isEqualTo(RegistryAwareClientConstants.DEFAULT_READ_TIMEOUT_MILLIS);
        }

        @Test
        void shouldSetConnectionTimeout() {
            assertThat(config.getConnectionTimeout().toMilliseconds())
                    .isEqualTo(RegistryAwareClientConstants.DEFAULT_CONNECT_TIMEOUT_MILLIS);
        }
    }

}
