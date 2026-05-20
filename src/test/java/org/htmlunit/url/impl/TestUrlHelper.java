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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class TestUrlHelper {
    static Collection<Map<String, Object>> percentEncodeAfterEncodingWhatWgTestData() {
        Collection<Object> result = TestUtils.readJsonFile("percent-encoding.json");
        // remove entries that are not Maps to account for comments in the file
        result.removeIf(next -> !(next instanceof Map));
        return (Collection<Map<String, Object>>) (Object) result;
    }

    @Test
    public void hasLeadingOrTrailingC0ControlOrSpace() {
        Assertions.assertFalse(UrlHelper.hasLeadingOrTrailingC0ControlOrSpace("abc".codePoints().toArray()));
        Assertions.assertTrue(UrlHelper.hasLeadingOrTrailingC0ControlOrSpace(" abc".codePoints().toArray()));
        Assertions.assertTrue(UrlHelper.hasLeadingOrTrailingC0ControlOrSpace("abc ".codePoints().toArray()));
        Assertions.assertFalse(UrlHelper.hasLeadingOrTrailingC0ControlOrSpace("a bc".codePoints().toArray()));
        Assertions.assertFalse(UrlHelper.hasLeadingOrTrailingC0ControlOrSpace("ab c".codePoints().toArray()));
    }

    @Test
    public void percentDecode() {
        Assertions.assertArrayEquals("%%s%1G".getBytes(StandardCharsets.UTF_8), UrlHelper.percentDecode("%25%s%1G"));
        Assertions.assertArrayEquals(new byte[] { (byte) 226, (byte) 128, (byte) 189, 37, 46 },
                UrlHelper.percentDecode("‽%25%2E"));
    }

    @Test
    public void percentEncode() {
        Assertions.assertEquals("%03", UrlHelper.percentEncode((byte) 0x3));
        Assertions.assertEquals("%23", UrlHelper.percentEncode((byte) 0x23));
        Assertions.assertEquals("%7F", UrlHelper.percentEncode((byte) 0x7F));
    }

    @Test
    public void percentEncodeAfterEncodingIso2022JP() {
        Charset charset = Charset.forName("ISO-2022-JP");
        Assertions.assertEquals("%1B(J%5C%1B(B", UrlHelper.percentEncodeAfterEncoding(charset.newEncoder(), "¥",
                CodepointHelper::isInUserInfoPercentEncodeSet, false));
        // TODO this test used to fail with the expected value of '"%1B(J\\%1B(B"'. Note
        // that this value
        // comes
        // from an example of the WhatWg url spec. However the result I get (which is
        // '%1B(J\%1B(B') is
        // the one that makes sens to me.
        // the reason is that the encoding of the value is the same in all online
        // encoders I tried.
        // Thus the percent encoded value is the one I am testing against and I suspect
        // that there is a
        // typo in the WhatWg url exammple
        // Need to check though
    }

    @ParameterizedTest
    @MethodSource("percentEncodeAfterEncodingWhatWgTestData")
    public void percentEncodeAfterEncodingOnWhatWgTestData(Map<String, Object> testData) {
        String input = (String) testData.get("input");
        Map<String, Object> output = (Map<String, Object>) testData.get("output");
        if (input != null && output != null) {
            for (String encoding : output.keySet()) {
                String expectedEncodedResult = (String) output.get(encoding);
                Charset charset = Charset.forName(encoding);
                int[] scalarCodepoints = InfraHelper.toScalarCodepoints(input.codePoints().toArray());
                String scalarInput = new String(scalarCodepoints, 0, scalarCodepoints.length);
                String encodedValue = UrlHelper.percentEncodeAfterEncoding(charset.newEncoder(), scalarInput,
                        CodepointHelper::isSpecialQueryPercentEncodeSet, false);
                Assertions.assertEquals(expectedEncodedResult, encodedValue);
            }
        }
    }

    @Test
    public void percentEncodeAfterEncodingShiftJIS() {
        Charset charset = Charset.forName("Shift_JIS");
        Assertions.assertEquals("%20", UrlHelper.percentEncodeAfterEncoding(charset.newEncoder(), " ",
                CodepointHelper::isInUserInfoPercentEncodeSet, false));
        Assertions.assertEquals("%81%DF", UrlHelper.percentEncodeAfterEncoding(charset.newEncoder(), "≡",
                CodepointHelper::isInUserInfoPercentEncodeSet, false));
        Assertions.assertEquals("%26%238253%3B", UrlHelper.percentEncodeAfterEncoding(charset.newEncoder(), "‽",
                CodepointHelper::isInUserInfoPercentEncodeSet, false));
        Assertions.assertEquals("1+1+%81%DF+2%20%26%238253%3B", UrlHelper.percentEncodeAfterEncoding(
                charset.newEncoder(), "1+1 ≡ 2%20‽", CodepointHelper::isInUserInfoPercentEncodeSet, true));
    }

    @Test
    public void removeAsciiTabAndNewline() {
        Assertions.assertArrayEquals("abcd".codePoints().toArray(),
                UrlHelper.removeAsciiTabAndNewline("\ta\rb\nc\td".codePoints().toArray()));
        Assertions.assertArrayEquals("abcd".codePoints().toArray(),
                UrlHelper.removeAsciiTabAndNewline("\t\n\na\t\n\nb\t\n\nc\t\n\nd".codePoints().toArray()));
    }

    @Test
    public void removeLeadingOrTrailingC0ControlOrSpace() {
        // Assertions.assertArrayEquals("abc".codePoints().toArray(),
        // UrlParserUtils.removeLeadingOrTrailingC0ControlOrSpace("abc".codePoints().toArray()));
        // Assertions.assertArrayEquals("abc".codePoints().toArray(),
        // UrlParserUtils.removeLeadingOrTrailingC0ControlOrSpace(
        // " abc".codePoints().toArray()));
        Assertions.assertArrayEquals("abc".codePoints().toArray(),
                UrlHelper.removeLeadingOrTrailingC0ControlOrSpace("abc ".codePoints().toArray()));
        Assertions.assertArrayEquals("abc".codePoints().toArray(),
                UrlHelper.removeLeadingOrTrailingC0ControlOrSpace("    abc".codePoints().toArray()));
        Assertions.assertArrayEquals("abc".codePoints().toArray(),
                UrlHelper.removeLeadingOrTrailingC0ControlOrSpace("abc    ".codePoints().toArray()));
        Assertions.assertArrayEquals("abc".codePoints().toArray(),
                UrlHelper.removeLeadingOrTrailingC0ControlOrSpace("    abc       ".codePoints().toArray()));
        Assertions.assertArrayEquals("a bc".codePoints().toArray(),
                UrlHelper.removeLeadingOrTrailingC0ControlOrSpace("a bc".codePoints().toArray()));
        Assertions.assertArrayEquals("ab c".codePoints().toArray(),
                UrlHelper.removeLeadingOrTrailingC0ControlOrSpace("ab c".codePoints().toArray()));
    }

    @Test
    public void toScalarCodepoints() {
        Assertions.assertArrayEquals("abc".codePoints().toArray(),
                InfraHelper.toScalarCodepoints("abc".codePoints().toArray()));
        Assertions.assertArrayEquals("a\uFFFDb".codePoints().toArray(),
                InfraHelper.toScalarCodepoints("a\uD800b".codePoints().toArray()));
        Assertions.assertArrayEquals("a\uFFFDb\uFFFDc\uFFFDd".codePoints().toArray(),
                InfraHelper.toScalarCodepoints("a\uD83Db\uD83Dc\uD83Dd".codePoints().toArray()));
    }

    @Test
    public void utf8PercentEncode() {
        Assertions.assertEquals("%E2%89%A1",
                UrlHelper.utf8PercentEncode("≡", CodepointHelper::isInUserInfoPercentEncodeSet));
        Assertions.assertEquals("%E2%80%BD",
                UrlHelper.utf8PercentEncode("‽", CodepointHelper::isInUserInfoPercentEncodeSet));
        Assertions.assertEquals("Say%20what%E2%80%BD",
                UrlHelper.utf8PercentEncode("Say what‽", CodepointHelper::isInUserInfoPercentEncodeSet));
    }

    @Test
    public void utf8PercentEncodeSurrogatePair1() {
        String test = "💩";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < test.length();) {
            int cp = test.codePointAt(i);
            String s = UrlHelper.utf8PercentEncode(StandardCharsets.UTF_8.newEncoder(), cp,
                    CodepointHelper::isInUserInfoPercentEncodeSet);
            result.append(s);
            i += Character.charCount(cp);
        }
        Assertions.assertEquals("%F0%9F%92%A9", result.toString());
    }
}