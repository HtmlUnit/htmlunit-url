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

import org.htmlunit.url.Url;
import org.htmlunit.url.ValidationError;
import org.htmlunit.url.ValidationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link Url} API.
 *
* @author Stephane Bastian
* @author Ronald Brill
 */
public class TestUrlApi {
    @Test
    public void createEmptyUrl() {
        Url url = Url.create();
        Assertions.assertNotNull(url);
        Assertions.assertTrue(url.hash().isEmpty());
        Assertions.assertTrue(url.host().isEmpty());
        Assertions.assertTrue(url.hostname().isEmpty());
        Assertions.assertEquals(":", url.href());
        Assertions.assertEquals("null", url.origin());
        Assertions.assertTrue(url.password().isEmpty());
        Assertions.assertTrue(url.pathname().isEmpty());
        Assertions.assertEquals(":", url.protocol());
        Assertions.assertEquals("", url.port());
        Assertions.assertTrue(url.search().isEmpty());
        Assertions.assertNotNull(url.searchParams());
        Assertions.assertEquals(0, url.searchParams().size());
        Assertions.assertTrue(url.username().isEmpty());
    }

    @Test
    public void parseUrl() {
        Url url = Url.create("http://www.myurl.com/path1?a=1&b=2#hash1");
        Assertions.assertNotNull(url);
        Assertions.assertEquals("#hash1", url.hash());
        Assertions.assertEquals("www.myurl.com", url.host());
        Assertions.assertEquals("www.myurl.com", url.hostname());
        Assertions.assertEquals("http://www.myurl.com/path1?a=1&b=2#hash1", url.href());
        Assertions.assertEquals("http://www.myurl.com", url.origin());
        Assertions.assertTrue(url.password().isEmpty());
        Assertions.assertEquals("/path1", url.pathname());
        Assertions.assertEquals("", url.port());
        Assertions.assertEquals("http:", url.protocol());
        Assertions.assertEquals("?a=1&b=2", url.search());
        Assertions.assertNotNull(url.searchParams());
        Assertions.assertEquals(2, url.searchParams().size());
        Assertions.assertTrue(url.username().isEmpty());
    }

