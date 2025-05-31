package org.kiwiproject.jersey.client.dropwizard;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jersey.jackson.JacksonMessageBodyProvider;
import jakarta.ws.rs.client.Client;
import lombok.experimental.UtilityClass;

/**
 * Utilities for adding Dropwizard features to JAX-RS Clients.
 * <p>
 * This requires {@code io.dropwizard:dropwizard-client} as a dependency.
 */
@UtilityClass
public class DropwizardClients {

    /**
     * Adds a {@link JacksonMessageBodyProvider} to the given {@link Client}.
     * <p>
     * See the note explaining when this is not needed in the description of
     * {@link #addJacksonMessageBodyProvider(Client, JacksonMessageBodyProvider)}.
     *
     * @param client the Client to add the {@link JacksonMessageBodyProvider}
     * @param mapper the {@link ObjectMapper} that will be supplied to the {@link JacksonMessageBodyProvider}
     * @return the Client argument, for method chaining
     * @see #addJacksonMessageBodyProvider(Client, JacksonMessageBodyProvider)
     */
    public static Client addJacksonMessageBodyProvider(Client client, ObjectMapper mapper) {
        return addJacksonMessageBodyProvider(client, new JacksonMessageBodyProvider(mapper));
    }

    /**
     * Adds the {@link JacksonMessageBodyProvider} to the given {@link Client}.
     * <p>
     * Note that this is not necessary when using {@link DropwizardManagedClientBuilder} to build clients, because
     * Dropwizard adds {@link JacksonMessageBodyProvider}. It is mainly useful in a Dropwizard application when using
     * {@link org.kiwiproject.jersey.client.RegistryAwareClientBuilder RegistryAwareClientBuilder} to build clients,
     * since it is not aware of Dropwizard, and you need to customize JSON serialization via a custom
     * {@link ObjectMapper}. For example, if you use milliseconds for all timestamps instead of Jackson's default
     * serialization of Java date/time objects like {@link java.time.ZonedDateTime}, you need to configure the
     * ObjectMapper to read and write timestamps as milliseconds.
     *
     * @param client   the Client to add the {@link JacksonMessageBodyProvider}
     * @param provider the {@link JacksonMessageBodyProvider} to add
     * @return the Client argument, for method chaining
     */
    public static Client addJacksonMessageBodyProvider(Client client, JacksonMessageBodyProvider provider) {
        return client.register(provider);
    }
}
