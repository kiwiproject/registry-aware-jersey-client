package org.kiwiproject.jersey.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ClientBuilders")
class ClientBuildersTest {

    @Test
    void jerseyShouldReturnRegistryAwareClientBuilder() {
        assertThat(ClientBuilders.jersey()).isInstanceOf(RegistryAwareClientBuilder.class);
    }
}
