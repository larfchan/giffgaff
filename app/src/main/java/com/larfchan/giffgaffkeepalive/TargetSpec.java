package com.larfchan.giffgaffkeepalive;

import java.net.InetAddress;
import java.net.UnknownHostException;

final class TargetSpec {
    final String host;
    final int port;
    final InetAddress numericAddress;

    private TargetSpec(String host, int port, InetAddress numericAddress) {
        this.host = host;
        this.port = port;
        this.numericAddress = numericAddress;
    }

    static TargetSpec parse(String rawHost, String rawPort) throws IllegalArgumentException {
        String host = AddressPolicy.normalizeHost(rawHost);

        final int port;
        try {
            port = Integer.parseInt(rawPort == null ? "" : rawPort.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("UDP 端口必须是 1–65535 的整数");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("UDP 端口必须是 1–65535 的整数");
        }

        InetAddress numericAddress = null;
        if (AddressPolicy.looksNumericAddress(host)) {
            try {
                numericAddress = AddressPolicy.parseNumericPublicAddress(host);
            } catch (UnknownHostException exception) {
                throw new IllegalArgumentException("数字 IP 格式不正确");
            }
        }
        return new TargetSpec(host, port, numericAddress);
    }

    boolean usesDns() {
        return numericAddress == null;
    }
}

