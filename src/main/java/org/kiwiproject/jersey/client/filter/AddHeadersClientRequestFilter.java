package org.kiwiproject.jersey.client.filter;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.kiwiproject.base.KiwiPreconditions.checkOnlyOneArgumentIsNull;
import static org.kiwiproject.collect.KiwiMaps.isNullOrEmpty;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.function.Supplier;

/**
 * A Jakarta-RS {@link ClientRequestFilter} that adds headers to a request
 * from a {@link Supplier}.
*/
@Beta
@Slf4j
public class AddHeadersClientRequestFilter implements ClientRequestFilter {

    @VisibleForTesting
    final Supplier<Map<String, Object>> headersSupplier;

    @VisibleForTesting
    final Supplier<MultivaluedMap<String, Object>> headersMultivalueSupplier;

    @VisibleForTesting
    AddHeadersClientRequestFilter(Supplier<Map<String, Object>> headersSupplier,
                                  Supplier<MultivaluedMap<String, Object>> headersMultivalueSupplier) {

        checkOnlyOneArgumentIsNull(headersSupplier, headersMultivalueSupplier,
                "one of headersSupplier and headersMultivalueSupplier must be null, and the other non-null");

        this.headersSupplier = headersSupplier;
        this.headersMultivalueSupplier = headersMultivalueSupplier;
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
     * @param headersMultivalueSupplier the supplier that provides headers as a {@link MultivaluedMap}
     * @return a new {@link AddHeadersClientRequestFilter}
     */
    public static AddHeadersClientRequestFilter fromMultivaluedMapSupplier(
            Supplier<MultivaluedMap<String, Object>> headersMultivalueSupplier) {
        return new AddHeadersClientRequestFilter(null, headersMultivalueSupplier);
    }

    /**
     * Convenience method to create a new {@link AddHeadersClientRequestFilter} and
     * register it on {@code client}.
     * <p>
     * Only one of {@code headersSupplier} or {@code headersMultivalueSupplier}
     * should be non-null. If both are non-null, then {@code headersMultivalueSupplier}
     * is used and {@code headersSupplier} is ignored. If both are null, this is a no-op.
     * 
     * @param client                    the {@link Client} to register the filter on
     * @param headersSupplier           the supplier that provides headers as a Map
     * @param headersMultivalueSupplier the supplier that provides headers as a {@link MultivaluedMap}
     */
    public static void createAndRegister(Client client,
                                         @Nullable Supplier<Map<String, Object>> headersSupplier,
                                         @Nullable Supplier<MultivaluedMap<String, Object>> headersMultivalueSupplier) {        
       
        if (nonNull(headersMultivalueSupplier)) {
            client.register(AddHeadersClientRequestFilter.fromMultivaluedMapSupplier(headersMultivalueSupplier));
        } else if (nonNull(headersSupplier)) {
            client.register(AddHeadersClientRequestFilter.fromMapSupplier(headersSupplier));
        } else {
            LOG.debug("Not registering AddHeadersClientRequestFilter: headersSupplier and headersMultivalueSupplier are both null");
        }
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
            LOG.warn("No headers to add: Supplier provided null or empty headers Map");
            return;
        } else if (map instanceof MultivaluedMap) {
            throw new IllegalStateException(
                    "Supplier provided MultivaluedMap (for MultivaluedMaps, use fromMultivaluedMapSupplier factory method)");
        }

        var headers = requestContext.getHeaders();
        map.forEach(headers::add);
    }

    private void addHeadersFromMultivaluedMap(ClientRequestContext requestContext) {
        var multivaluedMap = headersMultivalueSupplier.get();
        if (isNull(multivaluedMap) || multivaluedMap.isEmpty()) {
            LOG.warn("No headers to add: Supplier provided null or empty headers MultivaluedMap");
            return;
        }

        var headers = requestContext.getHeaders();
        multivaluedMap.forEach((name, values) -> values.forEach(value -> headers.add(name, value)));
    }
}
