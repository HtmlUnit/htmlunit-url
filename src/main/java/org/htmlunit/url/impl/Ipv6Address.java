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
    private short[] ip_;

    private Ipv6Address(final short[] ip) {
        ip_ = Objects.requireNonNull(ip);
    }

    public static Ipv6Address create(final short[] ip) {
        return new Ipv6Address(ip);
    }

    public short[] ip() {
        return ip_;
    }

    public String toString() {
        final StringBuilder result = new StringBuilder();
        SerializerHelper.serializeHost(this, result);
        return result.toString();
    }
}
