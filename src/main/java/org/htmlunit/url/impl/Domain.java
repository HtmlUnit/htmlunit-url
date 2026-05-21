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

/**
 * Represents a domain host as defined by the WHATWG URL Living Standard.
 *
 * @author Stephane Bastian
 * @author Ronald Brill
 */
final class Domain implements Host {
    private final String host_;

    private Domain(final String host) {
        host_ = Objects.requireNonNull(host);
    }

    /**
     * Creates a new {@link Domain} for the given host string.
     *
     * @param host the domain host string; must not be null
     * @return a new {@link Domain} instance
     */
    static Domain create(final String host) {
        return new Domain(host);
    }

    /**
     * Returns the domain host string.
     *
     * @return the domain host string
     */
    String host() {
        return host_;
    }

    /**
     * Returns the domain host string.
     *
     * @return the domain host string
     */
    @Override
    public String toString() {
        return host_;
    }
}
