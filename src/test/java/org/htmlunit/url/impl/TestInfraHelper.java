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

import java.util.Arrays;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestInfraHelper {

    @Test
    void strictSplit() {
        Assertions.assertEquals(Arrays.asList("a", "B", "c"), InfraHelper.strictSplit("a.B.c", '.'));
        Assertions.assertEquals(Arrays.asList("", "a", "B", "", "c", ""), InfraHelper.strictSplit(".a.B..c.", '.'));
    }

    @Test
    void toHexChars() {
        Assertions.assertArrayEquals(new char[] { '0', '0' }, InfraHelper.toHexChars((byte) 0));
        Assertions.assertArrayEquals(new char[] { '0', 'F' }, InfraHelper.toHexChars((byte) 15));
        Assertions.assertArrayEquals(new char[] { '1', '0' }, InfraHelper.toHexChars((byte) 16));
        Assertions.assertArrayEquals(new char[] { 'F', 'F' }, InfraHelper.toHexChars((byte) 255));
    }
}