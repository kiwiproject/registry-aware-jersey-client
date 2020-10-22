package org.kiwiproject.jersey.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.registry.client.RegistryClient;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.registry.model.ServicePaths;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import java.util.List;
import java.util.Optional;

@DisplayName("RegistryAwareClient")
class RegistryAwareClientTest {

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
    class TargetForService {

        private ServiceInstance instance;

        @BeforeEach
        void setUpInstance() {
            instance = ServiceInstance.builder()
                    .serviceName("test-service")
                    .hostName("localhost")
                    .ports(List.of(
                            Port.builder().number(8080).type(Port.PortType.APPLICATION).secure(Port.Security.SECURE).build(),
                            Port.builder().number(8081).type(Port.PortType.ADMIN).secure(Port.Security.SECURE).build()
                    ))
                    .paths(ServicePaths.builder().homePagePath("/home").build())
                    .build();
        }

        @Nested
        class WithServiceIdentifier {

            @Test
            void shouldReturnWebTargetWithCorrectBasePathWhenServiceIsFoundAndApplicationConnectionWanted() {
                when(registryClient.findServiceInstanceBy(any(RegistryClient.InstanceQuery.class))).thenReturn(Optional.of(instance));

                var identifier = ServiceIdentifier.builder()
                        .serviceName("test-service")
                        .connector(ServiceIdentifier.Connector.APPLICATION)
                        .build();

                var target = registryAwareClient.targetForService(identifier);

                assertThat(target.getUri()).hasToString("https://localhost:8080/home");
            }

            @Test
            void shouldReturnWebTargetWithCorrectBaseAdminPathWhenServiceIsFoundAndAdminConnectionWanted() {
                when(registryClient.findServiceInstanceBy(any(RegistryClient.InstanceQuery.class))).thenReturn(Optional.of(instance));

                var identifier = ServiceIdentifier.builder()
                        .serviceName("test-service")
                        .connector(ServiceIdentifier.Connector.ADMIN)
                        .build();

                var target = registryAwareClient.targetForService(identifier);

                assertThat(target.getUri()).hasToString("https://localhost:8081/");
            }

            @Test
            void shouldThrowMissingServiceExceptionWhenServiceNotFound() {
                when(registryClient.findServiceInstanceBy(any(RegistryClient.InstanceQuery.class))).thenReturn(Optional.empty());

                var identifier = ServiceIdentifier.builder()
                        .serviceName("test-service")
                        .connector(ServiceIdentifier.Connector.ADMIN)
                        .build();

                assertThatThrownBy(() -> registryAwareClient.targetForService(identifier))
                        .isInstanceOf(RegistryAwareClient.MissingServiceRuntimeException.class)
                        .hasMessage("No service instances found with name test-service, preferred version <latest>, min version <none>");
            }
        }

        @Nested
        class WithServiceName {

            @Test
            void shouldBuildClientFromServiceNameOnly() {
                when(registryClient.findServiceInstanceBy(any(RegistryClient.InstanceQuery.class))).thenReturn(Optional.of(instance));

                var target = registryAwareClient.targetForService("test-service");

                assertThat(target.getUri()).hasToString("https://localhost:8080/home");
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
