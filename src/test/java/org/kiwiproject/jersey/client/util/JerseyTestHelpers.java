package org.kiwiproject.jersey.client.util;

import jakarta.ws.rs.client.Client;
import lombok.experimental.UtilityClass;

@UtilityClass
public class JerseyTestHelpers {

    /**
     * Checks if a component of the specified class type is registered with the client.
     *
     * @see jakarta.ws.rs.core.Configuration#isRegistered(Class)
     */
    public static boolean isFeatureRegisteredByClass(Client client, Class<?> component) {
        return client.getConfiguration().isRegistered(component);
    }

    /**
     * Checks if the specific component instance is registered with the client.
     *
     * @see jakarta.ws.rs.core.Configuration#isRegistered(Object)
     */
    public static boolean isFeatureRegisteredByObject(Client client, Object component) {
        return client.getConfiguration().isRegistered(component);
    }
}
