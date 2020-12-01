package org.kiwiproject.jersey.client.exception;

import static org.kiwiproject.base.KiwiStrings.f;

import org.kiwiproject.jersey.client.RegistryAwareClient;
import org.kiwiproject.jersey.client.ServiceIdentifier;

import java.util.Optional;

/**
 * Runtime exception that indicates that the {@link RegistryAwareClient} was unable to find the requested service
 * in the registry service.
 */
public class MissingServiceRuntimeException extends RuntimeException {

    /**
     * Creates a new {@link MissingServiceRuntimeException} with a given message.
     *
     * @param message the message to attach to the exception
     */
    public MissingServiceRuntimeException(String message) {
        super(message);
    }

    /**
     * Creates a new {@link MissingServiceRuntimeException} based on the given {@link ServiceIdentifier}.
     *
     * @param identifier the service identifier that contains information about the service that the lookup was
     *                   attempted for.
     * @return the created {@link MissingServiceRuntimeException}
     */
    public static MissingServiceRuntimeException from(ServiceIdentifier identifier) {
        var message = f("No service instances found with name {}, preferred version {}, min version {}",
                identifier.getServiceName(),
                Optional.ofNullable(identifier.getPreferredVersion()).orElse("[latest]"),
                Optional.ofNullable(identifier.getMinimumVersion()).orElse("[none]")
        );

        return new MissingServiceRuntimeException(message);
    }
}
