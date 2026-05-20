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
 * @author <a href="mail://stephane.bastian.dev@gmail.com">Stephane Bastian</a>
 */
final class Ipv4Address implements Host {
    private final int ip_;

    private Ipv4Address(final int ip) {
        ip_ = Objects.requireNonNull(ip);
    }

    public static Ipv4Address create(final int ip) {
        return new Ipv4Address(ip);
    }

    public int ip() {
        return ip_;
    }

    public String toString() {
        final StringBuilder result = new StringBuilder();
        SerializerHelper.serializeHost(this, result);
        return result.toString();
    }
}
