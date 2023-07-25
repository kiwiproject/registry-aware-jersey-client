package org.kiwiproject.jersey.client.dropwizard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.kiwiproject.test.constants.KiwiTestConstants.JSON_HELPER;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.testing.junit5.DropwizardClientExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kiwiproject.jersey.client.ClientBuilders;
import org.kiwiproject.jersey.client.RegistryAwareClient;
import org.kiwiproject.json.JsonHelper;
import org.kiwiproject.registry.NoOpRegistryClient;
import org.kiwiproject.registry.client.RegistryClient;

import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.concurrent.ThreadLocalRandom;

@DisplayName("DropwizardClients")
@ExtendWith(DropwizardExtensionsSupport.class)
class DropwizardClientsTest {

    @Path("/dropwizardClients")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Slf4j
    public static class TestResource {

        public TestResource() {
            LOG.info("TestResource: constructor");
        }

        @GET
        @Path("/{id}")
        public Response get(@PathParam("id") Long id) {
            var alice = newSamplePerson(id);

            return Response.ok(alice).build();
        }

        @POST
        public Response create(@NotNull Person person) {
            Long id = Math.abs(ThreadLocalRandom.current().nextLong(1_000));
            var createdPerson = person.withId(id);
            var location = UriBuilder.fromResource(TestResource.class)
                    .path("/{id}")
                    .resolveTemplate("id", id)
                    .build();
            return Response.created(location).entity(createdPerson).build();
        }

        @PUT
        public Response update(@NotNull Person person) {
            var updatedPerson = person.withUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
            return Response.ok(updatedPerson).build();
        }
    }

    @Value
    @Builder
    private static class Person {

        @With
        Long id;
        String firstName;
        String lastName;
        String email;
        int age;
        ZonedDateTime createdAt;
        @With
        ZonedDateTime updatedAt;
    }

    private static final DropwizardClientExtension CLIENT_EXTENSION = new DropwizardClientExtension(new TestResource());

    private static String baseUri;

    @BeforeAll
    static void beforeAll() {
        baseUri = CLIENT_EXTENSION.baseUri().toString();

        // IMPORTANT: configure so ZonedDateTime are serialized as millis
        JsonHelper.configureForMillisecondDateTimestamps(CLIENT_EXTENSION.getObjectMapper());
    }

    @Nested
    class AddJacksonMessageBodyProvider {

        private RegistryClient registryClient;

        @BeforeEach
        void setUp() {
            registryClient = new NoOpRegistryClient();
        }

        @Test
        void shouldReturnSameDelegateClientInstance() {
            var mapper = new ObjectMapper();
            RegistryAwareClient originalClient = ClientBuilders.jersey().registryClient(registryClient).build();
            Client client = DropwizardClients.addJacksonMessageBodyProvider(originalClient, mapper);

            assertThat(client)
                    .describedAs("The returned Client should be the same as the Client @Delegate in RegistryAwareClient")
                    .isSameAs(originalClient.client());
        }

        @Test
        void shouldDeserializeJsonResponses() {
            var registryAwareClient = ClientBuilders.jersey().registryClient(registryClient).build();
            DropwizardClients.addJacksonMessageBodyProvider(registryAwareClient, CLIENT_EXTENSION.getObjectMapper());

            var id = 42L;
            var response = registryAwareClient.target(baseUri)
                    .path("/dropwizardClients/{id}")
                    .resolveTemplate("id", id)
                    .request()
                    .get();

            var entity = response.readEntity(Person.class);
            var expectedSamplePerson = newSamplePerson(id);
            assertThat(entity).isEqualTo(expectedSamplePerson);
        }

        @Test
        void shouldUseCustomizedObjectMapperToWriteTimestampsAsMillis() {
            var registryAwareClient = ClientBuilders.jersey().registryClient(registryClient).build();
            DropwizardClients.addJacksonMessageBodyProvider(registryAwareClient, CLIENT_EXTENSION.getObjectMapper());

            var id = 42L;
            var response = registryAwareClient.target(baseUri)
                    .path("/dropwizardClients/{id}")
                    .resolveTemplate("id", id)
                    .request()
                    .get();

            var json = response.readEntity(String.class);
            var entity = JSON_HELPER.toMap(json);

            var samplePerson = newSamplePerson(id);
            assertThat(entity)
                    .describedAs("JSON should contain epoch millis from customized MObjectMapper")
                    .contains(
                            entry("createdAt", samplePerson.getCreatedAt().toInstant().toEpochMilli()),
                            entry("updatedAt", samplePerson.getUpdatedAt().toInstant().toEpochMilli())
                    );
        }
    }

    private static Person newSamplePerson(Long id) {
        var utcZoneId = ZoneId.of("UTC").normalized();

        return Person.builder()
                .id(id)
                .firstName("Alice")
                .lastName("Smith")
                .email("alice.smith@gmail.com")
                .age(42)
                .createdAt(ZonedDateTime.of(2020, 3, 31, 12, 0, 0, 0, utcZoneId))
                .updatedAt(ZonedDateTime.of(2020, 11, 15, 14, 30, 0, 0, utcZoneId))
                .build();
    }
}
