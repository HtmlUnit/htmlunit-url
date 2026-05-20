/*
 * Copyright 2023 - Stephane Bastian - stephane.bastian.dev@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.htmlunit.url.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.htmlunit.url.Url;
import org.htmlunit.url.ValidationError;
import org.htmlunit.url.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for the Host API.
 *
* @author Stephane Bastian
* @author Ronald Brill
 */
public class TestHost {
    static Collection<Map<String, Object>> invalidIpv4Data() {
        return TestUtils.readJsonFile("ipv4-invalid.json");
    }

    static Collection<Map<String, Object>> invalidIpv6Data() {
        return TestUtils.readJsonFile("ipv6-invalid.json");
    }

    static Collection<Map<String, Object>> validIpv4Data() {
        return TestUtils.readJsonFile("ipv4-valid.json");
    }

    static Collection<Map<String, Object>> validIpv6Data() {
        return TestUtils.readJsonFile("ipv6-valid.json");
    }

    /**
     * EXAMPLE.COM example.com (domain) EXAMPLE.COM (opaque host) example%2Ecom example%2Ecom (opaque
     * host) faß.example xn--fa-hia.example (domain) fa%C3%9F.example (opaque host) 0 0.0.0.0 (IPv4) 0
     * (opaque host) %30 %30 (opaque host) 0x 0x (opaque host) 0xffffffff 255.255.255.255 (IPv4)
     * 0xffffffff (opaque host) [0:0::1] [::1] (IPv6) [0:0::1%5D Failure [0:0::%31] 09 Failure 09
     * (opaque host) example.255 example.255 (opaque host) example^example Failure
     */
    @Test
    public void hostParser() {
        // isNotSpecial = false
        assertInstanceOf(Domain.class,      HostParser.parse("EXAMPLE.COM",    false, error -> {}));
        assertEquals("example.com",         HostParser.parse("EXAMPLE.COM",    false, error -> {}).toString());
        assertInstanceOf(Domain.class,      HostParser.parse("example%2Ecom",  false, error -> {}));
        assertEquals("example.com",         HostParser.parse("example%2Ecom",  false, error -> {}).toString());
        assertInstanceOf(Domain.class,      HostParser.parse("faß.example",    false, error -> {}));
        assertEquals("xn--fa-hia.example",  HostParser.parse("faß.example",    false, error -> {}).toString());
        assertInstanceOf(Ipv4Address.class, HostParser.parse("0",              false, error -> {}));
        assertEquals("0.0.0.0",             HostParser.parse("0",              false, error -> {}).toString());
        assertInstanceOf(Ipv4Address.class, HostParser.parse("%30",            false, error -> {}));
        assertEquals("0.0.0.0",             HostParser.parse("%30",            false, error -> {}).toString());
        assertInstanceOf(Ipv4Address.class, HostParser.parse("0x",             false, error -> {}));
        assertEquals("0.0.0.0",             HostParser.parse("0x",             false, error -> {}).toString());
        assertInstanceOf(Ipv4Address.class, HostParser.parse("0xffffffff",     false, error -> {}));
        assertEquals("255.255.255.255",     HostParser.parse("0xffffffff",     false, error -> {}).toString());
        assertInstanceOf(Ipv6Address.class, HostParser.parse("[0:0::1]",       false, error -> {}));
        assertEquals("::1",                 HostParser.parse("[0:0::1]",       false, error -> {}).toString());

        // isNotSpecial = true
        assertInstanceOf(OpaqueHost.class,  HostParser.parse("EXAMPLE.COM",    true, error -> {}));
        assertEquals("EXAMPLE.COM",         HostParser.parse("EXAMPLE.COM",    true, error -> {}).toString());
        assertInstanceOf(OpaqueHost.class,  HostParser.parse("example%2Ecom",  true, error -> {}));
        assertEquals("example%2Ecom",       HostParser.parse("example%2Ecom",  true, error -> {}).toString());
        assertInstanceOf(OpaqueHost.class,  HostParser.parse("faß.example",    true, error -> {}));
        assertEquals("fa%C3%9F.example",    HostParser.parse("faß.example",    true, error -> {}).toString());
        assertInstanceOf(OpaqueHost.class,  HostParser.parse("0",              true, error -> {}));
        assertEquals("0",                   HostParser.parse("0",              true, error -> {}).toString());
        assertInstanceOf(OpaqueHost.class,  HostParser.parse("%30",            true, error -> {}));
        assertEquals("%30",                 HostParser.parse("%30",            true, error -> {}).toString());
        assertInstanceOf(OpaqueHost.class,  HostParser.parse("0x",             true, error -> {}));
        assertEquals("0x",                  HostParser.parse("0x",             true, error -> {}).toString());
        assertInstanceOf(OpaqueHost.class,  HostParser.parse("0xffffffff",     true, error -> {}));
        assertEquals("0xffffffff",          HostParser.parse("0xffffffff",     true, error -> {}).toString());
        assertInstanceOf(Ipv6Address.class, HostParser.parse("[0:0::1]",       true, error -> {}));
        assertEquals("::1",                 HostParser.parse("[0:0::1]",       true, error -> {}).toString());

        // failure
        assertThrows(ValidationException.class, () -> HostParser.parse("xn--", false, error -> {}));
    }

    @Test
    public void serializeUrlSearchParams() {
        Url url = Url.create("http://myhost.com?a=a2&b=b1&a=a1&c=c1");
        String result = url.searchParams().toString();
        assertFalse(result.isEmpty());
        assertEquals("a=a2&b=b1&a=a1&c=c1", result);
    }

    @ParameterizedTest
    @MethodSource("invalidIpv4Data")
    public void testInvalidIpv4Data(Map<String, String> testData) {
        String ip = testData.get("ip");
        assertThrows(ValidationException.class, () -> {
            List<ValidationError> errors = new ArrayList<>();
            Ipv4Address result = HostParser.parseIpv4(ip, errors::add);
            assertTrue(!errors.isEmpty() || result == null);
            assertTrue(errors.size() > 0);
        });
    }

    @ParameterizedTest
    @MethodSource("invalidIpv6Data")
    public void testInvalidIpv6(Map<String, String> testData) {
        String ip = testData.get("ip");
        assertThrows(ValidationException.class, () -> HostParser.parseIpv6(ip));
    }

    @ParameterizedTest
    @MethodSource("validIpv4Data")
    public void testValidIpv4Data(Map<String, String> testData) {
        String ip = testData.get("ip");
        String ipExpected = testData.get("expected");
        assertDoesNotThrow(() -> {
            Ipv4Address result = HostParser.parseIpv4(ip, error -> {});
            assertNotNull(result);
            StringBuilder formattedIp = new StringBuilder();
            SerializerHelper.serializeHost(result, formattedIp);
            assertEquals(ipExpected, formattedIp.toString());
        });
    }

    @ParameterizedTest
    @MethodSource("validIpv6Data")
    public void testValidIpv6(Map<String, String> testData) {
        String ip = testData.get("ip");
        String ipExpected = testData.get("expected");
        assertDoesNotThrow(() -> {
            Ipv6Address result = HostParser.parseIpv6(ip);
            assertNotNull(result);
            StringBuilder formattedIp = new StringBuilder();
            SerializerHelper.serializeHost(result, formattedIp);
            assertEquals(ipExpected, formattedIp.toString());
        });
    }
}