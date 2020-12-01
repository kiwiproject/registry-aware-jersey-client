package org.kiwiproject.jersey.client.exception;

import static org.kiwiproject.base.KiwiStrings.f;

import org.kiwiproject.jersey.client.ServiceIdentifier;

import java.util.Optional;

public class MissingServiceRuntimeException extends RuntimeException {
    public MissingServiceRuntimeException(String message) {
        super(message);
    }

    public static MissingServiceRuntimeException newMissingServiceRuntimeException(ServiceIdentifier identifier) {
        var message = f("No service instances found with name {}, preferred version {}, min version {}",
                identifier.getServiceName(),
                Optional.ofNullable(identifier.getPreferredVersion()).orElse("[latest]"),
                Optional.ofNullable(identifier.getMinimumVersion()).orElse("[none]")
        );

        return new MissingServiceRuntimeException(message);
    }
}
