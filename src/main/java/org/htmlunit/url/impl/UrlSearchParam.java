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
 * Represents a single URL search parameter name-value pair.
 *
 * @author Stephane Bastian
 * @author Ronald Brill
 */
class UrlSearchParam {
    private final String name_;
    private String value_;

    UrlSearchParam(final String name, final String value) {
        name_ = Objects.requireNonNull(name);
        value_ = value;
    }

    public String name() {
        return name_;
    }

    public String value() {
        return value_;
    }

    public void value(final String value) {
        value_ = value;
    }
}
