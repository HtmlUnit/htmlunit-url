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
import org.htmlunit.url.UrlSearchParams;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests that {@code +} in application/x-www-form-urlencoded query strings is
 * decoded as a space (U+0020), as required by step 3.4 of the
 * WHATWG URL Living Standard application/x-www-form-urlencoded parser.
 *
 * <p>The bug: {@code parseFormUrlEncoded} was replacing {@code '='} with {@code ' '}
 * instead of replacing {@code '+'} with {@code ' '}.
 *
 * @author Ronald Brill
 */
public class TestSearchParamsPlusDecoding {

    /**
     * A {@code +} in a parameter name must be decoded as a space.
     * Input query: {@code "hello+world=1"}
     * Expected name: {@code "hello world"}, expected value: {@code "1"}.
     */
    @Test
    public void plusInNameDecodedAsSpace() {
        final Url url = Url.create("http://example.com/?hello+world=1");
        final UrlSearchParams params = url.searchParams();

        Assertions.assertEquals(1, params.size());
        Assertions.assertNotNull(params.get("hello world"));
        Assertions.assertEquals("1", params.get("hello world"));
        Assertions.assertNull(params.get("hello+world"));
    }

    /**
     * A {@code +} in a parameter value must be decoded as a space.
     * Input query: {@code "key=hello+world"}
     * Expected name: {@code "key"}, expected value: {@code "hello world"}.
     */
    @Test
    public void plusInValueDecodedAsSpace() {
        final Url url = Url.create("http://example.com/?key=hello+world");
        final UrlSearchParams params = url.searchParams();

        Assertions.assertEquals(1, params.size());
        Assertions.assertEquals("hello world", params.get("key"));
    }

    /**
     * Multiple {@code +} signs in both name and value must all be decoded as spaces.
     * Input query: {@code "first+last=john+doe"}
     * Expected name: {@code "first last"}, expected value: {@code "john doe"}.
     */
    @Test
    public void multiplePlusSignsDecodedAsSpaces() {
        final Url url = Url.create("http://example.com/?first+last=john+doe");
        final UrlSearchParams params = url.searchParams();

        Assertions.assertEquals(1, params.size());
        Assertions.assertEquals("john doe", params.get("first last"));
    }

    /**
     * Multiple parameters each containing {@code +} must all be decoded correctly.
     * Input query: {@code "a+b=c+d&e+f=g+h"}
     */
    @Test
    public void multiplePlusSignsInMultipleParams() {
        final Url url = Url.create("http://example.com/?a+b=c+d&e+f=g+h");
        final UrlSearchParams params = url.searchParams();

        Assertions.assertEquals(2, params.size());
        Assertions.assertEquals("c d", params.get("a b"));
        Assertions.assertEquals("g h", params.get("e f"));
    }

    /**
     * A literal {@code %20} (percent-encoded space) must also decode as a space,
     * and must not be confused with {@code +} decoding.
     * Input query: {@code "hello%20world=1"}
     */
    @Test
    public void percentEncodedSpaceAlsoDecodedAsSpace() {
        final Url url = Url.create("http://example.com/?hello%20world=1");
        final UrlSearchParams params = url.searchParams();

        Assertions.assertEquals(1, params.size());
        Assertions.assertEquals("1", params.get("hello world"));
    }

    /**
     * A {@code +} in a query string set via {@link Url#search(String)} must be
     * decoded as a space when read back through {@code searchParams()}.
     * This exercises the same {@code parseFormUrlEncoded} path via the search setter.
     */
    @Test
    public void plusInSearchSetterDecodedAsSpace() {
        final Url url = Url.create("http://example.com/");
        url.search("greeting=hello+world");
        final UrlSearchParams params = url.searchParams();

        Assertions.assertEquals(1, params.size());
        Assertions.assertEquals("hello world", params.get("greeting"));
    }

    /**
     * A {@code +} with no surrounding characters must decode to a single space
     * in both name and value.
     * Input query: {@code "+=+"}
     * Expected name: {@code " "}, expected value: {@code " "}.
     */
    @Test
    public void onlyPlusSignDecodesToSpace() {
        final Url url = Url.create("http://example.com/?+=+");
        final UrlSearchParams params = url.searchParams();

        Assertions.assertEquals(1, params.size());
        Assertions.assertEquals(" ", params.get(" "));
    }

    /**
     * A query string without any {@code +} signs must not be affected.
     * Input query: {@code "name=value"}
     * This is the baseline / non-regression case.
     */
    @Test
    public void noPlusSignUnchanged() {
        final Url url = Url.create("http://example.com/?name=value");
        final UrlSearchParams params = url.searchParams();

        Assertions.assertEquals(1, params.size());
        Assertions.assertEquals("value", params.get("name"));
    }
}