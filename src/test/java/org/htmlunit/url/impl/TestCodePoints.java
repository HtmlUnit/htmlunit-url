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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.htmlunit.url.Url;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link Codepoints}.
 *
 * @author <a href="mail://stephane.bastian.dev@gmail.com">Stephane Bastian</a>
 * @author rbri
 */
public class TestCodePoints {

    @Test
    public void codepointAt() {
        Codepoints input = new Codepoints("A B C D");
        assertEquals(7, input.length());
        assertEquals(7, input.remaining());
        assertEquals(CodepointHelper.CP_BOF, input.codepointAt(-1));
        assertEquals(65,  input.codepointAt(0));
        assertEquals(32,  input.codepointAt(1));
        assertEquals(66,  input.codepointAt(2));
        assertEquals(32,  input.codepointAt(3));
        assertEquals(67,  input.codepointAt(4));
        assertEquals(32,  input.codepointAt(5));
        assertEquals(68,  input.codepointAt(6));
        assertEquals(CodepointHelper.CP_EOF, input.codepointAt(7));
        assertEquals(CodepointHelper.CP_EOF, input.codepointAt(700));
        assertEquals(7, input.remaining());
    }

    @Test
    public void emptyInput() {
        Codepoints input = new Codepoints("");
        assertEquals(0,  input.length());
        assertEquals(0,  input.remaining());
        assertEquals(-1, input.codepoint());
        input.increasePointerByOne();
        assertEquals(-1, input.codepoint());
        assertEquals(0,  input.length());
        assertEquals(0,  input.remaining());
    }

    @Test
    public void remainingMatch() {
        Codepoints input = new Codepoints("A B C D");
        assertEquals(7,  input.length());
        assertEquals(7,  input.remaining());
        assertEquals(65, input.codepoint());
        assertTrue(input.remainingMatch(2, (idx, cp) -> idx == 0 ? cp == 32 : cp == 66));
        input.increasePointerByOne();
        assertEquals(6,  input.remaining());
        assertEquals(32, input.codepoint());
        assertTrue(input.remainingMatch(2, (idx, cp) -> idx == 0 ? cp == 66 : cp == 32));
        input.increasePointerByOne();
        assertEquals(5,  input.remaining());
        assertEquals(66, input.codepoint());
        assertTrue(input.remainingMatch(2, (idx, cp) -> idx == 0 ? cp == 32 : cp == 67));
        input.increasePointerByOne();
        assertEquals(4,  input.remaining());
        assertEquals(32, input.codepoint());
        assertTrue(input.remainingMatch(2, (idx, cp) -> idx == 0 ? cp == 67 : cp == 32));
        input.increasePointerByOne();
        assertEquals(3,  input.remaining());
        assertEquals(67, input.codepoint());
        assertTrue(input.remainingMatch(2, (idx, cp) -> idx == 0 ? cp == 32 : cp == 68));
        input.increasePointerByOne();
        assertEquals(2,  input.remaining());
        assertEquals(32, input.codepoint());
        assertTrue( input.remainingMatch(1, (idx, cp) -> cp == 68));
        assertFalse(input.remainingMatch(2, (idx, cp) -> idx == 0 ? cp == 68 : cp == 32));
        input.increasePointerByOne();
        assertEquals(1,  input.remaining());
        assertEquals(68, input.codepoint());
        assertFalse(input.remainingMatch(2, (idx, cp) -> true));
        input.increasePointerByOne();
        assertEquals(0,  input.remaining());
        assertEquals(CodepointHelper.CP_EOF, input.codepoint());
    }

    // -------------------------------------------------------------------------
    // Tests for issue #9 — isUrlCodepoint off-by-one on U+00A0 and U+10FFFD
    // -------------------------------------------------------------------------

    /**
     * U+009F is the last C0/C1 control — must NOT be a URL code point.
     * Verifies the code point just below the range boundary is correctly excluded.
     */
    @Test
    public void isUrlCodepoint_belowLowerBound_U009F() {
        assertFalse(CodepointHelper.isUrlCodepoint(0x009F),
                "U+009F (APPLICATION PROGRAM COMMAND) must not be a URL code point");
    }

    /**
     * U+00A0 NO-BREAK SPACE is the first code point in the "U+00A0 to U+10FFFD"
     * range defined by the WHATWG spec and MUST be a URL code point.
     *
     * BUG: the current implementation uses {@code codepoint > 0x00A0} (exclusive),
     * so this assertion FAILS against the buggy code and PASSES after the fix.
     */
    @Test
    public void isUrlCodepoint_lowerBound_U00A0_noBreakSpace() {
        assertTrue(CodepointHelper.isUrlCodepoint(0x00A0),
                "U+00A0 NO-BREAK SPACE must be a URL code point (spec: U+00A0 to U+10FFFD inclusive)");
    }

    /**
     * U+00A1 INVERTED EXCLAMATION MARK — clearly inside the range; must be a URL code point.
     * Acts as a regression guard: if this ever fails the range itself is broken.
     */
    @Test
    public void isUrlCodepoint_insideRange_U00A1() {
        assertTrue(CodepointHelper.isUrlCodepoint(0x00A1),
                "U+00A1 must be a URL code point");
    }

    /**
     * A handful of representative non-BMP code points that are well inside the
     * upper portion of the range and are neither surrogates nor noncharacters.
     */
    @Test
    public void isUrlCodepoint_insideRange_nonBmpSamples() {
        assertTrue(CodepointHelper.isUrlCodepoint(0x1F600),
                "U+1F600 GRINNING FACE must be a URL code point");
        assertTrue(CodepointHelper.isUrlCodepoint(0x10FFFC),
                "U+10FFFC must be a URL code point");
    }

