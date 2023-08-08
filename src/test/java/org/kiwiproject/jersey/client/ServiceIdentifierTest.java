package org.kiwiproject.jersey.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kiwiproject.test.util.Fixtures.fixture;

import io.dropwizard.util.Duration;
import jakarta.ws.rs.client.ClientBuilder;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.kiwiproject.registry.model.Port.PortType;
import org.kiwiproject.yaml.YamlHelper;

import java.util.concurrent.TimeUnit;

@DisplayName("ServiceIdentifier")
@ExtendWith(SoftAssertionsExtension.class)
class ServiceIdentifierTest {

    @Test
    void shouldRequireServiceNameDuringBuilder() {
        var builder = ServiceIdentifier.builder();

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Service name is required");
    }

    @Test
    void shouldHaveDefaultValuesForSomeProperties(SoftAssertions softly) {
        var identifier = ServiceIdentifier.builder().serviceName("test-service").build();

        softly.assertThat(identifier.getServiceName()).isEqualTo("test-service");
        softly.assertThat(identifier.getPreferredVersion()).isNull();
        softly.assertThat(identifier.getMinimumVersion()).isNull();
        softly.assertThat(identifier.getConnector()).isEqualTo(PortType.APPLICATION);
        softly.assertThat(identifier.getConnectTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_CONNECT_TIMEOUT);
        softly.assertThat(identifier.getReadTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_READ_TIMEOUT);
    }

    @Test
    void shouldSetDefaultsEvenWhenSpecifiedAsNull(SoftAssertions softly) {
        var identifier = ServiceIdentifier.builder()
                .serviceName("test-service")
                .connector(null)
                .connectTimeout(null)
                .readTimeout(null)
                .build();

        softly.assertThat(identifier.getConnector()).isEqualTo(PortType.APPLICATION);
        softly.assertThat(identifier.getConnectTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_CONNECT_TIMEOUT);
        softly.assertThat(identifier.getReadTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_READ_TIMEOUT);
    }

    @Test
    void shouldStillExpectExceptionFromJerseyForConnectTimeoutOverMaxInteger() {
        var timeout = oneMoreThanMaxInteger();
        assertThatThrownBy(() ->
                ClientBuilder.newBuilder().connectTimeout(timeout, TimeUnit.MILLISECONDS).build())
                .describedAs("Implementation change: Jersey is no longer requiring connect timeout to be convertible to an int")
                .isExactlyInstanceOf(ArithmeticException.class);
    }

    @Test
    void shouldStillExpectExceptionFromJerseyForReadTimeoutOverMaxInteger() {
        var timeout = oneMoreThanMaxInteger();
        assertThatThrownBy(() ->
                ClientBuilder.newBuilder().readTimeout(timeout, TimeUnit.MILLISECONDS).build())
                .describedAs("Implementation change: Jersey is no longer requiring read timeout to be convertible to an int")
                .isExactlyInstanceOf(ArithmeticException.class);
    }

    @Test
    void shouldNotAllowConnectTimeoutThatWouldOverflowInt() {
        long timeout = oneMoreThanMaxInteger();
        assertThatIllegalArgumentException().isThrownBy(() ->
                ServiceIdentifier.builder().serviceName("test-service").connectTimeout(Duration.milliseconds(timeout)).build())
                .withMessage("connect timeout must be convertible to an int but %d is more than Integer.MAX_VALUE." +
                        " See Jersey API docs for CONNECT_TIMEOUT and READ_TIMEOUT in ClientProperties", timeout);
    }

    @Test
    void shouldNotAllowReadTimeoutThatWouldOverflowInt() {
        long timeout = oneMoreThanMaxInteger();
        assertThatIllegalArgumentException().isThrownBy(() ->
                ServiceIdentifier.builder().serviceName("test-service").readTimeout(Duration.milliseconds(timeout)).build())
                .withMessage("read timeout must be convertible to an int but %d is more than Integer.MAX_VALUE." +
                        " See Jersey API docs for CONNECT_TIMEOUT and READ_TIMEOUT in ClientProperties", timeout);
    }

    private static long oneMoreThanMaxInteger() {
        return 1L + Integer.MAX_VALUE;
    }

    @Test
    void shouldHaveACopyBuilder(SoftAssertions softly) {
        var identifier = ServiceIdentifier.builder()
                .serviceName("copy-service")
                .preferredVersion("42.0.0")
                .minimumVersion("42.0.0")
                .connector(PortType.ADMIN)
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
                .connector(PortType.ADMIN)
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
        softly.assertThat(identifier.getConnector()).isEqualTo(PortType.ADMIN);
        softly.assertThat(identifier.getConnectTimeout()).isEqualTo(Duration.milliseconds(5));
        softly.assertThat(identifier.getReadTimeout()).isEqualTo(Duration.milliseconds(10));
    }

    @Nested
    class Factories {

        @Test
        void shouldCreateWithServiceName() {
            var factoryIdentifier = ServiceIdentifier.of("test-service");
            var builderIdentifier = ServiceIdentifier.builder().serviceName("test-service").build();
            assertThat(factoryIdentifier).usingRecursiveComparison().isEqualTo(builderIdentifier);
        }

        @ParameterizedTest
        @EnumSource(PortType.class)
        void shouldCreateWithServiceName_AndConnector(PortType connector) {
            var factoryIdentifier = ServiceIdentifier.of("test-service", connector);
            var builderIdentifier = ServiceIdentifier.builder()
                    .serviceName("test-service")
                    .connector(connector)
                    .build();
            assertThat(factoryIdentifier).usingRecursiveComparison().isEqualTo(builderIdentifier);
        }
    }

    @Nested
    class CopyOf {

        @Test
        void shouldReturnANewInstance_WithDataCopied(SoftAssertions softly) {
            var identifier = ServiceIdentifier.builder()
                    .serviceName("copy-service")
                    .preferredVersion("42.0.0")
                    .minimumVersion("42.0.0")
                    .connector(PortType.ADMIN)
                    .connectTimeout(Duration.milliseconds(1))
                    .readTimeout(Duration.milliseconds(5))
                    .build();

            var identifierCopy = ServiceIdentifier.copyOf(identifier);

            softly.assertThat(identifierCopy).isNotSameAs(identifier);
            softly.assertThat(identifierCopy.getServiceName()).isEqualTo("copy-service");
            softly.assertThat(identifierCopy.getPreferredVersion()).isEqualTo(identifier.getPreferredVersion());
            softly.assertThat(identifierCopy.getMinimumVersion()).isEqualTo(identifier.getMinimumVersion());
            softly.assertThat(identifierCopy.getConnector()).isEqualTo(identifier.getConnector());
            softly.assertThat(identifierCopy.getConnectTimeout()).isEqualTo(identifier.getConnectTimeout());
            softly.assertThat(identifierCopy.getReadTimeout()).isEqualTo(identifier.getReadTimeout());
        }
    }

    @Nested
    class GetTimeoutMethods {

        @Test
        void shouldReturnConnectTimeoutAsInt() {
            var identifier = ServiceIdentifier.builder()
                    .serviceName("test-service")
                    .connectTimeout(Duration.milliseconds(250))
                    .build();

            assertThat(identifier.getConnectTimeoutAsIntMillis()).isEqualTo(250);
        }

        @Test
        void shouldReturnReadTimeoutAsInt() {
            var identifier = ServiceIdentifier.builder()
                    .serviceName("test-service")
                    .readTimeout(Duration.milliseconds(750))
                    .build();

            assertThat(identifier.getReadTimeoutAsIntMillis()).isEqualTo(750);
        }
    }
}
