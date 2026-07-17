package com.larfchan.giffgaffkeepalive;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.InetAddress;

public class AddressPolicyTest {
    @Test
    public void acceptsPublicIpv4() throws Exception {
        assertTrue(AddressPolicy.isPublicInternetAddress(
                InetAddress.getByName("1.1.1.1")));
    }

    @Test
    public void rejectsPrivateAndReservedIpv4() throws Exception {
        assertFalse(AddressPolicy.isPublicInternetAddress(
                InetAddress.getByName("192.168.1.1")));
        assertFalse(AddressPolicy.isPublicInternetAddress(
                InetAddress.getByName("100.64.1.1")));
        assertFalse(AddressPolicy.isPublicInternetAddress(
                InetAddress.getByName("203.0.113.1")));
    }

    @Test
    public void acceptsPublicIpv6() throws Exception {
        assertTrue(AddressPolicy.isPublicInternetAddress(
                InetAddress.getByName("2606:4700:4700::1111")));
    }

    @Test
    public void rejectsLocalAndDocumentationIpv6() throws Exception {
        assertFalse(AddressPolicy.isPublicInternetAddress(
                InetAddress.getByName("fd00::1")));
        assertFalse(AddressPolicy.isPublicInternetAddress(
                InetAddress.getByName("2001:db8::1")));
    }
}