    @Test
    public void parseUrlThrowingException() {
        {
            ValidationException ex = Assertions.assertThrows(ValidationException.class,
                    () -> Url.create("http://xn--/"));
            Assertions.assertEquals(ValidationError.DOMAIN_TO_ASCII.description(), ex.getMessage());
        }
        {
            ValidationException ex = Assertions.assertThrows(ValidationException.class,
                    () -> Url.create("https://exa%23mple.org"));
            Assertions.assertEquals(ValidationError.DOMAIN_INVALID_CODEPOINT.description(), ex.getMessage());
        }
        {
            ValidationException ex = Assertions.assertThrows(ValidationException.class,
                    () -> Url.create("foo://exa[mple.org"));
            Assertions.assertEquals(ValidationError.HOST_INVALID_CODEPOINT.description(), ex.getMessage());
        }
        {
            ValidationException ex = Assertions.assertThrows(ValidationException.class,
                    () -> Url.create("https://1.2.3.4.5/"));
            Assertions.assertEquals(ValidationError.IPV4_TOO_MANY_PARTS.description(), ex.getMessage());
        }
        {
            ValidationException ex = Assertions.assertThrows(ValidationException.class,
                    () -> Url.create("https://test.42"));
            Assertions.assertEquals(ValidationError.IPV4_NON_NUMERIC_PART.description(), ex.getMessage());
        }
        {
            ValidationException ex = Assertions.assertThrows(ValidationException.class,
                    () -> Url.create("https://255.255.4000.1"));
            Assertions.assertEquals(ValidationError.IPV4_OUT_OF_RANGE_PART.description(), ex.getMessage());
        }
        {
            ValidationException ex = Assertions.assertThrows(ValidationException.class,
                    () -> Url.create("https://[::1"));
            Assertions.assertEquals(ValidationError.IPV6_UNCLOSED.description(), ex.getMessage());
        }
        {
            ValidationException ex = Assertions.assertThrows(ValidationException.class,
                    () -> Url.create("https://[:1]"));
            Assertions.assertEquals(ValidationError.IPV6_INVALID_COMPRESSION.description(), ex.getMessage());
        }
        {
            ValidationException ex = Assertions.assertThrows(ValidationException.class,
                    () -> Url.create("https://[1:2:3:4:5:6:7:8:9]"));
            Assertions.assertEquals(ValidationError.IPV6_TOO_MANY_PIECES.description(), ex.getMessage());
        }
        {
            ValidationException ex = Assertions.assertThrows(ValidationException.class,
                    () -> Url.create("https://[1::1::1]"));
            Assertions.assertEquals(ValidationError.IPV6_MULTIPLE_COMPRESSION.description(), ex.getMessage());
        }
        {
            ValidationException ex = Assertions.assertThrows(ValidationException.class,
                    () -> Url.create("https://[1:2:3!:4]"));
            Assertions.assertEquals(ValidationError.IPV6_INVALID_CODEPOINT.description(), ex.getMessage());
        }
        {
            ValidationException ex = Assertions.assertThrows(ValidationException.class,
                    () -> Url.create("https://[1:2:3:]"));
            Assertions.assertEquals(ValidationError.IPV6_INVALID_CODEPOINT.description(), ex.getMessage());
        }
        {
            ValidationException ex = Assertions.assertThrows(ValidationException.class,
                    () -> Url.create("https://[1:2:3]"));
            Assertions.assertEquals(ValidationError.IPV6_TOO_FEW_PIECES.description(), ex.getMessage());
        }
        {
            ValidationException ex = Assertions.assertThrows(ValidationException.class,
                    () -> Url.create("https://[1:1:1:1:1:1:1:127.0.0.1]"));
            Assertions.assertEquals(ValidationError.IPV4_IN_IPV6_TOO_MANY_PIECES.description(), ex.getMessage());
        }
        {
            ValidationException ex = Assertions.assertThrows(ValidationException.class,
                    () -> Url.create("https://[ffff::.0.0.1]"));
            Assertions.assertEquals(ValidationError.IPV4_IN_IPV6_INVALID_CODEPOINT.description(), ex.getMessage());
        }
        {
            ValidationException ex = Assertions.assertThrows(ValidationException.class,
                    () -> Url.create("https://[ffff::127.0.xyz.1]"));
            Assertions.assertEquals(ValidationError.IPV4_IN_IPV6_INVALID_CODEPOINT.description(), ex.getMessage());
        }
        {
            ValidationException ex = Assertions.assertThrows(ValidationException.class,
                    () -> Url.create("https://[ffff::127.0xyz]"));
            Assertions.assertEquals(ValidationError.IPV4_IN_IPV6_INVALID_CODEPOINT.description(), ex.getMessage());
        }
        {
            ValidationException ex = Assertions.assertThrows(ValidationException.class,
                    () -> Url.create("https://[ffff::127.00.0.1]"));
            Assertions.assertEquals(ValidationError.IPV4_IN_IPV6_INVALID_CODEPOINT.description(), ex.getMessage());
        }
        {
            ValidationException ex = Assertions.assertThrows(ValidationException.class,
                    () -> Url.create("https://[ffff::127.0.0.1.2]"));
            Assertions.assertEquals(ValidationError.IPV4_IN_IPV6_INVALID_CODEPOINT.description(), ex.getMessage());
        }
        {
            ValidationException ex = Assertions.assertThrows(ValidationException.class,
                    () -> Url.create("https://[ffff::127.0.0.4000]"));
            Assertions.assertEquals(ValidationError.IPV4_IN_IPV6_OUT_OF_RANGE_PART.description(), ex.getMessage());
        }
        {
            ValidationException ex = Assertions.assertThrows(ValidationException.class,
                    () -> Url.create("https://[ffff::127.0.0]"));
            Assertions.assertEquals(ValidationError.IPV4_IN_IPV6_TOO_FEW_PARTS.description(), ex.getMessage());
        }
        {
            ValidationException ex = Assertions.assertThrows(ValidationException.class,
                    () -> Url.create("\\uD83D\\uDCA9"));
            Assertions.assertEquals(ValidationError.MISSING_SCHEME_NON_RELATIVE_URL.description(), ex.getMessage());
        }
        {
            ValidationException ex = Assertions.assertThrows(ValidationException.class,
                    () -> Url.create("\\uD83D\\uDCA9\\", "mailto:user@example.org"));
            Assertions.assertEquals(ValidationError.MISSING_SCHEME_NON_RELATIVE_URL.description(), ex.getMessage());
        }
        {
            ValidationException ex = Assertions.assertThrows(ValidationException.class,
                    () -> Url.create("https://#fragment"));
            Assertions.assertEquals(ValidationError.HOST_MISSING.description(), ex.getMessage());
        }
        {
            ValidationException ex = Assertions.assertThrows(ValidationException.class,
                    () -> Url.create("https://:443"));
            Assertions.assertEquals(ValidationError.HOST_MISSING.description(), ex.getMessage());
        }
        {
            ValidationException ex = Assertions.assertThrows(ValidationException.class,
                    () -> Url.create("https://example.org:70000"));
            Assertions.assertEquals(ValidationError.PORT_OUT_OF_RANGE.description(), ex.getMessage());
        }
        {
            ValidationException ex = Assertions.assertThrows(ValidationException.class,
                    () -> Url.create("https://example.org:7z"));
            Assertions.assertEquals(ValidationError.PORT_INVALID.description(), ex.getMessage());
        }
    }

