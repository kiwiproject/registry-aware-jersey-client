package org.kiwiproject.jersey.client.filter;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.kiwiproject.base.KiwiPreconditions.checkOnlyOneArgumentIsNull;
import static org.kiwiproject.collect.KiwiMaps.isNullOrEmpty;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.function.Supplier;

/**
 * A Jakarta-RS {@link ClientRequestFilter} that adds headers to a request
 * from a {@link Supplier}.
*/
@Beta
@Slf4j
public class AddHeadersClientRequestFilter implements ClientRequestFilter {

    private final Supplier<Map<String, Object>> headersSupplier;
    private final Supplier<MultivaluedMap<String, Object>> multivaluedHeadersSupplier;

    @VisibleForTesting
    AddHeadersClientRequestFilter(Supplier<Map<String, Object>> headersSupplier,
                                  Supplier<MultivaluedMap<String, Object>> multivaluedHeadersSupplier) {

        checkOnlyOneArgumentIsNull(headersSupplier, multivaluedHeadersSupplier,
                "one of headersSupplier and multivaluedHeadersSupplier must be null, and the other non-null");

        this.headersSupplier = headersSupplier;
        this.multivaluedHeadersSupplier = multivaluedHeadersSupplier;
    }

    /**
     * Creates a new instance that will add headers from a {@link Map} supplied by the given supplier.
     * The supplier should provide a regular {@link Map}, not a {@link MultivaluedMap}.
     * <p>
     * Use this when you only need to set a single value for each header.
     *
     * @param headersSupplier the supplier that provides headers as a Map
     * @return a new {@link AddHeadersClientRequestFilter}
     * @throws IllegalStateException if the supplier provides a {@link MultivaluedMap}
     */
    public static AddHeadersClientRequestFilter fromMapSupplier(Supplier<Map<String, Object>> headersSupplier) {
        return new AddHeadersClientRequestFilter(headersSupplier, null);
    }

    /**
     * Creates a new filter that will add headers from a {@link MultivaluedMap} supplied by the given supplier.
     * <p>
     * Use this method when you need to supply multiple values for the same header.
     *
     * @param headersSupplier the supplier that provides headers as a {@link MultivaluedMap}
     * @return a new {@link AddHeadersClientRequestFilter}
     */
    public static AddHeadersClientRequestFilter fromMultivaluedMapSupplier(
            Supplier<MultivaluedMap<String, Object>> headersSupplier) {
        return new AddHeadersClientRequestFilter(null, headersSupplier);
    }

    @Override
    public void filter(ClientRequestContext requestContext) {
        if (nonNull(headersSupplier)) {
            addHeadersFromMap(requestContext);
        } else {
            addHeadersFromMultivaluedMap(requestContext);
        }
    }

    private void addHeadersFromMap(ClientRequestContext requestContext) {
        var map = headersSupplier.get();
        if (isNullOrEmpty(map)) {
            LOG.warn("Supplier provided null or empty headers Map");
            return;
        } else if (map instanceof MultivaluedMap) {
            throw new IllegalStateException(
                    "Supplier provided MultivaluedMap (for MultivaluedMaps, use fromMultivaluedMapSupplier factory method)");
        }

        var headers = requestContext.getHeaders();
        map.forEach((key, value) -> headers.add(key, value));
    }

    private void addHeadersFromMultivaluedMap(ClientRequestContext requestContext) {
        var multivaluedMap = multivaluedHeadersSupplier.get();
        if (isNull(multivaluedMap) || multivaluedMap.isEmpty()) {
            LOG.warn("Supplier provided null or empty headers MultivaluedMap");
            return;
        }

        var headers = requestContext.getHeaders();
        multivaluedMap.forEach((name, values) -> values.forEach(value -> headers.add(name, value)));
    }
}
