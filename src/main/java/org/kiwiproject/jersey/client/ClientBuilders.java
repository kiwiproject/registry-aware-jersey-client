package org.kiwiproject.jersey.client;

import lombok.experimental.UtilityClass;

/**
 * The starting point for creating {@link RegistryAwareClient} instances
 */
@UtilityClass
public class ClientBuilders {

    /**
     * Returns a {@link ClientBuilder} that can be used to create JAX-RS clients that use Jersey as their
     * implementation and are aware of a service registry for service lookups.
     *
     * @return The builder for a {@link RegistryAwareClient}
     */
    public static ClientBuilder jersey() {
        return new RegistryAwareClientBuilder();
    }
}