    @Test
    public void parseUrlWithValidationError() {
        Url url = Url.create(" \t http://www.myurl.com/path1?a=1&b=2#hash1");
        Assertions.assertEquals(ValidationError.INVALID_URL_UNIT, url.validationErrors().get(0));
        url = Url.create("https://127.0.0.1./");
        Assertions.assertEquals(ValidationError.IPV4_EMPTY_PART, url.validationErrors().get(0));
        url = Url.create("https://127.0.0x0.1");
        Assertions.assertEquals(ValidationError.IPV4_NON_DECIMAL_PART, url.validationErrors().get(0));
        url = Url.create("https://example.org/>");
        Assertions.assertEquals(ValidationError.INVALID_URL_UNIT, url.validationErrors().get(0));
        url = Url.create(" https://example.org ");
        Assertions.assertEquals(ValidationError.INVALID_URL_UNIT, url.validationErrors().get(0));
        url = Url.create("ht\ntps://example.org");
        Assertions.assertEquals(ValidationError.INVALID_URL_UNIT, url.validationErrors().get(0));
        url = Url.create("https://example.org/%s");
        Assertions.assertEquals(ValidationError.INVALID_URL_UNIT, url.validationErrors().get(0));
        url = Url.create("https:example.org");
        Assertions.assertEquals(ValidationError.SPECIAL_SCHEME_MISSING_FOLLOWING_SOLIDUS,
                url.validationErrors().get(0));
        url = Url.create("https://example.org\\\\path\\\\to\\\\file\\");
        Assertions.assertEquals(ValidationError.INVALID_REVERSE_SOLIDUS, url.validationErrors().get(0));
        url = Url.create("https://user@example.org");
        Assertions.assertEquals(ValidationError.INVALID_CREDENTIALS, url.validationErrors().get(0));
        url = Url.create("ssh://user@example.org");
        Assertions.assertEquals(ValidationError.INVALID_CREDENTIALS, url.validationErrors().get(0));
        // url = Url.create("//c:/path/to/file", "file:///c:/");
        // Assertions.assertEquals(ValidationError.FILE_INVALID_WINDOWS_DRIVE_LETTER,
        // url.validationErrors().get(0));
        url = Url.create("file://c:");
        Assertions.assertEquals(ValidationError.FILE_INVALID_WINDOWS_DRIVE_LETTER_HOST, url.validationErrors().get(0));
    }

