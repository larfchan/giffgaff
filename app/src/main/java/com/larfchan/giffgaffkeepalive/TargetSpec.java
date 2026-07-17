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

    static TargetSpec parsePublicIp(String rawHost, int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("UDP 端口必须是 1–65535 的整数");
        }

        String host = rawHost == null ? "" : rawHost.trim();
        if (host.startsWith("[") && host.endsWith("]") && host.length() > 2) {
            host = host.substring(1, host.length() - 1);
        }
        if (host.isEmpty()) {
            throw new IllegalArgumentException("请填写数字公网 IP");
        }
        if (host.contains("%")) {
            throw new IllegalArgumentException("不支持带网络区域标识的 IPv6 地址");
        }
        if (!AddressPolicy.looksNumericAddress(host)) {
            throw new IllegalArgumentException("请输入数字公网 IP，不要填写域名或网址");
        }
        if (host.indexOf(':') >= 0 && !host.matches("[0-9A-Fa-f:.]+")) {
            throw new IllegalArgumentException("数字 IPv6 格式不正确");
        }

        final InetAddress address;
        try {
            address = AddressPolicy.parseNumericPublicAddress(host);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("数字 IP 格式不正确");
        }
        return new TargetSpec(host, port, address);
    }
}
