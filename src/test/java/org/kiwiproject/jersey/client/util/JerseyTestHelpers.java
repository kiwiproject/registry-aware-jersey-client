package org.kiwiproject.jersey.client.util;

import jakarta.ws.rs.client.Client;
import lombok.experimental.UtilityClass;

@UtilityClass
public class JerseyTestHelpers {

    public static boolean isFeatureRegistered(Client client, Class<?> component) {
        return client.getConfiguration().isRegistered(component);
    }
}