    @Test
    public void parseRelativeUrl() {
        Url url = Url.create("path1?a=1&b=2#hash1", "http://www.myurl.com/path2?c=3&d=2#hash2");
        Assertions.assertNotNull(url);
        Assertions.assertEquals("#hash1", url.hash());
        Assertions.assertEquals("www.myurl.com", url.host());
        Assertions.assertEquals("www.myurl.com", url.hostname());
        Assertions.assertEquals("http://www.myurl.com/path1?a=1&b=2#hash1", url.href());
        Assertions.assertEquals("http://www.myurl.com", url.origin());
        Assertions.assertTrue(url.password().isEmpty());
        Assertions.assertEquals("/path1", url.pathname());
        Assertions.assertEquals("", url.port());
        Assertions.assertEquals("http:", url.protocol());
        Assertions.assertEquals("?a=1&b=2", url.search());
        Assertions.assertNotNull(url.searchParams());
        Assertions.assertEquals(2, url.searchParams().size());
        Assertions.assertTrue(url.username().isEmpty());
    }

    @Test
    public void setHash() {
        Url url = Url.create();
        // with leading #
        url.hash("#hash1");
        Assertions.assertEquals("#hash1", url.hash());
        Assertions.assertTrue(url.validationErrors().isEmpty());
        // without leading #
        url = Url.create();
        url.hash("hash2");
        Assertions.assertEquals("#hash2", url.hash());
        Assertions.assertTrue(url.validationErrors().isEmpty());
        // with percent encoded value
        url = Url.create();
        url.hash("%20hash2");
        Assertions.assertEquals("#%20hash2", url.hash());
        Assertions.assertTrue(url.validationErrors().isEmpty());
        // with leading # and percent encoded value
        url = Url.create();
        url.hash("#%20hash2");
        Assertions.assertEquals("#%20hash2", url.hash());
        Assertions.assertTrue(url.validationErrors().isEmpty());
    }

    @Test
    public void setHashWithValidationErrors() {
        Url url = Url.create();
        // bad url codepoint
        url.hash("hash3 ");
        Assertions.assertEquals(1, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.INVALID_URL_UNIT, url.validationErrors().get(0));
        Assertions.assertEquals("#hash3%20", url.hash());
        // bad url codepoint
        url = Url.create();
        url.hash(" hash3 ");
        Assertions.assertEquals(2, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.INVALID_URL_UNIT, url.validationErrors().get(0));
        Assertions.assertEquals(ValidationError.INVALID_URL_UNIT, url.validationErrors().get(1));
        Assertions.assertEquals("#%20hash3%20", url.hash());
        // bad url codepoint
        url = Url.create();
        url.hash(" #hash3 ");
        Assertions.assertEquals(3, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.INVALID_URL_UNIT, url.validationErrors().get(0));
        Assertions.assertEquals(ValidationError.INVALID_URL_UNIT, url.validationErrors().get(1));
        Assertions.assertEquals(ValidationError.INVALID_URL_UNIT, url.validationErrors().get(2));
        Assertions.assertEquals("#%20#hash3%20", url.hash());
        // bad url codepoint
        url = Url.create();
        url.hash("%h");
        Assertions.assertEquals(1, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.INVALID_URL_UNIT, url.validationErrors().get(0));
        Assertions.assertEquals("#%h", url.hash());
    }

