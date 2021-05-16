package org.kiwiproject.jersey.client.util;

import lombok.experimental.UtilityClass;

import javax.ws.rs.client.Client;

@UtilityClass
public class JerseyTestHelpers {

    public static boolean isFeatureRegistered(Client client, Class<?> component) {
        return client.getConfiguration().isRegistered(component);
    }
}
