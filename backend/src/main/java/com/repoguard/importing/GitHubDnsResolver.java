package com.repoguard.importing;

import org.apache.hc.client5.http.DnsResolver;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Used by the connection manager itself: it connects to these validated addresses. */
final class GitHubDnsResolver implements DnsResolver {
    @FunctionalInterface
    interface Lookup {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final Lookup lookup;

    GitHubDnsResolver() {
        this(InetAddress::getAllByName);
    }

    GitHubDnsResolver(Lookup lookup) {
        this.lookup = lookup;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        requireAllowedHost(host);
        InetAddress[] addresses = lookup.resolve(host);
        if (addresses == null || addresses.length == 0) {
            throw new UnknownHostException("GitHub archive DNS returned no addresses.");
        }
        for (InetAddress address : addresses) {
            if (address == null || !PublicAddressPolicy.isPublic(address)) {
                throw new UnknownHostException("GitHub archive DNS returned a prohibited address.");
            }
        }
        return addresses.clone();
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        requireAllowedHost(host);
        // Keep the original hostname for TLS; never use reverse DNS to change it.
        return host;
    }

    private void requireAllowedHost(String host) throws UnknownHostException {
        if (!"codeload.github.com".equals(host)) {
            throw new UnknownHostException("Only the GitHub archive host is allowed.");
        }
    }
}
