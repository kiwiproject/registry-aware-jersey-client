package org.kiwiproject.jersey.client.net.ssl;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

/**
 * This is a no-op {@link HostnameVerifier} that disables hostname verification.
 */
public class NoopHostnameVerifier implements HostnameVerifier {

    /**
     * Always returns {@code true}.
     *
     * @param hostname the host name
     * @param session SSLSession used on the connection to host
     * @return {@code true}
     */
    @Override
    public boolean verify(String hostname, SSLSession session) {
        return true;
    }

    @Override
    public final String toString() {
        return "NO_OP";
    }
}
