package com.larfchan.giffgaffkeepalive;

import java.util.concurrent.ThreadLocalRandom;

final class DnsQuery {
    private static final byte[] ROOT_A_QUERY_TEMPLATE = new byte[]{
            0x00, 0x00,
            0x01, 0x00,
            0x00, 0x01,
            0x00, 0x00,
            0x00, 0x00,
            0x00, 0x00,
            0x00,
            0x00, 0x01,
            0x00, 0x01
    };

    private DnsQuery() {
    }

    static byte[] rootA() {
        byte[] query = ROOT_A_QUERY_TEMPLATE.clone();
        int transactionId = ThreadLocalRandom.current().nextInt(1 << 16);
        query[0] = (byte) (transactionId >>> 8);
        query[1] = (byte) transactionId;
        return query;
    }
}
