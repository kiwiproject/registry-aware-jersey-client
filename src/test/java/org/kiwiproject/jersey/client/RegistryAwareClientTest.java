package org.kiwiproject.jersey.client;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertOkResponse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.dropwizard.testing.junit5.DropwizardClientExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kiwiproject.jersey.client.exception.MissingServiceRuntimeException;
import org.kiwiproject.registry.client.RegistryClient;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.Port.PortType;
import org.kiwiproject.registry.model.Port.Security;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.registry.model.ServicePaths;
import org.kiwiproject.test.junit.jupiter.ResetLogbackLoggingExtension;

import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

@DisplayName("RegistryAwareClient")
@ExtendWith(ResetLogbackLoggingExtension.class)
@ExtendWith(DropwizardExtensionsSupport.class)
class RegistryAwareClientTest {

    @Path("/")
    @Produces(APPLICATION_JSON)
    public static class TestResource {

        @GET
        @Path("/echoHeaders")
        public Response verifyHeadersWereSent(@Context HttpHeaders httpHeaders) {
            return Response.ok(httpHeaders.getRequestHeaders()).build();
        }

        @GET
        @Path("/test")
        public Response test() {
            return Response.ok().build();
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
        void shouldNotAllowNullClient() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new RegistryAwareClient(null, registryClient));
        }

        @Test
        void shouldNotAllowNullRegistryClient() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new RegistryAwareClient(client, null));
        }

        @Test
        void shouldNotBeClosedAfterCreation() {
            assertThat(registryAwareClient.isClosed()).isFalse();
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

        @Nested
        class WithServiceIdentifierAndPortType {

            @ParameterizedTest
            @CsvSource({
                    "APPLICATION, https://localhost:8080/home",
                    "ADMIN, https://localhost:8081/"
            })
            void shouldBuildClientForSpecifiedPortType(PortType portType, String expectedUri) {
                when(registryClient.findServiceInstanceBy(any(RegistryClient.InstanceQuery.class)))
                        .thenReturn(Optional.of(instance));

                var identifier = ServiceIdentifier.builder().serviceName("test-service").build();
                var target = registryAwareClient.targetForService(identifier, portType);

                assertThat(target.getUri()).hasToString(expectedUri);
            }
        }

        @Nested
        class WithServiceIdentifierAndPortTypeAndPathResolver {

            @Test
            void shouldBuildClientForWith() {
                when(registryClient.findServiceInstanceBy(any(RegistryClient.InstanceQuery.class)))
                        .thenReturn(Optional.of(instance));

                var identifier = ServiceIdentifier.builder().serviceName("test-service").build();
                var target = registryAwareClient.targetForService(identifier, PortType.ADMIN,
                        theInstance -> theInstance.getPaths().getStatusPath());

                assertThat(target.getUri()).hasToString("https://localhost:8081/ping");
            }

            @Test
            void shouldThrowMissingServiceExceptionWhenServiceNotFound() {
                when(registryClient.findServiceInstanceBy(any(RegistryClient.InstanceQuery.class)))
                        .thenReturn(Optional.empty());
                
                var identifier = ServiceIdentifier.builder().serviceName("test-service").build();

                assertThatThrownBy(() -> registryAwareClient.targetForService(identifier, PortType.ADMIN, theInstance -> theInstance.getPaths().getStatusPath()))
                        .isInstanceOf(MissingServiceRuntimeException.class)
                        .hasMessage("No service instances found with name test-service, preferred version [latest], min version [none]");
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

    @Test
    void shouldCloseUnderlyingClientAutomatically() {
        var spiedClient = spy(client);
        var uri = UriBuilder.fromUri(CLIENT_EXTENSION.baseUri()).path("test").build();

        try (var theClient = new RegistryAwareClient(spiedClient, registryClient);
             var response = theClient.target(uri).request().get()) {
            assertOkResponse(response);
        }

        verify(spiedClient).close();

        assertThatIllegalStateException()
                .describedAs("Client should now be closed and throw IllegalStateException")
                .isThrownBy(() -> client.target(uri).request().get());
    }

    @Test
    void shouldBeClosed_AfterClientIsClosed() {
        registryAwareClient.close();

        assertThat(registryAwareClient.isClosed()).isTrue();

        assertThatIllegalStateException()
                .describedAs("Client should now be closed and throw IllegalStateException")
                .isThrownBy(() -> registryAwareClient.target("https://localhost:3001/hello").request().get());
    }

    @Test
    void shouldAcceptMultipleCallsToClose() {
        var count = RandomGenerator.getDefault().nextInt(2, 10);
        IntStream.rangeClosed(1, count).forEach(ignored -> registryAwareClient.close());

        assertThat(registryAwareClient.isClosed()).isTrue();
    }
}