    @Test
    public void setHost() {
        // opaque host
        Url url = Url.create();
        Assertions.assertTrue(url.validationErrors().isEmpty());
        url.host("anotherhost.io");
        Assertions.assertEquals("anotherhost.io", url.host());
        Assertions.assertTrue(url.validationErrors().isEmpty());
        // opaque host with percent encoded
        url = Url.create();
        Assertions.assertTrue(url.validationErrors().isEmpty());
        url.host("another%20host.io");
        Assertions.assertEquals("another%20host.io", url.host());
        Assertions.assertTrue(url.validationErrors().isEmpty());
        // domain
        url = Url.create("http://www.myurl.com");
        Assertions.assertEquals("www.myurl.com", url.host());
        Assertions.assertTrue(url.validationErrors().isEmpty());
        url.host("anotherhost.io");
        Assertions.assertEquals("anotherhost.io", url.host());
        Assertions.assertTrue(url.validationErrors().isEmpty());
        // ipv4
        url = Url.create("http://www.myurl.com");
        Assertions.assertEquals("www.myurl.com", url.host());
        Assertions.assertTrue(url.validationErrors().isEmpty());
        url.host("192.168.0.1");
        Assertions.assertEquals("192.168.0.1", url.host());
        Assertions.assertTrue(url.validationErrors().isEmpty());
        // ipv6
        url = Url.create("http://www.myurl.com");
        Assertions.assertEquals("www.myurl.com", url.host());
        Assertions.assertTrue(url.validationErrors().isEmpty());
        url.host("[2001:0db8:0000:85a3:0000:0000:ac1f:8001]");
        Assertions.assertEquals("[2001:db8:0:ffff85a3::ffffac1f:ffff8001]", url.host());
        Assertions.assertTrue(url.validationErrors().isEmpty());
        // short ipv6
        url = Url.create("http://www.myurl.com");
        Assertions.assertEquals("www.myurl.com", url.host());
        Assertions.assertTrue(url.validationErrors().isEmpty());
        url.host("[fe00::1]");
        Assertions.assertEquals("[fffffe00::1]", url.host());
        Assertions.assertTrue(url.validationErrors().isEmpty());
    }

    @Test
    public void setHostWithValidationError() {
        // opaque host with an invalid codepoint
        Url url = Url.create();
        Assertions.assertTrue(url.validationErrors().isEmpty());
        url.host("another%host.io");
        Assertions.assertEquals("another%host.io", url.host());
        Assertions.assertEquals(1, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.INVALID_URL_UNIT, url.validationErrors().get(0));
        // ipv4 with an empty part
        url = Url.create("https://host.com");
        Assertions.assertTrue(url.validationErrors().isEmpty());
        url.host("127.0.0.1.");
        Assertions.assertEquals(1, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.IPV4_EMPTY_PART, url.validationErrors().get(0));
        Assertions.assertEquals("127.0.0.1", url.host());
        // ipv4 with non-decimal part
        url = Url.create("https://host.com");
        url.host("127.0.0x0.1");
        Assertions.assertEquals(1, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.IPV4_NON_DECIMAL_PART, url.validationErrors().get(0));
        Assertions.assertEquals("127.0.0.1", url.host());
        // ipv4 out of range part
        url = Url.create("https://host.com");
        url.host("255.255.4000");
        Assertions.assertEquals(1, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.IPV4_OUT_OF_RANGE_PART, url.validationErrors().get(0));
        Assertions.assertEquals("255.255.15.160", url.host());
    }

