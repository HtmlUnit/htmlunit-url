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

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.IntPredicate;

/**
 * This class is a helper to make it easier to deal with codepoints.
 */
public class Codepoints {
    private int[] codepoints_;
    private int pointer_;

    public Codepoints(final String value) {
        Objects.requireNonNull(value);
        codepoints_ = value.codePoints().toArray();
        pointer_ = 0;
    }

    public Codepoints(final int[] codepoints) {
        codepoints_ = Objects.requireNonNull(codepoints);
        pointer_ = 0;
    }

    int codepoint() {
        return codepointAt(pointer());
    }

    int codepointAt(final int position) {
        if (position < 0) {
            return CodepointHelper.CP_BOF;
        }
        if (position >= codepoints_.length) {
            return CodepointHelper.CP_EOF;
        }
        return codepoints_[position];
    }

    boolean codepointIs(final int codePoint) {
        return codepoint() == codePoint;
    }

    boolean codepointIs(final IntPredicate predicate) {
        return predicate.test(codepoint());
    }

    boolean codepointIsNot(final IntPredicate predicate) {
        return !codepointIs(predicate);
    }

    boolean codepointIsNot(final int codePoint) {
        return !codepointIs(codePoint);
    }

    boolean codepointIsOneOf(final int... codePoints) {
        for (final int codePoint : codePoints) {
            if (codepointIs(codePoint)) {
                return true;
            }
        }
        return false;
    }

    void decreasePointerBy(final int value) {
        pointer(pointer() - value);
    }

    void decreasePointerByOne() {
        pointer(pointer() - 1);
    }

    void increasePointerByOne() {
        pointer(pointer() + 1);
    }

    boolean isEof() {
        return codepoint() == CodepointHelper.CP_EOF;
    }

    int length() {
        return codepoints_.length;
    }

    int pointer() {
        return pointer_;
    }

    void pointer(final int position) {
        this.pointer_ = position;
    }

    int remaining() {
        return remaining(pointer());
    }

    private int remaining(final int position) {
        final int result = length() - position;
        return result >= 0 ? result : 0;
    }

    boolean remainingMatch(final int numberOfCodepointsToMatch, final  BiPredicate<Integer, Integer> predicate) {
        if (numberOfCodepointsToMatch > 0 && remaining(pointer() + 1) >= numberOfCodepointsToMatch) {
            for (int i = 0; i < numberOfCodepointsToMatch; i++) {
                final int codepoint = codepointAt(pointer() + i + 1);
                final boolean result = predicate.test(i, codepoint);
                if (!result) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    void startOver() {
        pointer(-1);
    }
}
