package org.kiwiproject.jersey.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.kiwiproject.test.util.Fixtures.fixture;

import io.dropwizard.util.Duration;
import jakarta.ws.rs.client.ClientBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.kiwiproject.registry.model.Port.PortType;
import org.kiwiproject.yaml.YamlHelper;

import java.util.concurrent.TimeUnit;

@DisplayName("ServiceIdentifier")
class ServiceIdentifierTest {

    @Test
    void shouldRequireServiceNameDuringBuilder() {
        var builder = ServiceIdentifier.builder();

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Service name is required");
    }

    @Test
    void shouldHaveDefaultValuesForSomeProperties() {
        var identifier = ServiceIdentifier.builder().serviceName("test-service").build();

        assertAll(
                () -> assertThat(identifier.getServiceName()).isEqualTo("test-service"),
                () -> assertThat(identifier.getPreferredVersion()).isNull(),
                () -> assertThat(identifier.getMinimumVersion()).isNull(),
                () -> assertThat(identifier.getConnector()).isEqualTo(PortType.APPLICATION),
                () -> assertThat(identifier.getConnectTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_CONNECT_TIMEOUT),
                () -> assertThat(identifier.getReadTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_READ_TIMEOUT)
        );
    }

    @Test
    void shouldSetDefaultsEvenWhenSpecifiedAsNull() {
        var identifier = ServiceIdentifier.builder()
                .serviceName("test-service")
                .connector(null)
                .connectTimeout(null)
                .readTimeout(null)
                .build();

        assertAll(
                () -> assertThat(identifier.getConnector()).isEqualTo(PortType.APPLICATION),
                () -> assertThat(identifier.getConnectTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_CONNECT_TIMEOUT),
                () -> assertThat(identifier.getReadTimeout()).isEqualTo(RegistryAwareClientConstants.DEFAULT_READ_TIMEOUT)
        );
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
    void shouldHaveACopyBuilder() {
        var identifier = ServiceIdentifier.builder()
                .serviceName("copy-service")
                .preferredVersion("42.0.0")
                .minimumVersion("42.0.0")
                .connector(PortType.ADMIN)
                .connectTimeout(Duration.milliseconds(1))
                .readTimeout(Duration.milliseconds(5))
                .build();

        var identifierCopy = identifier.toBuilder().build();

        assertAll(
                () -> assertThat(identifierCopy.getServiceName()).isEqualTo(identifier.getServiceName()),
                () -> assertThat(identifierCopy.getPreferredVersion()).isEqualTo(identifier.getPreferredVersion()),
                () -> assertThat(identifierCopy.getMinimumVersion()).isEqualTo(identifier.getMinimumVersion()),
                () -> assertThat(identifierCopy.getConnector()).isEqualTo(identifier.getConnector()),
                () -> assertThat(identifierCopy.getConnectTimeout()).isEqualTo(identifier.getConnectTimeout()),
                () -> assertThat(identifierCopy.getReadTimeout()).isEqualTo(identifier.getReadTimeout())
        );
    }

    @Test
    void shouldAllowForCopyWithServiceNameChange() {
        var identifier = ServiceIdentifier.builder()
                .serviceName("copy-service")
                .preferredVersion("42.0.0")
                .minimumVersion("42.0.0")
                .connector(PortType.ADMIN)
                .connectTimeout(Duration.milliseconds(1))
                .readTimeout(Duration.milliseconds(5))
                .build();

        var identifierCopy = identifier.withServiceName("name-change-service");

        assertAll(
                () -> assertThat(identifierCopy.getServiceName()).isEqualTo("name-change-service"),
                () -> assertThat(identifierCopy.getPreferredVersion()).isEqualTo(identifier.getPreferredVersion()),
                () -> assertThat(identifierCopy.getMinimumVersion()).isEqualTo(identifier.getMinimumVersion()),
                () -> assertThat(identifierCopy.getConnector()).isEqualTo(identifier.getConnector()),
                () -> assertThat(identifierCopy.getConnectTimeout()).isEqualTo(identifier.getConnectTimeout()),
                () -> assertThat(identifierCopy.getReadTimeout()).isEqualTo(identifier.getReadTimeout())
        );
    }

    @Test
    void shouldPopulateCorrectlyFromYamlFile() {
        var yaml = new YamlHelper();

        var identifier = yaml.toObject(fixture("ServiceIdentifierTest/config.yml"), ServiceIdentifier.class);

        assertAll(
                () -> assertThat(identifier.getServiceName()).isEqualTo("config-test-service"),
                () -> assertThat(identifier.getPreferredVersion()).isEqualTo("42.0.1"),
                () -> assertThat(identifier.getMinimumVersion()).isEqualTo("42.0.0"),
                () -> assertThat(identifier.getConnector()).isEqualTo(PortType.ADMIN),
                () -> assertThat(identifier.getConnectTimeout()).isEqualTo(Duration.milliseconds(5)),
                () -> assertThat(identifier.getReadTimeout()).isEqualTo(Duration.milliseconds(10))
        );
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
        void shouldReturnANewInstance_WithDataCopied() {
            var identifier = ServiceIdentifier.builder()
                    .serviceName("copy-service")
                    .preferredVersion("42.0.0")
                    .minimumVersion("42.0.0")
                    .connector(PortType.ADMIN)
                    .connectTimeout(Duration.milliseconds(1))
                    .readTimeout(Duration.milliseconds(5))
                    .build();

            var identifierCopy = ServiceIdentifier.copyOf(identifier);

            assertAll(
                    () -> assertThat(identifierCopy).isNotSameAs(identifier),
                    () -> assertThat(identifierCopy.getServiceName()).isEqualTo("copy-service"),
                    () -> assertThat(identifierCopy.getPreferredVersion()).isEqualTo(identifier.getPreferredVersion()),
                    () -> assertThat(identifierCopy.getMinimumVersion()).isEqualTo(identifier.getMinimumVersion()),
                    () -> assertThat(identifierCopy.getConnector()).isEqualTo(identifier.getConnector()),
                    () -> assertThat(identifierCopy.getConnectTimeout()).isEqualTo(identifier.getConnectTimeout()),
                    () -> assertThat(identifierCopy.getReadTimeout()).isEqualTo(identifier.getReadTimeout())
            );
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
