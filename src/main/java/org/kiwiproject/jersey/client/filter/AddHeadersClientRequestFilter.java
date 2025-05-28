package org.kiwiproject.jersey.client.filter;

import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;

import com.google.common.annotations.Beta;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;

import java.util.Map;
import java.util.function.Supplier;

/**
 * A Jakarta-RS {@link ClientRequestFilter} that adds headers to a request
 * from a {@link Supplier}.
*/
@Beta
public class AddHeadersClientRequestFilter implements ClientRequestFilter {

    private final Supplier<Map<String, Object>> headersSupplier;

    public AddHeadersClientRequestFilter(Supplier<Map<String, Object>> headersSupplier) {
        this.headersSupplier = requireNotNull(headersSupplier, "headersSupplier must not be null");
    }

    @Override
    public void filter(ClientRequestContext requestContext) {
        var headers = headersSupplier.get();
        // TODO handle badly behaved Supplier? i.e., that provides us a null value
        headers.forEach((key, value) -> requestContext.getHeaders().add(key, value));
    }
}
