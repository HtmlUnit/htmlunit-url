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

import org.junit.jupiter.api.Test;

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
}