    @Test
    public void setHostWithValidationFailure() {
        // opaque host with an invalid codepoint
        Url url = Url.create();
        Assertions.assertTrue(url.validationErrors().isEmpty());
        url.host("exa[mple.org");
        Assertions.assertEquals("", url.host());
        Assertions.assertEquals(1, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.HOST_INVALID_CODEPOINT, url.validationErrors().get(0));
        // domain with invalid codepoint
        url = Url.create("http://www.myurl.com");
        Assertions.assertEquals("www.myurl.com", url.host());
        Assertions.assertTrue(url.validationErrors().isEmpty());
        url.host("exa%23mple.org");
        Assertions.assertEquals(1, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.DOMAIN_INVALID_CODEPOINT, url.validationErrors().get(0));
        Assertions.assertEquals("www.myurl.com", url.host());
        // ipv4 too many parts
        url = Url.create("https://host.com");
        Assertions.assertTrue(url.validationErrors().isEmpty());
        url.host("1.2.3.4.5");
        Assertions.assertEquals(1, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.IPV4_TOO_MANY_PARTS, url.validationErrors().get(0));
        Assertions.assertEquals("host.com", url.host());
        // ipv4 non numeric part
        url = Url.create("https://host.com");
        url.host("test.42");
        Assertions.assertEquals(1, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.IPV4_NON_NUMERIC_PART, url.validationErrors().get(0));
        Assertions.assertEquals("host.com", url.host());
        // ipv4 out of range part
        url = Url.create("https://host.com");
        url.host("255.255.4000.4000");
        Assertions.assertEquals(2, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.IPV4_OUT_OF_RANGE_PART, url.validationErrors().get(0));
        Assertions.assertEquals(ValidationError.IPV4_OUT_OF_RANGE_PART, url.validationErrors().get(1));
        Assertions.assertEquals("host.com", url.host());
        // ipv6 unclosed
        url = Url.create("https://host.com");
        url.host("[::1");
        Assertions.assertEquals(1, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.IPV6_UNCLOSED, url.validationErrors().get(0));
        Assertions.assertEquals("host.com", url.host());
        // ipv6 invalid compression
        url = Url.create("https://host.com");
        url.host("[:1]");
        Assertions.assertEquals(1, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.IPV6_INVALID_COMPRESSION, url.validationErrors().get(0));
        Assertions.assertEquals("host.com", url.host());
        // ipv6 too many pieces
        url = Url.create("https://host.com");
        url.host("[1:2:3:4:5:6:7:8:9]");
        Assertions.assertEquals(1, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.IPV6_TOO_MANY_PIECES, url.validationErrors().get(0));
        Assertions.assertEquals("host.com", url.host());
        // ipv6 too many pieces
        url = Url.create("https://host.com");
        url.host("[1::1::1]");
        Assertions.assertEquals(1, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.IPV6_MULTIPLE_COMPRESSION, url.validationErrors().get(0));
        Assertions.assertEquals("host.com", url.host());
        // ipv6 invalid codepoint
        url = Url.create("https://host.com");
        url.host("[1:2:3!:4]");
        Assertions.assertEquals(1, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.IPV6_INVALID_CODEPOINT, url.validationErrors().get(0));
        Assertions.assertEquals("host.com", url.host());
        // ipv6 invalid codepoint 2
        url = Url.create("https://host.com");
        url.host("[1:2:3:]");
        Assertions.assertEquals(1, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.IPV6_INVALID_CODEPOINT, url.validationErrors().get(0));
        Assertions.assertEquals("host.com", url.host());
        // ipv6 too few pieces
        url = Url.create("https://host.com");
        url.host("[1:2:3]");
        Assertions.assertEquals(1, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.IPV6_TOO_FEW_PIECES, url.validationErrors().get(0));
        Assertions.assertEquals("host.com", url.host());
        // ipv4 in ipv6 invalid codepoint
        url = Url.create("https://host.com");
        url.host("[ffff::.0.0.1]");
        Assertions.assertEquals(1, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.IPV4_IN_IPV6_INVALID_CODEPOINT, url.validationErrors().get(0));
        Assertions.assertEquals("host.com", url.host());
        // ipv4 in ipv6 invalid codepoint 2
        url = Url.create("https://host.com");
        url.host("[ffff::127.0.xyz.1]");
        Assertions.assertEquals(1, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.IPV4_IN_IPV6_INVALID_CODEPOINT, url.validationErrors().get(0));
        Assertions.assertEquals("host.com", url.host());
        // ipv4 in ipv6 invalid codepoint 3
        url = Url.create("https://host.com");
        url.host("[ffff::127.0xyz]");
        Assertions.assertEquals(1, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.IPV4_IN_IPV6_INVALID_CODEPOINT, url.validationErrors().get(0));
        Assertions.assertEquals("host.com", url.host());
        // ipv4 in ipv6 invalid codepoint 4
        url = Url.create("https://host.com");
        url.host("[ffff::127.00.0.1]");
        Assertions.assertEquals(1, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.IPV4_IN_IPV6_INVALID_CODEPOINT, url.validationErrors().get(0));
        Assertions.assertEquals("host.com", url.host());
        // ipv4 in ipv6 invalid codepoint 5
        url = Url.create("https://host.com");
        url.host("[ffff::127.0.0.1.2]");
        Assertions.assertEquals(1, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.IPV4_IN_IPV6_INVALID_CODEPOINT, url.validationErrors().get(0));
        Assertions.assertEquals("host.com", url.host());
        // ipv4 in ipv6 out of range part
        url = Url.create("https://host.com");
        url.host("[ffff::127.0.0.4000]");
        Assertions.assertEquals(1, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.IPV4_IN_IPV6_OUT_OF_RANGE_PART, url.validationErrors().get(0));
        Assertions.assertEquals("host.com", url.host());
        // ipv4 in ipv6 out of range part
        url = Url.create("https://host.com");
        url.host("[ffff::127.0.0]");
        Assertions.assertEquals(1, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.IPV4_IN_IPV6_TOO_FEW_PARTS, url.validationErrors().get(0));
        Assertions.assertEquals("host.com", url.host());
    }

