package org.kiwiproject.jersey.client;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.dropwizard.testing.junit5.DropwizardClientExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kiwiproject.jaxrs.KiwiMultivaluedMaps;
import org.kiwiproject.jersey.client.exception.MissingServiceRuntimeException;
import org.kiwiproject.registry.client.RegistryClient;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.Port.PortType;
import org.kiwiproject.registry.model.Port.Security;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.registry.model.ServicePaths;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@DisplayName("RegistryAwareClient")
@ExtendWith(DropwizardExtensionsSupport.class)
class RegistryAwareClientTest {

    @Path("/")
    @Produces(APPLICATION_JSON)
    public static class TestResource {

        @GET
        public Response verifyHeadersWereSent(@Context HttpHeaders httpHeaders) {
            return Response.ok(httpHeaders.getRequestHeaders()).build();
        }
    }

    private static final DropwizardClientExtension CLIENT_EXTENSION = new DropwizardClientExtension(new TestResource());

    private RegistryClient registryClient;
    private Client client;
    private RegistryAwareClient registryAwareClient;

    @BeforeEach
    void setUpClients() {
        registryClient = mock(RegistryClient.class);
        client = ClientBuilder.newClient();
        registryAwareClient = new RegistryAwareClient(client, registryClient);
    }

    @Nested
    class Construct {

        @Test
        void shouldSupplyHeaders_WhenSupplierProvided() {
            var baseUri = CLIENT_EXTENSION.baseUri();
            var instance = ServiceInstance.builder()
                    .serviceName("foo-service")
                    .hostName("localhost")
                    .ports(List.of(
                            Port.of(baseUri.getPort(), PortType.APPLICATION, Security.NOT_SECURE)
                    ))
                    .paths(ServicePaths.builder().homePagePath(baseUri.getPath()).build())
                    .build();

            when(registryClient.findServiceInstanceBy(any(RegistryClient.InstanceQuery.class)))
                    .thenReturn(Optional.of(instance));

            registryAwareClient = new RegistryAwareClient(client, registryClient,
                    () -> Map.of("FOO-HEADER", "This-Is-Cool"));

            var response = registryAwareClient.targetForService("foo-service").request().get();

            var bodyMap = response.readEntity(new GenericType<MultivaluedHashMap<String, String>>() {
            });
            var headerMap = KiwiMultivaluedMaps.toSingleValuedParameterMap(bodyMap);
            assertThat(headerMap).contains(entry("FOO-HEADER", "This-Is-Cool"));
        }
    }

    @Nested
    class TargetForService {

        private ServiceInstance instance;

        @BeforeEach
        void setUpInstance() {
            instance = ServiceInstance.builder()
                    .serviceName("test-service")
                    .hostName("localhost")
                    .ports(List.of(
                            Port.builder().number(8080).type(PortType.APPLICATION).secure(Security.SECURE).build(),
                            Port.builder().number(8081).type(PortType.ADMIN).secure(Security.SECURE).build()
                    ))
                    .paths(ServicePaths.builder().homePagePath("/home").build())
                    .build();
        }

        @Nested
        class WithServiceIdentifier {

            @Test
            void shouldReturnWebTargetWithCorrectBasePathWhenServiceIsFoundAndApplicationConnectionWanted() {
                when(registryClient.findServiceInstanceBy(any(RegistryClient.InstanceQuery.class)))
                        .thenReturn(Optional.of(instance));

                var identifier = ServiceIdentifier.builder()
                        .serviceName("test-service")
                        .connector(PortType.APPLICATION)
                        .build();

                var target = registryAwareClient.targetForService(identifier);

                assertThat(target.getUri()).hasToString("https://localhost:8080/home");
            }

            @Test
            void shouldReturnWebTargetWithCorrectBaseAdminPathWhenServiceIsFoundAndAdminConnectionWanted() {
                when(registryClient.findServiceInstanceBy(any(RegistryClient.InstanceQuery.class)))
                        .thenReturn(Optional.of(instance));

                var identifier = ServiceIdentifier.builder()
                        .serviceName("test-service")
                        .connector(PortType.ADMIN)
                        .build();

                var target = registryAwareClient.targetForService(identifier);

                assertThat(target.getUri()).hasToString("https://localhost:8081/");
            }

            @Test
            void shouldThrowMissingServiceExceptionWhenServiceNotFound() {
                when(registryClient.findServiceInstanceBy(any(RegistryClient.InstanceQuery.class)))
                        .thenReturn(Optional.empty());

                var identifier = ServiceIdentifier.builder()
                        .serviceName("test-service")
                        .connector(PortType.ADMIN)
                        .build();

                assertThatThrownBy(() -> registryAwareClient.targetForService(identifier))
                        .isInstanceOf(MissingServiceRuntimeException.class)
                        .hasMessage("No service instances found with name test-service, preferred version [latest], min version [none]");
            }
        }

        @Nested
        class WithServiceName {

            @Test
            void shouldBuildClientFromServiceNameOnly() {
                when(registryClient.findServiceInstanceBy(any(RegistryClient.InstanceQuery.class)))
                        .thenReturn(Optional.of(instance));

                var target = registryAwareClient.targetForService("test-service");

                assertThat(target.getUri()).hasToString("https://localhost:8080/home");
            }
        }

        @Nested
        class WithServiceNameAndPortType {

            @ParameterizedTest
            @CsvSource({
                    "APPLICATION, https://localhost:8080/home",
                    "ADMIN, https://localhost:8081/"
            })
            void shouldBuildClientForSpecifiedPortType(PortType portType, String expectedUri) {
                when(registryClient.findServiceInstanceBy(any(RegistryClient.InstanceQuery.class)))
                        .thenReturn(Optional.of(instance));

                var target = registryAwareClient.targetForService("test-service", portType);

                assertThat(target.getUri()).hasToString(expectedUri);
            }
        }
    }

    @Nested
    class GetClient {

        @Test
        void shouldReturnTheBuiltJerseyClient() {
            assertThat(registryAwareClient.client()).isEqualTo(client);
        }
    }

}
