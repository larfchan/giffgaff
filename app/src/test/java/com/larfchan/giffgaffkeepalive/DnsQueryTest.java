package com.larfchan.giffgaffkeepalive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import org.junit.Test;

public class DnsQueryTest {
    @Test
    public void buildsMinimalRootAQuery() {
        byte[] query = DnsQuery.rootA();

        assertEquals(17, query.length);
        assertEquals(0x01, query[2] & 0xff);
        assertEquals(0x01, query[5] & 0xff);
        assertEquals(0x00, query[12] & 0xff);
        assertEquals(0x01, query[14] & 0xff);
        assertEquals(0x01, query[16] & 0xff);
    }

    @Test
    public void returnsIndependentPayloads() {
        byte[] first = DnsQuery.rootA();
        byte[] second = DnsQuery.rootA();

        assertNotSame(first, second);
        first[2] = 0;
        assertEquals(0x01, second[2] & 0xff);
    }
}