    @Test
    public void setHostname() {
        Url url = Url.create("http://www.myurl.com/path1?a=1&b=2#hash1");
        Assertions.assertEquals("www.myurl.com", url.hostname());
        url.hostname("anotherhost.io");
        Assertions.assertEquals("anotherhost.io", url.hostname());
    }

    @Test
    public void setHostnameWithValidationError() {
        Url url = Url.create("https://host.com");
        url.hostname("127.0.0.1.");
        Assertions.assertEquals(ValidationError.IPV4_EMPTY_PART, url.validationErrors().get(0));
        url = Url.create("https://host.com");
        url.hostname("127.0.0x0.1");
        Assertions.assertEquals(ValidationError.IPV4_NON_DECIMAL_PART, url.validationErrors().get(0));
    }

    @Test
    public void setHref() {
        Url url = Url.create("http://www.myurl.com/path1?a=1&b=2#hash1");
        Assertions.assertEquals("www.myurl.com", url.hostname());
        url.href("http://www.anotherurl.io/path2?c=3&d=4#hash2");
        Assertions.assertEquals("#hash2", url.hash());
        Assertions.assertEquals("www.anotherurl.io", url.host());
        Assertions.assertEquals("www.anotherurl.io", url.hostname());
        Assertions.assertEquals("http://www.anotherurl.io/path2?c=3&d=4#hash2", url.href());
        Assertions.assertEquals("http://www.anotherurl.io", url.origin());
        Assertions.assertTrue(url.password().isEmpty());
        Assertions.assertEquals("/path2", url.pathname());
        Assertions.assertEquals("http:", url.protocol());
        Assertions.assertEquals("?c=3&d=4", url.search());
        Assertions.assertNotNull(url.searchParams());
        Assertions.assertEquals(2, url.searchParams().size());
        Assertions.assertTrue(url.username().isEmpty());
    }

    @Test
    public void setPassword() {
        Url url = Url.create("http://www.myurl.com/path1?a=1&b=2#hash1");
        Assertions.assertEquals("www.myurl.com", url.hostname());
        url.password("password2");
        Assertions.assertEquals("password2", url.password());
    }

    @Test
    public void setPathname() {
        Url url = Url.create("http://www.myurl.com/path1?a=1&b=2#hash1");
        Assertions.assertEquals("www.myurl.com", url.hostname());
        url.pathname("path2/path3");
        Assertions.assertEquals("/path2/path3", url.pathname());
        url.pathname("/path4");
        Assertions.assertEquals("/path4", url.pathname());
    }

