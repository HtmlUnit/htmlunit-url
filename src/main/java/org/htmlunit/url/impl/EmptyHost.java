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

/**
 * Represents an empty host as defined by the WHATWG URL Living Standard.
 *
 * @author Stephane Bastian
 * @author Ronald Brill
 */
final class EmptyHost implements Host {
    private static final EmptyHost INSTANCE = new EmptyHost();

    private EmptyHost() {
    }

    /**
     * Returns the singleton {@link EmptyHost} instance.
     *
     * @return the singleton {@link EmptyHost} instance
     */
    static EmptyHost create() {
        return INSTANCE;
    }

    /**
     * Returns an empty string, as an empty host has no host value.
     *
     * @return an empty string
     */
    @Override
    public String toString() {
        return "";
    }
}
