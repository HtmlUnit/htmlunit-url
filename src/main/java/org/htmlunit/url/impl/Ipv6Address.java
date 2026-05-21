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
 * Represents an IPv6 address host as defined by the WHATWG URL Living Standard.
 *
 * @author Stephane Bastian
 * @author Ronald Brill
 */
final class Ipv6Address implements Host {
    private final short[] ip_;

    private Ipv6Address(final short[] ip) {
        ip_ = Objects.requireNonNull(ip);
    }

    /**
     * Creates a new {@link Ipv6Address} for the given array of 8 IPv6 pieces.
     *
     * @param ip an array of exactly 8 {@code short} values representing the IPv6 pieces;
     *           must not be null
     * @return a new {@link Ipv6Address} instance
     */
    static Ipv6Address create(final short[] ip) {
        return new Ipv6Address(ip);
    }

    /**
     * Returns the IPv6 address as an array of 8 {@code short} pieces.
     *
     * @return the array of 8 IPv6 pieces
     */
    short[] ip() {
        return ip_;
    }

    /**
     * Returns the compressed colon-separated hexadecimal string representation of
     * this IPv6 address, e.g. {@code "::1"} or {@code "2001:db8::1"}.
     *
     * @return the string representation of the IPv6 address
     */
    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        SerializerHelper.serializeHost(this, result);
        return result.toString();
    }
}