    @Test
    public void setPathnameWithValidationError() {
        Url url = Url.create();
        url.pathname(">");
        Assertions.assertEquals(1, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.INVALID_URL_UNIT, url.validationErrors().get(0));
        url = Url.create();
        url.pathname(" ");
        Assertions.assertEquals(1, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.INVALID_URL_UNIT, url.validationErrors().get(0));
        url = Url.create();
        url.pathname("%s");
        Assertions.assertEquals(1, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.INVALID_URL_UNIT, url.validationErrors().get(0));
        url = Url.create("https://example.org");
        url.pathname("\\\\path\\\\to\\\\file\\");
        Assertions.assertEquals(7, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.INVALID_REVERSE_SOLIDUS, url.validationErrors().get(0));
        Assertions.assertEquals(ValidationError.INVALID_REVERSE_SOLIDUS, url.validationErrors().get(1));
        Assertions.assertEquals(ValidationError.INVALID_REVERSE_SOLIDUS, url.validationErrors().get(2));
        Assertions.assertEquals(ValidationError.INVALID_REVERSE_SOLIDUS, url.validationErrors().get(3));
        Assertions.assertEquals(ValidationError.INVALID_REVERSE_SOLIDUS, url.validationErrors().get(4));
        Assertions.assertEquals(ValidationError.INVALID_REVERSE_SOLIDUS, url.validationErrors().get(5));
        Assertions.assertEquals(ValidationError.INVALID_REVERSE_SOLIDUS, url.validationErrors().get(6));
    }

    @Test
    public void setPort() {
        Url url = Url.create("http://www.myurl.com");
        Assertions.assertEquals("www.myurl.com", url.hostname());
        Assertions.assertTrue(url.validationErrors().isEmpty());
        url.port("443");
        Assertions.assertTrue(url.validationErrors().isEmpty());
        Assertions.assertEquals("443", url.port());
        url.port("80");
        Assertions.assertTrue(url.validationErrors().isEmpty());
        Assertions.assertEquals("", url.port());
        url.port("8080");
        Assertions.assertTrue(url.validationErrors().isEmpty());
        Assertions.assertEquals("8080", url.port());
        url.port("804zzz");
        Assertions.assertTrue(url.validationErrors().isEmpty());
        Assertions.assertEquals("804", url.port());
    }

    @Test
    public void setPortwithValidationFailure() {
        Url url = Url.create("http://www.myurl.com");
        Assertions.assertEquals("", url.port());
        url.port("70000");
        Assertions.assertEquals(1, url.validationErrors().size());
        Assertions.assertEquals(ValidationError.PORT_OUT_OF_RANGE, url.validationErrors().get(0));
    }

    @Test
    public void setProtocol() {
        Url url = Url.create("http://www.myurl.com/path1?a=1&b=2#hash1");
        Assertions.assertEquals("www.myurl.com", url.hostname());
        url.protocol("ftp");
        Assertions.assertEquals("ftp:", url.protocol());
        Assertions.assertEquals("ftp://www.myurl.com/path1?a=1&b=2#hash1", url.href());
        Assertions.assertEquals("ftp://www.myurl.com", url.origin());
    }

    @Test
    public void setProtocolWithValidationError() {
        Url url = Url.create();
        url.protocol("ht\ntps");
        Assertions.assertEquals(ValidationError.INVALID_URL_UNIT, url.validationErrors().get(0));
        url = Url.create();
        url.protocol("ht tps");
        Assertions.assertEquals(ValidationError.INVALID_URL_UNIT, url.validationErrors().get(0));
    }

    @Test
    public void setSearch() {
        Url url = Url.create("http://www.myurl.com/path1?a=1&b=2#hash1");
        Assertions.assertEquals("www.myurl.com", url.hostname());
        url.search("c=3&d=4");
        Assertions.assertEquals("?c=3&d=4", url.search());
    }

    @Test
    public void setUsername() {
        Url url = Url.create();
        Assertions.assertTrue(url.username().isEmpty());
        // we can not set the username on a url without a host
        url.username("username");
        Assertions.assertTrue(url.username().isEmpty());
        // we can not set the username if the host is file
        url = Url.create("file://");
        url.username("username");
        Assertions.assertTrue(url.username().isEmpty());
        // set basic username
        url = Url.create("http://myhost.com");
        url.username("username");
        Assertions.assertEquals("username", url.username());
        // set username - percent encoded result
        url = Url.create("http://myhost.com");
        url.username("user name");
        Assertions.assertEquals("user%20name", url.username());
    }
}