package org.kiwiproject.jersey.client.filter;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.APPLICATION_XML;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.kiwiproject.test.junit.jupiter.ClearBoxTest;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

@DisplayName("AddHeadersClientRequestFilter")
class AddHeadersClientRequestFilterTest {

    private ClientRequestContext context;

    @BeforeEach
    void setUp() {
        context = mock(ClientRequestContext.class);
    }

    @ClearBoxTest("tests internal constructor logic")
    void shouldThrowIllegalArgumentException_FromConstructor_WhenViolateOnlyOneNullContract() {
        var expectedMessage = "one of headersSupplier and headersMultivalueSupplier must be null, and the other non-null";
        assertAll(
                () -> assertThatIllegalArgumentException()
                        .isThrownBy(() -> new AddHeadersClientRequestFilter(null, null))
                        .withMessage(expectedMessage),
                () -> assertThatIllegalArgumentException()
                        .isThrownBy(() -> new AddHeadersClientRequestFilter(newMapSupplier(), newMultivaluedMapSupplier()))
                        .withMessage(expectedMessage)
        );
    }

    @Test
    void shouldAddHeadersFromRegularMap() {
        Supplier<Map<String, Object>> headersSupplier = newMapSupplier();

        var filter = AddHeadersClientRequestFilter.fromMapSupplier(headersSupplier);

        var headers = new MultivaluedHashMap<String, Object>();
        when(context.getHeaders()).thenReturn(headers);
        filter.filter(context);

        assertAll(
                () -> assertThat(headers.getFirst(HttpHeaders.ACCEPT)).isEqualTo(APPLICATION_JSON),
                () -> assertThat(headers.getFirst(HttpHeaders.ACCEPT_ENCODING)).isEqualTo("gzip"),
                () -> assertThat(headers.getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo(APPLICATION_JSON),
                () -> assertThat(headers.getFirst(HttpHeaders.USER_AGENT)).isEqualTo("Kiwi Jersey Client")
        );
    }

    private static Supplier<Map<String, Object>> newMapSupplier() {
        return () -> Map.of(
                HttpHeaders.ACCEPT, APPLICATION_JSON,
                HttpHeaders.ACCEPT_ENCODING, "gzip",
                HttpHeaders.CONTENT_TYPE, APPLICATION_JSON,
                HttpHeaders.USER_AGENT, "Kiwi Jersey Client"
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldIgnoreNullOrEmptyRegularMap(Map<String, Object> headers) {
        Supplier<Map<String, Object>> headersSupplier = () -> headers;

        var filter = AddHeadersClientRequestFilter.fromMapSupplier(headersSupplier);

        var theHeaders = new MultivaluedHashMap<String, Object>();
        when(context.getHeaders()).thenReturn(theHeaders);
        filter.filter(context);

        assertThat(theHeaders).isEmpty();
    }

    @Test
    void shouldThrowIllegalStateException_WhenSupplierProvidesMultivaluedMap_WhenExpectingRegularMap() {
        Supplier<Map<String, Object>> headersSupplier = newMapSupplierOfMultivaluedMap();

        var filter = AddHeadersClientRequestFilter.fromMapSupplier(headersSupplier);

        assertThatIllegalStateException()
                .isThrownBy(() -> filter.filter(context))
                .withMessage("Supplier provided MultivaluedMap (for MultivaluedMaps, use fromMultivaluedMapSupplier factory method)");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Supplier<Map<String, Object>> newMapSupplierOfMultivaluedMap() {
        return () -> {
            var map = new MultivaluedHashMap();
            map.putSingle(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON);
            return map;
        };
    }

    @Test
    void shouldAddHeadersFromMultivaluedMap() {
        Supplier<MultivaluedMap<String, Object>> headersSupplier = newMultivaluedMapSupplier();

        var filter = AddHeadersClientRequestFilter.fromMultivaluedMapSupplier(headersSupplier);

        var headers = new MultivaluedHashMap<String, Object>();
        when(context.getHeaders()).thenReturn(headers);
        filter.filter(context);

        assertAll(
                () -> assertThat(headers.get(HttpHeaders.ACCEPT))
                        .containsExactlyInAnyOrder(APPLICATION_JSON, APPLICATION_XML),
                () -> assertThat(headers.get(HttpHeaders.ACCEPT_ENCODING))
                        .containsExactlyInAnyOrder("gzip", "compress", "deflate", "br"),
                () -> assertThat(headers.getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo(APPLICATION_JSON),
                () -> assertThat(headers.getFirst(HttpHeaders.USER_AGENT)).isEqualTo("Kiwi Jersey Client")
        );
    }

    private static Supplier<MultivaluedMap<String, Object>> newMultivaluedMapSupplier() {
        return () -> {
            var headers = new MultivaluedHashMap<String, Object>();
            headers.add(HttpHeaders.ACCEPT, APPLICATION_JSON);
            headers.add(HttpHeaders.ACCEPT, APPLICATION_XML);
            headers.add(HttpHeaders.ACCEPT_ENCODING, "gzip");
            headers.add(HttpHeaders.ACCEPT_ENCODING, "compress");
            headers.add(HttpHeaders.ACCEPT_ENCODING, "deflate");
            headers.add(HttpHeaders.ACCEPT_ENCODING, "br");
            headers.add(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON);
            headers.add(HttpHeaders.USER_AGENT, "Kiwi Jersey Client");
            return headers;
        };
    }

    @ParameterizedTest
    @MethodSource("nullAndEmptyMultivaluedMaps")
    void shouldIgnoreNullOrEmptyMultivaluedMap(MultivaluedMap<String, Object> multivaluedMap) {
        Supplier<MultivaluedMap<String, Object>> headersSupplier = () -> multivaluedMap;

        var filter = AddHeadersClientRequestFilter.fromMultivaluedMapSupplier(headersSupplier);

        var theHeaders = new MultivaluedHashMap<String, Object>();
        when(context.getHeaders()).thenReturn(theHeaders);
        filter.filter(context);

        assertThat(theHeaders).isEmpty();
    }

    static Stream<MultivaluedMap<String, Object>> nullAndEmptyMultivaluedMaps() {
        return Stream.of(
                null,
                new MultivaluedHashMap<>()
        );
    }

    @Nested
    class CreateAndRegister {

        private Client client;

        @BeforeEach
        void setUp() {
            client = mock(Client.class);
        }

        @Test
        void shouldDoNothing_WhenNeitherSupplierIsProvided() {
            AddHeadersClientRequestFilter.createAndRegister(client, null, null);

            verifyNoInteractions(client);
        }

        @Test
        void shouldRegisterOnClient_WithMapSupplier() {
            var headersSupplier = newMapSupplier();

            AddHeadersClientRequestFilter.createAndRegister(client, headersSupplier, null);

            var filterCaptor = ArgumentCaptor.forClass(AddHeadersClientRequestFilter.class);
            verify(client, only()).register(filterCaptor.capture());

            var filter = filterCaptor.getValue();
            assertAll(
                    () -> assertThat(filter.headersSupplier).isNotNull(),
                    () -> assertThat(filter.headersMultivalueSupplier).isNull()
            );
        }

        @Test
        void shouldRegisterOnClient_WithMultivaluedSupplier() {
            var headersMultivalueSupplier = newMultivaluedMapSupplier();

            AddHeadersClientRequestFilter.createAndRegister(client, null, headersMultivalueSupplier);

            var filterCaptor = ArgumentCaptor.forClass(AddHeadersClientRequestFilter.class);
            verify(client, only()).register(filterCaptor.capture());

            var filter = filterCaptor.getValue();
            assertAll(
                    () -> assertThat(filter.headersSupplier).isNull(),
                    () -> assertThat(filter.headersMultivalueSupplier).isNotNull()
            );
        }

        @Test
        void shouldUseMultivaluedSupplier_IfBothSuppliersAreProvided() {
            var headersSupplier = newMapSupplier();
            var headersMultivalueSupplier = newMultivaluedMapSupplier();

            AddHeadersClientRequestFilter.createAndRegister(client, headersSupplier, headersMultivalueSupplier);
        
            var filterCaptor = ArgumentCaptor.forClass(AddHeadersClientRequestFilter.class);
            verify(client, only()).register(filterCaptor.capture());

            var filter = filterCaptor.getValue();
            assertAll(
                    () -> assertThat(filter.headersSupplier).isNull(),
                    () -> assertThat(filter.headersMultivalueSupplier).isNotNull()
            );
        }
    }
}
