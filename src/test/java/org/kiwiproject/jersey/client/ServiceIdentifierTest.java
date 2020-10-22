package org.kiwiproject.jersey.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kiwiproject.test.util.Fixtures.fixture;

import io.dropwizard.util.Duration;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.yaml.YamlHelper;

@DisplayName("ServiceIdentifier")
@ExtendWith(SoftAssertionsExtension.class)
class ServiceIdentifierTest {

    @Test
    void shouldRequireServiceNameDuringBuilder() {
        assertThatThrownBy(() -> ServiceIdentifier.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Service name is required");
    }

    @Test
    void shouldHaveDefaultValuesForSomeProperties(SoftAssertions softly) {
        var identifier = ServiceIdentifier.builder().serviceName("test-service").build();

        softly.assertThat(identifier.getServiceName()).isEqualTo("test-service");
        softly.assertThat(identifier.getPreferredVersion()).isNull();
        softly.assertThat(identifier.getMinimumVersion()).isNull();
        softly.assertThat(identifier.getConnector()).isEqualTo(Port.PortType.APPLICATION);
        softly.assertThat(identifier.getConnectTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_CONNECT_TIMEOUT);
        softly.assertThat(identifier.getReadTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_CONNECT_REQUEST_TIMEOUT);
    }

    @Test
    void shouldHaveACopyBuilder(SoftAssertions softly) {
        var identifier = ServiceIdentifier.builder()
                .serviceName("copy-service")
                .preferredVersion("42.0.0")
                .minimumVersion("42.0.0")
                .connector(Port.PortType.ADMIN)
                .connectTimeout(Duration.milliseconds(1))
                .readTimeout(Duration.milliseconds(5))
                .build();

        var identifierCopy = identifier.toBuilder().build();

        softly.assertThat(identifierCopy.getServiceName()).isEqualTo(identifier.getServiceName());
        softly.assertThat(identifierCopy.getPreferredVersion()).isEqualTo(identifier.getPreferredVersion());
        softly.assertThat(identifierCopy.getMinimumVersion()).isEqualTo(identifier.getMinimumVersion());
        softly.assertThat(identifierCopy.getConnector()).isEqualTo(identifier.getConnector());
        softly.assertThat(identifierCopy.getConnectTimeout()).isEqualTo(identifier.getConnectTimeout());
        softly.assertThat(identifierCopy.getReadTimeout()).isEqualTo(identifier.getReadTimeout());
    }

    @Test
    void shouldAllowForCopyWithServiceNameChange(SoftAssertions softly) {
        var identifier = ServiceIdentifier.builder()
                .serviceName("copy-service")
                .preferredVersion("42.0.0")
                .minimumVersion("42.0.0")
                .connector(Port.PortType.ADMIN)
                .connectTimeout(Duration.milliseconds(1))
                .readTimeout(Duration.milliseconds(5))
                .build();

        var identifierCopy = identifier.withServiceName("name-change-service");

        softly.assertThat(identifierCopy.getServiceName()).isEqualTo("name-change-service");
        softly.assertThat(identifierCopy.getPreferredVersion()).isEqualTo(identifier.getPreferredVersion());
        softly.assertThat(identifierCopy.getMinimumVersion()).isEqualTo(identifier.getMinimumVersion());
        softly.assertThat(identifierCopy.getConnector()).isEqualTo(identifier.getConnector());
        softly.assertThat(identifierCopy.getConnectTimeout()).isEqualTo(identifier.getConnectTimeout());
        softly.assertThat(identifierCopy.getReadTimeout()).isEqualTo(identifier.getReadTimeout());
    }

    @Test
    void shouldPopulateCorrectlyFromYamlFile(SoftAssertions softly) {
        var yaml = new YamlHelper();

        var identifier = yaml.toObject(fixture("ServiceIdentifierTest/config.yml"), ServiceIdentifier.class);

        softly.assertThat(identifier.getServiceName()).isEqualTo("config-test-service");
        softly.assertThat(identifier.getPreferredVersion()).isEqualTo("42.0.1");
        softly.assertThat(identifier.getMinimumVersion()).isEqualTo("42.0.0");
        softly.assertThat(identifier.getConnector()).isEqualTo(Port.PortType.ADMIN);
        softly.assertThat(identifier.getConnectTimeout()).isEqualTo(Duration.milliseconds(5));
        softly.assertThat(identifier.getReadTimeout()).isEqualTo(Duration.milliseconds(10));
    }
}
