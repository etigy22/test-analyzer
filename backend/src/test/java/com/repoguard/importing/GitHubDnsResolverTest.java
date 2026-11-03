package com.repoguard.importing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class GitHubDnsResolverTest {
    @ParameterizedTest
    @ValueSource(strings = {"0.0.0.0", "10.1.1.1", "100.64.0.1", "100.127.255.255", "127.0.0.1",
            "169.254.169.254", "172.16.0.1", "172.31.255.255", "192.168.0.1", "192.0.0.1",
            "192.0.2.1", "192.88.99.1", "198.18.0.1", "198.19.255.255", "198.51.100.1",
            "203.0.113.1", "224.0.0.1", "240.0.0.1", "255.255.255.255", "::", "::1",
            "fc00::1", "fd00::1", "fe80::1", "ff02::1", "64:ff9b::7f00:1", "2001::1",
            "2001:2::1", "2001:db8::1", "2002:7f00:1::1", "3ffe::1", "3fff::1", "::ffff:127.0.0.1"})
    void rejectsSpecialUseDestinations(String ip) throws Exception {
        InetAddress address = InetAddress.getByName(ip);
        assertThat(PublicAddressPolicy.isPublic(address)).as(ip).isFalse();
        GitHubDnsResolver resolver = new GitHubDnsResolver(host -> new InetAddress[]{address});
        assertThatThrownBy(() -> resolver.resolve("codeload.github.com"))
                .isInstanceOf(UnknownHostException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"140.82.112.10", "185.199.108.133", "8.8.8.8", "2606:50c0:8000::154"})
    void acceptsPublicUnicast(String ip) throws Exception {
        assertThat(PublicAddressPolicy.isPublic(InetAddress.getByName(ip))).isTrue();
    }

    @Test
    void rejectsEntireAnswerIfAnyAddressIsPrivate() throws Exception {
        GitHubDnsResolver resolver = new GitHubDnsResolver(host -> new InetAddress[]{
                InetAddress.getByName("140.82.112.10"), InetAddress.getByName("127.0.0.1")});
        assertThatThrownBy(() -> resolver.resolve("codeload.github.com"))
                .isInstanceOf(UnknownHostException.class);
    }

    @Test
    void rejectsOtherHostsBeforeDnsLookup() {
        AtomicInteger calls = new AtomicInteger();
        GitHubDnsResolver resolver = new GitHubDnsResolver(host -> {
            calls.incrementAndGet();
            return new InetAddress[0];
        });
        assertThatThrownBy(() -> resolver.resolve("github.com.evil.example"))
                .isInstanceOf(UnknownHostException.class);
        assertThat(calls).hasValue(0);
        assertThatThrownBy(() -> resolver.resolve("codeload.github.com"))
                .isInstanceOf(UnknownHostException.class);
    }

    @Test
    void validatesEachConnectionResolutionAndKeepsOriginalTlsHostname() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        GitHubDnsResolver resolver = new GitHubDnsResolver(host -> new InetAddress[]{
                InetAddress.getByName(calls.getAndIncrement() == 0 ? "140.82.112.10" : "127.0.0.1")});
        assertThat(resolver.resolve("codeload.github.com")).hasSize(1);
        assertThatThrownBy(() -> resolver.resolve("codeload.github.com"))
                .isInstanceOf(UnknownHostException.class);
        assertThat(resolver.resolveCanonicalHostname("codeload.github.com")).isEqualTo("codeload.github.com");
        assertThatThrownBy(() -> resolver.resolveCanonicalHostname("evil.example"))
                .isInstanceOf(UnknownHostException.class);
    }
}
