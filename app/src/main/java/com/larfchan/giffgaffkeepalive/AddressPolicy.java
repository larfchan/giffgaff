package com.larfchan.giffgaffkeepalive;

import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

final class AddressPolicy {
    private AddressPolicy() {
    }

    static String normalizeHost(String raw) throws IllegalArgumentException {
        if (raw == null) {
            throw new IllegalArgumentException("请填写目标公网 IP 或主机名");
        }

        String host = raw.trim();
        if (host.isEmpty()) {
            throw new IllegalArgumentException("请填写目标公网 IP 或主机名");
        }
        if (host.startsWith("[") && host.endsWith("]") && host.length() > 2) {
            host = host.substring(1, host.length() - 1);
        }
        if (host.contains("://") || host.contains("/") || host.contains("?")
                || host.contains("#") || host.contains("@")) {
            throw new IllegalArgumentException("这里只填主机名或公网 IP，不要填写 URL、路径或账号");
        }
        if (host.contains("%")) {
            throw new IllegalArgumentException("不支持带网络区域标识的 IPv6 地址");
        }
        if (host.matches("[0-9.]+") && !isIpv4Syntax(host)) {
            throw new IllegalArgumentException("数字 IPv4 格式不正确");
        }

        if (looksNumericAddress(host)) {
            return host;
        }

        final String ascii;
        try {
            ascii = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES)
                    .toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("主机名格式不正确");
        }

        if (ascii.isEmpty() || ascii.length() > 253) {
            throw new IllegalArgumentException("主机名格式不正确");
        }
        String[] labels = ascii.split("\\.", -1);
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63
                    || label.startsWith("-") || label.endsWith("-")) {
                throw new IllegalArgumentException("主机名格式不正确");
            }
        }
        return ascii;
    }

    static boolean looksNumericAddress(String host) {
        return isIpv4Syntax(host) || (host != null && host.indexOf(':') >= 0);
    }

    static InetAddress parseNumericPublicAddress(String host)
            throws UnknownHostException, IllegalArgumentException {
        if (!looksNumericAddress(host)) {
            throw new IllegalArgumentException("目标不是数字 IP");
        }
        InetAddress address = InetAddress.getByName(host);
        if (!isPublicInternetAddress(address)) {
            throw new IllegalArgumentException("目标必须是公网 IP，不能使用局域网、回环或保留地址");
        }
        return address;
    }

    static InetAddress selectPublicInternetAddress(InetAddress[] addresses)
            throws IllegalArgumentException {
        InetAddress firstIpv6 = null;
        for (InetAddress address : addresses) {
            if (!isPublicInternetAddress(address)) {
                continue;
            }
            if (address instanceof Inet4Address) {
                return address;
            }
            if (firstIpv6 == null && address instanceof Inet6Address) {
                firstIpv6 = address;
            }
        }
        if (firstIpv6 != null) {
            return firstIpv6;
        }
        throw new IllegalArgumentException("该主机名没有解析到公网 IP");
    }

    static boolean isPublicInternetAddress(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }

        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address && bytes.length == 4) {
            int a = bytes[0] & 0xff;
            int b = bytes[1] & 0xff;
            int c = bytes[2] & 0xff;

            if (a == 0 || a == 10 || a == 127 || a >= 224) {
                return false;
            }
            if (a == 100 && b >= 64 && b <= 127) {
                return false; // Carrier-grade NAT.
            }
            if (a == 169 && b == 254) {
                return false;
            }
            if (a == 172 && b >= 16 && b <= 31) {
                return false;
            }
            if (a == 192 && ((b == 0 && (c == 0 || c == 2))
                    || b == 168 || (b == 88 && c == 99))) {
                return false;
            }
            if (a == 198 && (b == 18 || b == 19 || (b == 51 && c == 100))) {
                return false;
            }
            if (a == 203 && b == 0 && c == 113) {
                return false;
            }
            return true;
        }

        if (address instanceof Inet6Address && bytes.length == 16) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            if ((first & 0xfe) == 0xfc) {
                return false; // Unique-local fc00::/7.
            }
            if (first == 0xfe && (second & 0xc0) == 0x80) {
                return false; // Link-local fe80::/10.
            }
            if (first == 0x20 && second == 0x01
                    && (bytes[2] & 0xff) == 0x0d && (bytes[3] & 0xff) == 0xb8) {
                return false; // Documentation 2001:db8::/32.
            }
            return true;
        }

        return false;
    }

    private static boolean isIpv4Syntax(String value) {
        if (value == null) {
            return false;
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            for (int index = 0; index < part.length(); index++) {
                char character = part.charAt(index);
                if (character < '0' || character > '9') {
                    return false;
                }
            }
            try {
                if (Integer.parseInt(part) > 255) {
                    return false;
                }
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return true;
    }
}
