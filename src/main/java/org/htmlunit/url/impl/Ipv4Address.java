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
 * Represents an IPv4 address host as defined by the WHATWG URL Living Standard.
 *
 * @author Stephane Bastian
 * @author Ronald Brill
 */
final class Ipv4Address implements Host {
    private final int ip_;

    private Ipv4Address(final int ip) {
        ip_ = ip;
    }

    /**
     * Creates a new {@link Ipv4Address} for the given 32-bit integer address.
     *
     * @param ip the IPv4 address as a 32-bit integer (big-endian)
     * @return a new {@link Ipv4Address} instance
     */
    static Ipv4Address create(final int ip) {
        return new Ipv4Address(ip);
    }

    /**
     * Returns the IPv4 address as a 32-bit integer (big-endian).
     *
     * @return the IPv4 address as a 32-bit integer
     */
    int ip() {
        return ip_;
    }

    /**
     * Returns the dotted-decimal string representation of this IPv4 address
     * (e.g. {@code "192.168.0.1"}).
     *
     * @return the dotted-decimal string representation
     */
    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        SerializerHelper.serializeHost(this, result);
        return result.toString();
    }
}