    /**
     * U+10FFFD is the last valid (non-noncharacter) code point in the range.
     * The spec says "inclusive" so it MUST be a URL code point.
     *
     * BUG: the current implementation uses {@code codepoint < 0x10FFFD} (exclusive),
     * so this assertion FAILS against the buggy code and PASSES after the fix.
     */
    @Test
    public void isUrlCodepoint_upperBound_U10FFFD() {
        assertTrue(CodepointHelper.isUrlCodepoint(0x10FFFD),
                "U+10FFFD must be a URL code point (spec: U+00A0 to U+10FFFD inclusive)");
    }

    /**
     * U+10FFFE and U+10FFFF are noncharacters — they sit just above the range
     * and must NOT be URL code points, regardless of the boundary fix.
     */
    @Test
    public void isUrlCodepoint_aboveUpperBound_noncharacters() {
        assertFalse(CodepointHelper.isUrlCodepoint(0x10FFFE),
                "U+10FFFE is a noncharacter and must not be a URL code point");
        assertFalse(CodepointHelper.isUrlCodepoint(0x10FFFF),
                "U+10FFFF is a noncharacter and must not be a URL code point");
    }

    /**
     * Surrogates (U+D800–U+DFFF) are in the middle of the numeric range but are
     * explicitly excluded by the spec. They must NOT be URL code points.
     */
    @Test
    public void isUrlCodepoint_surrogates_excluded() {
        assertFalse(CodepointHelper.isUrlCodepoint(0xD800),
                "U+D800 (surrogate) must not be a URL code point");
        assertFalse(CodepointHelper.isUrlCodepoint(0xDFFF),
                "U+DFFF (surrogate) must not be a URL code point");
    }

    /**
     * Noncharacters inside the range (U+FDD0–U+FDEF) must NOT be URL code points.
     */
    @Test
    public void isUrlCodepoint_noncharactersInRange_excluded() {
        assertFalse(CodepointHelper.isUrlCodepoint(0xFDD0),
                "U+FDD0 (noncharacter) must not be a URL code point");
        assertFalse(CodepointHelper.isUrlCodepoint(0xFDEF),
                "U+FDEF (noncharacter) must not be a URL code point");
        assertFalse(CodepointHelper.isUrlCodepoint(0xFFFE),
                "U+FFFE (noncharacter) must not be a URL code point");
        assertFalse(CodepointHelper.isUrlCodepoint(0xFFFF),
                "U+FFFF (noncharacter) must not be a URL code point");
    }

    // -------------------------------------------------------------------------
    // Integration tests — observable impact of the U+00A0 bug via Url.create()
    // -------------------------------------------------------------------------

    /**
     * A fragment containing U+00A0 should parse without any validation errors.
     *
     * The WHATWG URL standard says: "If c is not a URL code point … validation
     * error." U+00A0 IS a URL code point, so no error must be reported.
     *
     * BUG: with the current off-by-one, U+00A0 is not recognised as a URL code
     * point and an {@code INVALID_URL_UNIT} validation error is wrongly added.
     */
    @Test
    public void urlCreate_noBreakSpaceInFragment_noValidationError() {
        // U+00A0 encoded directly in the fragment
        final Url url = Url.create("https://example.org/path#frag\u00A0ment");
        assertTrue(url.validationErrors().isEmpty(),
                "U+00A0 in a fragment must not produce a validation error; got: "
                        + url.validationErrors());
    }

    /**
     * A fragment containing U+00A0 should be percent-encoded and round-trip cleanly.
     * After parsing, the fragment is serialised with U+00A0 percent-encoded as %C2%A0
     * (its UTF-8 representation).
     */
    @Test
    public void urlCreate_noBreakSpaceInFragment_correctlySerialized() {
        final Url url = Url.create("https://example.org/#x\u00A0y");
        // U+00A0 → UTF-8 bytes 0xC2, 0xA0 → percent-encoded as %C2%A0
        assertEquals("#x%C2%A0y", url.hash(),
                "U+00A0 in a fragment must be percent-encoded as %C2%A0");
    }

    /**
     * Verifies that a URL with U+00A0 in the fragment can be parsed twice and
     * produces the same href both times (idempotent serialisation).
     */
    @Test
    public void urlCreate_noBreakSpaceInFragment_idempotent() {
        final String input = "https://example.org/#start\u00A0end";
        final Url first  = Url.create(input);
        final Url second = Url.create(first.href());
        assertEquals(first.href(), second.href(),
                "Re-parsing the serialised URL must yield the same href");
        assertTrue(second.validationErrors().isEmpty(),
                "Re-parsing must produce no validation errors");
    }

    /**
     * U+00A1 (one above the boundary) must similarly produce no validation error.
     * This acts as a non-regression check: if U+00A1 ever starts failing, the
     * range itself has regressed.
     */
    @Test
    public void urlCreate_u00A1InFragment_noValidationError() {
        final Url url = Url.create("https://example.org/#\u00A1");
        assertTrue(url.validationErrors().isEmpty(),
                "U+00A1 in a fragment must not produce a validation error");
    }

    /**
     * U+009F (just below U+00A0) is a C1 control and is NOT a URL code point.
     * Placing it in a fragment must report an {@code INVALID_URL_UNIT} validation
     * error — this verifies the boundary exclusion is correct on the low side.
     */
    @Test
    public void urlCreate_u009FInFragment_producesValidationError() {
        final Url url = Url.create("https://example.org/#\u009F");
        assertFalse(url.validationErrors().isEmpty(),
                "U+009F in a fragment must produce an INVALID_URL_UNIT validation error");
        assertEquals(org.htmlunit.url.ValidationError.INVALID_URL_UNIT,
                url.validationErrors().get(0),
                "Expected INVALID_URL_UNIT for U+009F in fragment");
    }
}
