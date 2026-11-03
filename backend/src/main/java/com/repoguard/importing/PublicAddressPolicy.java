package com.repoguard.importing;

import java.net.InetAddress;

/** Conservative public unicast policy; reject special-use and transition networks. */
public final class PublicAddressPolicy {
    private PublicAddressPolicy() { }

    public static boolean isPublic(InetAddress address) {
        byte[] bytes = address.getAddress();
        int a = Byte.toUnsignedInt(bytes[0]);
        int b = Byte.toUnsignedInt(bytes[1]);
        int c = Byte.toUnsignedInt(bytes[2]);
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        if (bytes.length == 4) {
            return !(a == 0 || a == 10 || a == 127 || a >= 224
                    || a == 100 && b >= 64 && b <= 127
                    || a == 169 && b == 254
                    || a == 172 && b >= 16 && b <= 31
                    || a == 192 && (b == 168 || b == 0 && (c == 0 || c == 2)
                        || b == 88 && c == 99)
                    || a == 198 && (b == 18 || b == 19 || b == 51 && c == 100)
                    || a == 203 && b == 0 && c == 113);
        }
        // Only allocated global-unicast space, excluding protocol assignments,
        // documentation space and 6to4 (which embeds an unchecked IPv4 target).
        return bytes.length == 16 && (a & 0xe0) == 0x20
                && !(a == 0x20 && b == 0x01 && (c < 2 || c == 0x0d && Byte.toUnsignedInt(bytes[3]) == 0xb8))
                && !(a == 0x20 && b == 0x02)
                && !(a == 0x3f && b == 0xfe)
                && !(a == 0x3f && b == 0xff && (c & 0xf0) == 0);
    }
}
