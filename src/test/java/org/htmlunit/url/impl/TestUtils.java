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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.htmlunit.url.Url;

/**
 * Test utilities including a minimal JSON parser sufficient for the WHATWG URL test suite files.
 *
* @author Stephane Bastian
* @author Ronald Brill
 */
public class TestUtils {

    public static <T> T readJsonFile(final String name) {
        final InputStream is = TestUtils.class.getClassLoader().getResourceAsStream(name);
        try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            final JsonParser parser = new JsonParser(reader);
            return (T) parser.parseValue();
        }
        catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(final String[] args) throws Exception {
        final String in = "https://퀬-?\uD99B\uDCD2.\u200Cૅ\uDB67\uDE24۴/x";
        final Url out = Url.create(in);
        System.out.println(out);
        System.out.println("\"%3Fa=b&c=d\"");
    }

    // -----------------------------------------------------------------------
    // Minimal recursive-descent JSON parser (no external dependencies)
    // -----------------------------------------------------------------------

    private static final class JsonParser {

        private final Reader reader_;
        /** Current character; -1 = EOF. */
        private int current_;

        JsonParser(final Reader reader) throws IOException {
            reader_ = reader;
            advance();
        }

        // --- public entry point ---

        Object parseValue() throws IOException {
            skipWhitespace();
            if (current_ == '{') {
                return parseObject();
            }
            if (current_ == '[') {
                return parseArray();
            }
            if (current_ == '"') {
                return parseString();
            }
            if (current_ == 't') {
                expect("true");
                return Boolean.TRUE;
            }
            if (current_ == 'f') {
                expect("false");
                return Boolean.FALSE;
            }
            if (current_ == 'n') {
                expect("null");
                return null;
            }
            if (current_ == '-' || (current_ >= '0' && current_ <= '9')) {
                return parseNumber();
            }
            // lenient: skip a leading comma or other stray character and retry
            if (current_ == ',') {
                advance();
                return parseValue();
            }
            throw new RuntimeException("Unexpected character: " + (char) current_);
        }

        // --- object ---

        private Map<String, Object> parseObject() throws IOException {
            consume('{');
            final Map<String, Object> map = new HashMap<>();
            skipWhitespace();
            while (current_ != '}' && current_ != -1) {
                skipWhitespace();
                final String key = parseString();
                skipWhitespace();
                consume(':');
                skipWhitespace();
                final Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                if (current_ == ',') {
                    advance();
                    skipWhitespace();
                }
            }
            consume('}');
            return map;
        }

        // --- array ---

        private Collection<Object> parseArray() throws IOException {
            consume('[');
            final Collection<Object> list = new ArrayList<>();
            skipWhitespace();
            while (current_ != ']' && current_ != -1) {
                list.add(parseValue());
                skipWhitespace();
                if (current_ == ',') {
                    advance();
                    skipWhitespace();
                }
            }
            consume(']');
            return list;
        }

        // --- string ---

        private String parseString() throws IOException {
            consume('"');
            final StringBuilder sb = new StringBuilder();
            while (current_ != '"' && current_ != -1) {
                if (current_ == '\\') {
                    advance();
                    switch (current_) {
                        case '"':  sb.append('"');  break;
                        case '\\': sb.append('\\'); break;
                        case '/':  sb.append('/');  break;
                        case 'b':  sb.append('\b'); break;
                        case 'f':  sb.append('\f'); break;
                        case 'n':  sb.append('\n'); break;
                        case 'r':  sb.append('\r'); break;
                        case 't':  sb.append('\t'); break;
                        case 'u':
                            sb.append(parseUnicodeEscape());
                            break;
                        default:
                            sb.append((char) current_);
                    }
                }
                else {
                    sb.append((char) current_);
                }
                advance();
            }
            consume('"');
            return sb.toString();
        }

        private char parseUnicodeEscape() throws IOException {
            int value = 0;
            for (int i = 0; i < 4; i++) {
                advance();
                final int digit = Character.digit(current_, 16);
                if (digit == -1) {
                    throw new RuntimeException("Invalid unicode escape: " + (char) current_);
                }
                value = value * 16 + digit;
            }
            return (char) value;
        }

        // --- number ---

        private Double parseNumber() throws IOException {
            final StringBuilder sb = new StringBuilder();
            if (current_ == '-') {
                sb.append((char) current_);
                advance();
            }
            while (current_ >= '0' && current_ <= '9') {
                sb.append((char) current_);
                advance();
            }
            if (current_ == '.') {
                sb.append('.');
                advance();
                while (current_ >= '0' && current_ <= '9') {
                    sb.append((char) current_);
                    advance();
                }
            }
            if (current_ == 'e' || current_ == 'E') {
                sb.append((char) current_);
                advance();
                if (current_ == '+' || current_ == '-') {
                    sb.append((char) current_);
                    advance();
                }
                while (current_ >= '0' && current_ <= '9') {
                    sb.append((char) current_);
                    advance();
                }
            }
            return Double.parseDouble(sb.toString());
        }

        // --- helpers ---

        private void skipWhitespace() throws IOException {
            while (current_ == ' ' || current_ == '\t' || current_ == '\r' || current_ == '\n') {
                advance();
            }
        }

        private void advance() throws IOException {
            current_ = reader_.read();
        }

        private void consume(final int expected) throws IOException {
            if (current_ != expected) {
                throw new RuntimeException(
                        "Expected '" + (char) expected + "' but got '" + (char) current_ + "'");
            }
            advance();
        }

        private void expect(final String literal) throws IOException {
            for (final char c : literal.toCharArray()) {
                if (current_ != c) {
                    throw new RuntimeException(
                            "Expected literal '" + literal + "' but got '" + (char) current_ + "'");
                }
                advance();
            }
        }
    }
}