package com.larfchan.giffgaffkeepalive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class TargetSpecTest {
    @Test
    public void acceptsDefaultPublicDnsIp() {
        TargetSpec target = TargetSpec.parsePublicIp("8.8.8.8", 53);

        assertEquals("8.8.8.8", target.host);
        assertEquals(53, target.port);
        assertEquals("8.8.8.8", target.numericAddress.getHostAddress());
    }

    @Test
    public void rejectsHostnameAndPrivateIp() {
        assertRejected("dns.google");
        assertRejected("192.168.1.1");
        assertRejected("８.８.８.８");
    }

    private static void assertRejected(String value) {
        try {
            TargetSpec.parsePublicIp(value, 53);
            fail("Expected target to be rejected: " + value);
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
