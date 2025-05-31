package org.kiwiproject.jersey.client.net.ssl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.kiwiproject.test.junit.jupiter.params.provider.RandomStringSource;

@DisplayName("NoopHostnameVerifier")
class NoopHostnameVerifierTest {

    @ParameterizedTest
    @RandomStringSource(prefix = "www.", suffix = ".com")
    void shouldVerifyAnyHostname(String hostname) {
        assertThat(new NoopHostnameVerifier().verify(hostname, null)).isTrue();
    }

    @Test
    void shouldReturnReadable_ToString() {
        assertThat(new NoopHostnameVerifier()).hasToString("NO_OP");
    }
}
