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

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.IntPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.htmlunit.url.ValidationError;
import org.htmlunit.url.ValidationException;

/**
 * This is an implementation of the
 * <a href="https://url.spec.whatwg.org/#concept-basic-url-parser">WhatWg basic URL parser</a>.
 *
 * @author Stephane Bastian
 * @author Ronald Brill
 */
class UrlParser {
    private static final Logger logger = Logger.getLogger(UrlParser.class.getName());

    private UrlImpl url_;
    private boolean atSignSeenFlag_;
    private boolean insideBracketsFlag_;
    private StringBuilder buffer_;
    private Codepoints input_;
    private boolean passwordTokenSeenFlag_;
    private State state_;
    private UrlImpl base_;
    private Charset encoding_;
    private CharsetEncoder encoder_;
    private State stateOverride_;
    private CharsetEncoder utf8Encoder_;

    protected void appendToBuffer(final int codePoint) {
        buffer_.appendCodePoint(codePoint);
    }

    /**
     * Implementation of the
     * <a href="https://url.spec.whatwg.org/#authority-state">authority state</a>.
     * <ul>
     * <li>1) If c is U+0040 (@), then:
     *   <ul>
     *   <li>1.1) Invalid-credentials validation error.</li>
     *   <li>1.2) If atSignSeen is true, then prepend "%40" to buffer.</li>
     *   <li>1.3) Set atSignSeen to true.</li>
     *   <li>1.4) For each codePoint in buffer:
     *     <ul>
     *     <li>1.4.1) If codePoint is U+003A (:) and passwordTokenSeen is false, then
     *         set passwordTokenSeen to true and continue.</li>
     *     <li>1.4.2) Let encodedCodePoints be the result of running UTF-8
     *         percent-encode codePoint using the userinfo percent-encode set.</li>
     *     <li>1.4.3) If passwordTokenSeen is true, then append encodedCodePoints to
     *         url's password.</li>
     *     <li>1.4.4) Otherwise, append encodedCodePoints to url's username.</li>
     *     </ul>
     *   </li>
     *   <li>1.5) Set buffer to the empty string.</li>
     *   </ul>
     * </li>
     * <li>2) Otherwise, if one of the following is true:
     *   c is the EOF code point, U+002F (/), U+003F (?), or U+0023 (#);
     *   url is special and c is U+005C (\);
     *   then:
     *   <ul>
     *   <li>2.1) If atSignSeen is true and buffer is the empty string,
     *       host-missing validation error, return failure.</li>
     *   <li>2.2) Decrease pointer by buffer's code point length + 1, set buffer to
     *       the empty string, and set state to host state.</li>
     *   </ul>
     * </li>
     * <li>3) Otherwise, append c to buffer.</li>
     * </ul>
     */
    private StateReturnType authorityState() {
        // 1
        if (input_.codepointIs(CodepointHelper.CP_AT)) {
            // 1.1
            validationError(ValidationError.INVALID_CREDENTIALS);
            // 1.2
            if (atSignSeenFlag_) {
                prependToBuffer("%40");
            }
            // 1.3
            atSignSeenFlag_ = true;
            // 1.4
            buffer_.codePoints().forEach(cp -> {
                // 1.4.1
                if (cp == CodepointHelper.CP_COLON && !passwordTokenSeenFlag_) {
                    passwordTokenSeenFlag_ = true;
                }
                else {
                    // 1.4.2
                    final String encodedCodePoint = UrlHelper.utf8PercentEncode(utf8Encoder(), cp,
                            CodepointHelper::isInUserInfoPercentEncodeSet);
                    // 1.4.3
                    if (passwordTokenSeenFlag_) {
                        url_.password_ += encodedCodePoint;
                    }
                    // 1.4.4
                    else {
                        url_.username_ += encodedCodePoint;
                    }
                }
            });
            // 1.5
            clearBuffer();
        }
        // 2
        else if (input_.codepointIsOneOf(CodepointHelper.CP_EOF, CodepointHelper.CP_SLASH,
                CodepointHelper.CP_QUESTION_MARK, CodepointHelper.CP_HASH)
                || (url_.isSpecial() && input_.codepointIs(CodepointHelper.CP_BACKSLASH))) {
            // 2.1
            if (atSignSeenFlag_ && buffer().length() == 0) {
                throw validationException(ValidationError.HOST_MISSING);
            }
            else {
                // 2.2
                input_.decreasePointerBy((int) buffer().codePoints().count() + 1);
                clearBuffer();
                state(State.HOST);
            }
        }
        else {
            // 3
            appendToBuffer(input_.codepoint());
        }
        return StateReturnType.CONTINUE;
    }

    /**
     * Implementation of the
     * <a href="https://url.spec.whatwg.org/#concept-basic-url-parser">basic URL parser</a>.
     * <p>
     * The basic URL parser takes a scalar value string input, with an optional null or base URL
     * base (default null), an optional encoding encoding (default UTF-8), an optional URL url, and
     * an optional state override state override, and then runs these steps.
     * </p>
     * <p>
     * The encoding argument is a legacy concept only relevant for HTML. The url and state override
     * arguments are only for use by various APIs. When the url and state override arguments are not
     * passed, the basic URL parser returns either a new URL or failure. If they are passed, the
     * algorithm modifies the passed url and can terminate without returning anything.
     * </p>
     * <ul>
     * <li>1) If url is not given:
     *   <ul>
     *   <li>1.1) Set url to a new URL.</li>
     *   <li>1.2) If input contains any leading or trailing C0 control or space,
     *       invalid-URL-unit validation error.</li>
     *   <li>1.3) Remove any leading and trailing C0 control or space from input.</li>
     *   </ul>
     * </li>
     * <li>2) If input contains any ASCII tab or newline, invalid-URL-unit validation error.</li>
     * <li>3) Remove all ASCII tab or newline from input.</li>
     * <li>4) Let state be state override if given, or scheme start state otherwise.</li>
     * <li>5) Set encoding to the result of getting an output encoding from encoding.</li>
     * <li>6) Let buffer be the empty string.</li>
     * <li>7) Let atSignSeen, insideBrackets, and passwordTokenSeen be false.</li>
     * <li>8) Let pointer be a pointer for input.</li>
     * <li>9) Keep running the following state machine by switching on state. If after a run pointer
     *     points to the EOF code point, go to the next step. Otherwise, increase pointer by 1 and
     *     continue with the state machine.</li>
     * </ul>
     *
     * @param input         the scalar value string to parse
     * @param base          the optional base URL (may be null)
     * @param encoding      the optional encoding override (defaults to UTF-8 when null)
     * @param url           the optional URL to modify in place (a new URL is created when null)
     * @param stateOverride the optional state override (scheme start state is used when null)
     * @return the parsed or modified URL
     */
    public UrlImpl basicParse(final String input, final UrlImpl base,
            final Charset encoding, final UrlImpl url, final State stateOverride) {
        Objects.requireNonNull(input);
        logger.log(Level.FINER, () -> "starting basicParse - input : " + input + " - base: " + base + " - encoding: "
                + encoding + " - stateOverride: " + stateOverride);
        // easier to deal with the codepoint array than using String
        int[] codepoints = input.codePoints().toArray();
        // note that the spec indicates that basicParse takes a scalar value String
        // (basically where surrogates have been replaced)
        codepoints = InfraHelper.toScalarCodepoints(codepoints);
        base_ = base;
        // 1
        if (url == null) {
            // 1.1
            url_ = new UrlImpl();
            // 1.2
            if (UrlHelper.hasLeadingOrTrailingC0ControlOrSpace(codepoints)) {
                validationError(ValidationError.INVALID_URL_UNIT);
                // 1.3
                codepoints = UrlHelper.removeLeadingOrTrailingC0ControlOrSpace(codepoints);
            }
        }
        else {
            url_ = url;
        }
        // 2
        if (UrlHelper.hasAsciiTabOrNewline(codepoints)) {
            validationError(ValidationError.INVALID_URL_UNIT);
            // 3
            codepoints = UrlHelper.removeAsciiTabAndNewline(codepoints);
        }
        // 4
        stateOverride_ = stateOverride;
        state(stateOverride != null ? stateOverride : State.SCHEME_START);
        // 5
        encoding_ = encoding == null ? StandardCharsets.UTF_8 : InfraHelper.getOutputEncoding(encoding);
        // 6
        buffer_ = new StringBuilder(codepoints.length);
        // 7
        atSignSeenFlag_ = false;
        insideBracketsFlag_ = false;
        passwordTokenSeenFlag_ = false;
        // 8
        input_ = new Codepoints(codepoints);
        // 9
        stateMachine();
        return url_;
    }

    /**
     * Convenience overload of {@link #basicParse(String, UrlImpl, Charset, UrlImpl, State)}.
     * <p>
     * The URL parser takes a string input, with an optional base URL base and an optional encoding
     * encoding override, and then runs these steps:
     * </p>
     * <ol>
     * <li>Let url be the result of running the basic URL parser on input with base, and encoding
     *     override as provided.</li>
     * <li>If url is failure, return failure.</li>
     * <li>If url's scheme is not "blob", return url.</li>
     * <li>Set url's blob URL entry to the result of resolving the blob URL url, if that did not
     *     return failure, and null otherwise.</li>
     * <li>Return url.</li>
     * </ol>
     *
     * @param input   the scalar value string to parse
     * @param baseUrl the optional base URL (may be null)
     * @param encoding the optional encoding override (defaults to UTF-8 when null)
     * @return the parsed URL
     */
    public UrlImpl basicParse(final String input, final UrlImpl baseUrl, final Charset encoding) {
        return basicParse(input, baseUrl, encoding, null, null);
    }

    protected StringBuilder buffer() {
        return buffer_;
    }

    protected void clearBuffer() {
        buffer_.setLength(0);
    }

    private CharsetEncoder encoder() {
        if (encoder_ == null) {
            encoder_ = encoding_.equals(StandardCharsets.UTF_8) ? utf8Encoder() : EncodingHelper.getEncoder(encoding_);
        }
        return encoder_;
    }

    /**
     * Implementation of the
     * <a href="https://url.spec.whatwg.org/#file-host-state">file host state</a>.
     * <ul>
     * <li>1) If c is the EOF code point, U+002F (/), U+005C (\), U+003F (?), or U+0023 (#),
     *     then decrease pointer by 1 and then:
     *   <ul>
     *   <li>1.1) If state override is not given and buffer is a Windows drive letter,
     *       file-invalid-Windows-drive-letter-host validation error, set state to path state.
     *       <br>This is a (platform-independent) Windows drive letter quirk. buffer is not reset
     *       here and instead used in the path state.</li>
     *   <li>1.2) Otherwise, if buffer is the empty string, then:
     *     <ul>
     *     <li>1.2.1) Set url's host to the empty string.</li>
     *     <li>1.2.2) If state override is given, then return.</li>
     *     <li>1.2.3) Set state to path start state.</li>
     *     </ul>
     *   </li>
     *   <li>1.3) Otherwise, run these steps:
     *     <ul>
     *     <li>1.3.1) Let host be the result of host parsing buffer with url is not special.</li>
     *     <li>1.3.2) If host is failure, then return failure.</li>
     *     <li>1.3.3) If host is "localhost", then set host to the empty string.</li>
     *     <li>1.3.4) Set url's host to host.</li>
     *     <li>1.3.5) If state override is given, then return.</li>
     *     <li>1.3.6) Set buffer to the empty string and state to path start state.</li>
     *     </ul>
     *   </li>
     *   </ul>
     * </li>
     * <li>2) Otherwise, append c to buffer.</li>
     * </ul>
     */
    private StateReturnType fileHostState() {
        // 1
        if (input_.codepointIsOneOf(CodepointHelper.CP_EOF, CodepointHelper.CP_SLASH, CodepointHelper.CP_BACKSLASH,
                CodepointHelper.CP_QUESTION_MARK, CodepointHelper.CP_HASH)) {
            input_.decreasePointerByOne();
            // 1.1
            if (stateOverride_ == null && UrlHelper.isWindowsDriveLetter(buffer().toString(), 0)) {
                validationError(ValidationError.FILE_INVALID_WINDOWS_DRIVE_LETTER_HOST);
                state(State.PATH);
            }
            // 1.2
            else if (buffer().length() == 0) {
                // 1.2.1
                url_.host_ = EmptyHost.create();
                // 1.2.2
                if (stateOverride_ != null) {
                    return StateReturnType.RETURN;
                }
                // 1.2.3
                state(State.PATH_START);
            }
            // 1.3
            else {
                try {
                    // 1.3.1
                    Host host = HostParser.parse(buffer().toString(), !url_.isSpecial(), this::validationError);
                    // 1.3.3
                    if (host instanceof Domain domain && "localhost".equals(domain.host())) {
                        host = EmptyHost.create();
                    }
                    // 1.3.4
                    url_.host_ = host;
                }
                catch (final ValidationException e) {
                    // 1.3.2
                    throw validationException(e.validationError());
                }
                // 1.3.5
                if (stateOverride_ != null) {
                    return StateReturnType.RETURN;
                }
                // 1.3.6
                clearBuffer();
                state(State.PATH_START);
            }
        }
        // 2
        else {
            appendToBuffer(input_.codepoint());
        }
        return StateReturnType.CONTINUE;
    }

    /**
     * Implementation of the
     * <a href="https://url.spec.whatwg.org/#file-slash-state">file slash state</a>.
     * <ul>
     * <li>1) If c is U+002F (/) or U+005C (\), then:
     *   <ul>
     *   <li>1.1) If c is U+005C (\), invalid-reverse-solidus validation error.</li>
     *   <li>1.2) Set state to file host state.</li>
     *   </ul>
     * </li>
     * <li>2) Otherwise:
     *   <ul>
     *   <li>2.1) If base is non-null and base's scheme is "file", then:
     *     <ul>
     *     <li>2.1.1) Set url's host to base's host.</li>
     *     <li>2.1.2) If the code point substring from pointer to the end of input does not start
     *         with a Windows drive letter and base's path[0] is a normalized Windows drive letter,
     *         then append base's path[0] to url's path.
     *         <br>This is a (platform-independent) Windows drive letter quirk.</li>
     *     </ul>
     *   </li>
     *   <li>2.2) Set state to path state, and decrease pointer by 1.</li>
     *   </ul>
     * </li>
     * </ul>
     */
    private StateReturnType fileSlashState() {
        // 1
        if (input_.codepointIsOneOf(CodepointHelper.CP_SLASH, CodepointHelper.CP_BACKSLASH)) {
            // 1.1
            if (input_.codepointIs(CodepointHelper.CP_BACKSLASH)) {
                validationError(ValidationError.INVALID_REVERSE_SOLIDUS);
            }
            // 1.2
            state(State.FILE_HOST);
        }
        // 2
        else {
            // 2.1
            if (base_ != null && "file".equals(base_.scheme_)) {
                // 2.1.1
                url_.host_ = base_.host_;
                // 2.1.2
                if (!UrlHelper.startsWithWindowsDriveLetter(input_) && !base_.path_.isEmpty()
                        && UrlHelper.isNormalizedWindowsDriveLetter(base_.path_.get(0))) {
                    url_.path_.add(base_.path_.get(0));
                }
            }
            // 2.2
            state(State.PATH);
            input_.decreasePointerByOne();
        }
        return StateReturnType.CONTINUE;
    }

    /**
     * Implementation of the
     * <a href="https://url.spec.whatwg.org/#file-state">file state</a>.
     * <ul>
     * <li>1) Set url's scheme to "file".</li>
     * <li>2) Set url's host to the empty string.</li>
     * <li>3) If c is U+002F (/) or U+005C (\), then:
     *   <ul>
     *   <li>3.1) If c is U+005C (\), invalid-reverse-solidus validation error.</li>
     *   <li>3.2) Set state to file slash state.</li>
     *   </ul>
     * </li>
     * <li>4) Otherwise, if base is non-null and base's scheme is "file":
     *   <ul>
     *   <li>4.1) Set url's host to base's host, url's path to a clone of base's path,
     *       and url's query to base's query.</li>
     *   <li>4.2) If c is U+003F (?), then set url's query to the empty string and
     *       state to query state.</li>
     *   <li>4.3) Otherwise, if c is U+0023 (#), set url's fragment to the empty string
     *       and state to fragment state.</li>
     *   <li>4.4) Otherwise, if c is not the EOF code point:
     *     <ul>
     *     <li>4.4.1) Set url's query to null.</li>
     *     <li>4.4.2) If the code point substring from pointer to the end of input does not
     *         start with a Windows drive letter, then shorten url's path.</li>
     *     <li>4.4.3) Otherwise:
     *       <ul>
     *       <li>4.4.3.1) File-invalid-Windows-drive-letter validation error.</li>
     *       <li>4.4.3.2) Set url's path to {@code « »}.
     *           <br>This is a (platform-independent) Windows drive letter quirk.</li>
     *       </ul>
     *     </li>
     *     <li>4.4.4) Set state to path state and decrease pointer by 1.</li>
     *     </ul>
     *   </li>
     *   </ul>
     * </li>
     * <li>5) Otherwise, set state to path state, and decrease pointer by 1.</li>
     * </ul>
     */
    private StateReturnType fileState() {
        // 1
        url_.scheme_ = "file";
        // 2
        url_.host_ = EmptyHost.create();
        // 3
        if (input_.codepointIsOneOf(CodepointHelper.CP_SLASH, CodepointHelper.CP_BACKSLASH)) {
            // 3.1
            if (input_.codepointIs(CodepointHelper.CP_BACKSLASH)) {
                validationError(ValidationError.INVALID_REVERSE_SOLIDUS);
            }
            // 3.2
            state(State.FILE_SLASH);
        }
        // 4
        else if (base_ != null && "file".equals(base_.scheme_)) {
            // 4.1
            url_.host_ = base_.host_;
            url_.path(base_);
            url_.query_ = base_.query_;
            // 4.2
            if (input_.codepointIs(CodepointHelper.CP_QUESTION_MARK)) {
                url_.query_ = "";
                state(State.QUERY);
            }
            // 4.3
            else if (input_.codepointIs(CodepointHelper.CP_HASH)) {
                url_.fragment_ = "";
                state(State.FRAGMENT);
            }
            // 4.4
            else if (!input_.codepointIs(CodepointHelper.CP_EOF)) {
                // 4.4.1
                url_.query_ = null;
                // 4.4.2
                if (!UrlHelper.startsWithWindowsDriveLetter(input_)) {
                    url_.shortenPath();
                }
                // 4.4.3
                else {
                    // 4.4.3.1
                    validationError(ValidationError.FILE_INVALID_WINDOWS_DRIVE_LETTER);
                    // 4.4.3.2
                    url_.path_.clear();
                }
                // 4.4.4
                state(State.PATH);
                input_.decreasePointerByOne();
            }
        }
        // 5
        else {
            state(State.PATH);
            input_.decreasePointerByOne();
        }
        return StateReturnType.CONTINUE;
    }

    /**
     * Implementation of the
     * <a href="https://url.spec.whatwg.org/#fragment-state">fragment state</a>.
     * <ul>
     * <li>1) If c is not the EOF code point, then:
     *   <ul>
     *   <li>1.1) If c is not a URL code point and not U+0025 (%), invalid-URL-unit
     *       validation error.</li>
     *   <li>1.2) If c is U+0025 (%) and remaining does not start with two ASCII hex digits,
     *       invalid-URL-unit validation error.</li>
     *   <li>1.3) UTF-8 percent-encode c using the fragment percent-encode set and append
     *       the result to url's fragment.</li>
     *   </ul>
     * </li>
     * </ul>
     */
    private StateReturnType fragmentState() {
        // 1
        if (input_.codepointIsNot(CodepointHelper.CP_EOF)) {
            // 1.1
            if (input_.codepointIsNot(CodepointHelper::isUrlCodepoint)
                    && input_.codepointIsNot(CodepointHelper.CP_PERCENT)) {
                validationError(ValidationError.INVALID_URL_UNIT);
            }
            // 1.2
            if (input_.codepointIs(CodepointHelper.CP_PERCENT)
                    && !input_.remainingMatch(2, (idx, cp) -> InfraHelper.isAsciiHexDigit(cp))) {
                validationError(ValidationError.INVALID_URL_UNIT);
            }
            // 1.3
            final String utf8PercentEncode = UrlHelper.utf8PercentEncode(utf8Encoder(), input_.codepoint(),
                    CodepointHelper::isInFragmentPercentEncodeSet);
            url_.appendFragment(utf8PercentEncode);
        }
        return StateReturnType.CONTINUE;
    }

    /**
     * Implementation of the
     * <a href="https://url.spec.whatwg.org/#host-state">host state</a>.
     * <ul>
     * <li>1) If state override is given and url's scheme is "file", then decrease pointer by 1
     *     and set state to file host state.</li>
     * <li>2) Otherwise, if c is U+003A (:) and insideBrackets is false, then:
     *   <ul>
     *   <li>2.1) If buffer is the empty string, host-missing validation error, return failure.</li>
     *   <li>2.2) If state override is given and state override is hostname state, then return.</li>
     *   <li>2.3) Let host be the result of host parsing buffer with url is not special.</li>
     *   <li>2.4) If host is failure, then return failure.</li>
     *   <li>2.5) Set url's host to host, buffer to the empty string, and state to port state.</li>
     *   </ul>
     * </li>
     * <li>3) Otherwise, if one of the following is true:
     *   c is the EOF code point, U+002F (/), U+003F (?), or U+0023 (#);
     *   url is special and c is U+005C (\);
     *   then decrease pointer by 1, and then:
     *   <ul>
     *   <li>3.1) If url is special and buffer is the empty string, host-missing validation error,
     *       return failure.</li>
     *   <li>3.2) Otherwise, if state override is given, buffer is the empty string, and either
     *       url includes credentials or url's port is non-null, return.</li>
     *   <li>3.3) Let host be the result of host parsing buffer with url is not special.</li>
     *   <li>3.4) If host is failure, then return failure.</li>
     *   <li>3.5) Set url's host to host, buffer to the empty string, and state to path start
     *       state.</li>
     *   <li>3.6) If state override is given, then return.</li>
     *   </ul>
     * </li>
     * <li>4) Otherwise:
     *   <ul>
     *   <li>4.1) If c is U+005B ([), then set insideBrackets to true.</li>
     *   <li>4.2) If c is U+005D (]), then set insideBrackets to false.</li>
     *   <li>4.3) Append c to buffer.</li>
     *   </ul>
     * </li>
     * </ul>
     */
    private StateReturnType hostState() {
        // 1
        if (stateOverride_ != null && "file".equals(url_.scheme_)) {
            input_.decreasePointerByOne();
            state(State.FILE_HOST);
        }
        // 2
        else if (input_.codepointIs(CodepointHelper.CP_COLON) && !insideBracketsFlag_) {
            // 2.1
            if (buffer().length() == 0) {
                throw validationException(ValidationError.HOST_MISSING);
            }
            // 2.2
            if (State.HOSTNAME.equals(stateOverride_)) {
                return StateReturnType.RETURN;
            }
            try {
                // 2.3, 2.5
                url_.host_ = HostParser.parse(buffer().toString(), !url_.isSpecial(), this::validationError);
            }
            catch (final ValidationException e) {
                // 2.4
                throw validationException(e.validationError());
            }
            clearBuffer();
            state(State.PORT);
        }
        // 3
        else if (input_.codepointIsOneOf(CodepointHelper.CP_EOF, CodepointHelper.CP_SLASH,
                CodepointHelper.CP_QUESTION_MARK, CodepointHelper.CP_HASH)
                || (url_.isSpecial() && input_.codepointIs(CodepointHelper.CP_BACKSLASH))) {
            input_.decreasePointerByOne();
            // 3.1
            if (url_.isSpecial() && buffer().length() == 0) {
                throw validationException(ValidationError.HOST_MISSING);
            }
            // 3.2
            else if (stateOverride_ != null && buffer().length() == 0
                    && (url_.includeCredentials() || url_.port_ != null)) {
                return StateReturnType.RETURN;
            }
            try {
                // 3.3, 3.5
                url_.host_ = HostParser.parse(buffer().toString(), !url_.isSpecial(), this::validationError);
            }
            catch (final ValidationException e) {
                // 3.4
                throw validationException(e.validationError());
            }
            clearBuffer();
            state(State.PATH_START);
            // 3.6
            if (stateOverride_ != null) {
                return StateReturnType.RETURN;
            }
        }
        // 4
        else {
            // 4.1
            if (input_.codepointIs(CodepointHelper.CP_LEFT_SQUARE_BRACKET)) {
                insideBracketsFlag_ = true;
            }
            // 4.2
            if (input_.codepointIs(CodepointHelper.CP_RIGHT_SQUARE_BRACKET)) {
                insideBracketsFlag_ = false;
            }
            // 4.3
            appendToBuffer(input_.codepoint());
        }
        return StateReturnType.CONTINUE;
    }

    private StateReturnType hostnameState() {
        return hostState();
    }

    protected void logInfo() {
        logger.log(Level.FINER,
                () -> "codePoint: " + input_.codepoint() + " - char: "
                        + (input_.isEof() ? "EOF" : Integer.toString(input_.codepoint()))
                        + " - index: " + input_.pointer()
                        + " - state: " + state());
    }

    /**
     * Implementation of the
     * <a href="https://url.spec.whatwg.org/#no-scheme-state">no scheme state</a>.
     * <ul>
     * <li>1) If base is null, or base has an opaque path and c is not U+0023 (#),
     *     missing-scheme-non-relative-URL validation error, return failure.</li>
     * <li>2) Otherwise, if base has an opaque path and c is U+0023 (#), set url's scheme to
     *     base's scheme, url's path to base's path, url's query to base's query, url's fragment
     *     to the empty string, and set state to fragment state.</li>
     * <li>3) Otherwise, if base's scheme is not "file", set state to relative state and decrease
     *     pointer by 1.</li>
     * <li>4) Otherwise, set state to file state and decrease pointer by 1.</li>
     * </ul>
     */
    private StateReturnType noSchemeState() {
        // 1
        if (base_ == null || (base_.hasAnOpaquePath() && input_.codepointIsNot(CodepointHelper.CP_HASH))) {
            throw validationException(ValidationError.MISSING_SCHEME_NON_RELATIVE_URL);
        }
        // 2
        else if (base_.hasAnOpaquePath() && input_.codepointIs(CodepointHelper.CP_HASH)) {
            url_.scheme_ = base_.scheme_;
            url_.path(base_);
            url_.query_ = base_.query_;
            url_.fragment_ = "";
            state(State.FRAGMENT);
        }
        // 3
        else if (!"file".equals(base_.scheme_)) {
            state(State.RELATIVE);
            input_.decreasePointerByOne();
        }
        // 4
        else {
            state(State.FILE);
            input_.decreasePointerByOne();
        }
        return StateReturnType.CONTINUE;
    }

    /**
     * Implementation of the
     * <a href="https://url.spec.whatwg.org/#opaque-path-state">opaque path state</a>.
     * <ul>
     * <li>1) If c is U+003F (?), then set url's query to the empty string and state to query
     *     state.</li>
     * <li>2) Otherwise, if c is U+0023 (#), then set url's fragment to the empty string and state
     *     to fragment state.</li>
     * <li>3) Otherwise:
     *   <ul>
     *   <li>3.1) If c is not the EOF code point, not a URL code point, and not U+0025 (%),
     *       invalid-URL-unit validation error.</li>
     *   <li>3.2) If c is U+0025 (%) and remaining does not start with two ASCII hex digits,
     *       invalid-URL-unit validation error.</li>
     *   <li>3.3) If c is not the EOF code point, UTF-8 percent-encode c using the C0 control
     *       percent-encode set and append the result to url's path.</li>
     *   </ul>
     * </li>
     * </ul>
     */
    private StateReturnType opaquePathState() {
        // 1
        if (input_.codepointIs(CodepointHelper.CP_QUESTION_MARK)) {
            url_.query_ = "";
            state(State.QUERY);
        }
        // 2
        else if (input_.codepointIs(CodepointHelper.CP_HASH)) {
            url_.fragment_ = "";
            state(State.FRAGMENT);
        }
        // 3
        else {
            // 3.1
            if (!input_.isEof() && input_.codepointIsNot(CodepointHelper::isUrlCodepoint)
                    && input_.codepointIsNot(CodepointHelper.CP_PERCENT)) {
                validationError(ValidationError.INVALID_URL_UNIT);
            }
            // 3.2
            if (input_.codepointIs(CodepointHelper.CP_PERCENT)
                    && input_.remainingMatch(2, (pos, cp) -> InfraHelper.isAsciiHexDigit(cp))) {
                validationError(ValidationError.INVALID_URL_UNIT);
            }
            // 3.3
            if (!input_.isEof()) {
                final String utf8PercentEncoded = UrlHelper.utf8PercentEncode(utf8Encoder(), input_.codepoint(),
                        CodepointHelper::isInC0ControlPercentEncodeSet);
                if (url_.path_.isEmpty()) {
                    url_.path_.add(utf8PercentEncoded);
                }
                else {
                    url_.path_.set(0, url_.path_.get(0) + utf8PercentEncoded);
                }
            }
        }
        return StateReturnType.CONTINUE;
    }

    /**
     * Implementation of the
     * <a href="https://url.spec.whatwg.org/#path-or-authority-state">path or authority state</a>.
     * <ul>
     * <li>1) If c is U+002F (/), then set state to authority state.</li>
     * <li>2) Otherwise, set state to path state, and decrease pointer by 1.</li>
     * </ul>
     */
    private StateReturnType pathOrAuthorityState() {
        if (input_.codepointIs(CodepointHelper.CP_SLASH)) {
            state(State.AUTHORITY);
        }
        else {
            state(State.PATH);
            input_.decreasePointerByOne();
        }
        return StateReturnType.CONTINUE;
    }

    /**
     * Implementation of the
     * <a href="https://url.spec.whatwg.org/#path-start-state">path start state</a>.
     * <ul>
     * <li>1) If url is special, then:
     *   <ul>
     *   <li>1.1) If c is U+005C (\), invalid-reverse-solidus validation error.</li>
     *   <li>1.2) Set state to path state.</li>
     *   <li>1.3) If c is neither U+002F (/) nor U+005C (\), then decrease pointer by 1.</li>
     *   </ul>
     * </li>
     * <li>2) Otherwise, if state override is not given and c is U+003F (?), set url's query to
     *     the empty string and state to query state.</li>
     * <li>3) Otherwise, if state override is not given and c is U+0023 (#), set url's fragment to
     *     the empty string and state to fragment state.</li>
     * <li>4) Otherwise, if c is not the EOF code point:
     *   <ul>
     *   <li>4.1) Set state to path state.</li>
     *   <li>4.2) If c is not U+002F (/), then decrease pointer by 1.</li>
     *   </ul>
     * </li>
     * <li>5) Otherwise, if state override is given and url's host is null, append the empty
     *     string to url's path.</li>
     * </ul>
     */
    private StateReturnType pathStartState() {
        // 1
        if (url_.isSpecial()) {
            // 1.1
            if (input_.codepointIs(CodepointHelper.CP_BACKSLASH)) {
                validationError(ValidationError.INVALID_REVERSE_SOLIDUS);
            }
            // 1.2
            state(State.PATH);
            // 1.3
            if (!(input_.codepointIsOneOf(CodepointHelper.CP_SLASH, CodepointHelper.CP_BACKSLASH))) {
                input_.decreasePointerByOne();
            }
        }
        // 2
        else if (stateOverride_ == null && input_.codepointIs(CodepointHelper.CP_QUESTION_MARK)) {
            url_.query_ = "";
            state(State.QUERY);
        }
        // 3
        else if (stateOverride_ == null && input_.codepointIs(CodepointHelper.CP_HASH)) {
            url_.fragment_ = "";
            state(State.FRAGMENT);
        }
        // 4
        else if (input_.codepointIsNot(CodepointHelper.CP_EOF)) {
            // 4.1
            state(State.PATH);
            // 4.2
            if (input_.codepointIsNot(CodepointHelper.CP_SLASH)) {
                input_.decreasePointerByOne();
            }
        }
        // 5
        else if (stateOverride_ != null && url_.host_ == null) {
            url_.path_.add("");
        }
        return StateReturnType.CONTINUE;
    }

    /**
     * Implementation of the
     * <a href="https://url.spec.whatwg.org/#path-state">path state</a>.
     * <ul>
     * <li>1) If one of the following is true:
     *   c is the EOF code point or U+002F (/);
     *   url is special and c is U+005C (\);
     *   state override is not given and c is U+003F (?) or U+0023 (#);
     *   then:
     *   <ul>
     *   <li>1.1) If url is special and c is U+005C (\), invalid-reverse-solidus
     *       validation error.</li>
     *   <li>1.2) If buffer is a double-dot URL path segment, then:
     *     <ul>
     *     <li>1.2.1) Shorten url's path.</li>
     *     <li>1.2.2) If neither c is U+002F (/), nor url is special and c is U+005C (\),
     *         append the empty string to url's path.
     *         <br>This means that for input {@code /usr/..} the result is {@code /} and not a
     *         lack of a path.</li>
     *     </ul>
     *   </li>
     *   <li>1.3) Otherwise, if buffer is a single-dot URL path segment and if neither c is
     *       U+002F (/), nor url is special and c is U+005C (\), append the empty string to
     *       url's path.</li>
     *   <li>1.4) Otherwise, if buffer is not a single-dot URL path segment, then:
     *     <ul>
     *     <li>1.4.1) If url's scheme is "file", url's path is empty, and buffer is a Windows
     *         drive letter, then replace the second code point in buffer with U+003A (:).
     *         <br>This is a (platform-independent) Windows drive letter quirk.</li>
     *     <li>1.4.2) Append buffer to url's path.</li>
     *     </ul>
     *   </li>
     *   <li>1.5) Set buffer to the empty string.</li>
     *   <li>1.6) If c is U+003F (?), then set url's query to the empty string and state to
     *       query state.</li>
     *   <li>1.7) If c is U+0023 (#), then set url's fragment to the empty string and state to
     *       fragment state.</li>
     *   </ul>
     * </li>
     * <li>2) Otherwise, run these steps:
     *   <ul>
     *   <li>2.1) If c is not a URL code point and not U+0025 (%), invalid-URL-unit
     *       validation error.</li>
     *   <li>2.2) If c is U+0025 (%) and remaining does not start with two ASCII hex digits,
     *       invalid-URL-unit validation error.</li>
     *   <li>2.3) UTF-8 percent-encode c using the path percent-encode set and append the result
     *       to buffer.</li>
     *   </ul>
     * </li>
     * </ul>
     */
    private StateReturnType pathState() {
        // 1
        if (input_.codepointIsOneOf(CodepointHelper.CP_EOF, CodepointHelper.CP_SLASH)
                || (url_.isSpecial() && input_.codepointIs(CodepointHelper.CP_BACKSLASH)) || (stateOverride_ == null
                        && (input_.codepointIsOneOf(CodepointHelper.CP_QUESTION_MARK, CodepointHelper.CP_HASH)))) {
            // 1.1
            if (url_.isSpecial() && input_.codepointIs(CodepointHelper.CP_BACKSLASH)) {
                validationError(ValidationError.INVALID_REVERSE_SOLIDUS);
            }
            // 1.2
            if (UrlHelper.isDoubleDotPathSegment(buffer())) {
                // 1.2.1
                url_.shortenPath();
                // 1.2.2
                if (!(input_.codepointIs(CodepointHelper.CP_SLASH)
                        || (url_.isSpecial() && input_.codepointIs(CodepointHelper.CP_BACKSLASH)))) {
                    url_.path_.add("");
                }
            }
            // 1.3
            else if (UrlHelper.isSingleDotPathSegment(buffer()) && (!input_.codepointIs(CodepointHelper.CP_SLASH)
                    && !(url_.isSpecial() && input_.codepointIs(CodepointHelper.CP_BACKSLASH)))) {
                url_.path_.add("");
            }
            // 1.4
            else if (!UrlHelper.isSingleDotPathSegment(buffer())) {
                // 1.4.1
                if ("file".equals(url_.scheme_) && url_.path_.isEmpty()
                        && UrlHelper.isWindowsDriveLetter(buffer().toString(), 0)) {
                    if (buffer().length() > 1) {
                        buffer().replace(1, 2, ":");
                    }
                }
                // 1.4.2
                url_.path_.add(buffer().toString());
            }
            // 1.5
            clearBuffer();
            // 1.6
            if (input_.codepointIs(CodepointHelper.CP_QUESTION_MARK)) {
                url_.query_ = "";
                state(State.QUERY);
            }
            // 1.7
            if (input_.codepointIs(CodepointHelper.CP_HASH)) {
                url_.fragment_ = "";
                state(State.FRAGMENT);
            }
        }
        // 2
        else {
            // 2.1
            if (input_.codepointIsNot(CodepointHelper::isUrlCodepoint)
                    && input_.codepointIsNot(CodepointHelper.CP_PERCENT)) {
                validationError(ValidationError.INVALID_URL_UNIT);
            }
            // 2.2
            if (input_.codepointIs(CodepointHelper.CP_PERCENT)
                    && !input_.remainingMatch(2, (idx, cp) -> InfraHelper.isAsciiHexDigit(cp))) {
                validationError(ValidationError.INVALID_URL_UNIT);
            }
            // 2.3
            buffer_.append(UrlHelper.utf8PercentEncode(utf8Encoder(), input_.codepoint(),
                    CodepointHelper::isInPathPercentEncodeSet));
        }
        return StateReturnType.CONTINUE;
    }

    /**
     * Implementation of the
     * <a href="https://url.spec.whatwg.org/#port-state">port state</a>.
     * <ul>
     * <li>1) If c is an ASCII digit, append c to buffer.</li>
     * <li>2) Otherwise, if one of the following is true:
     *   c is the EOF code point, U+002F (/), U+003F (?), or U+0023 (#);
     *   url is special and c is U+005C (\);
     *   state override is given;
     *   then:
     *   <ul>
     *   <li>2.1) If buffer is not the empty string, then:
     *     <ul>
     *     <li>2.1.1) Let port be the mathematical integer value that is represented by buffer in
     *         radix-10 using ASCII digits for digits with values 0 through 9.</li>
     *     <li>2.1.2) If port is greater than 2<sup>16</sup> &minus; 1, port-out-of-range
     *         validation error, return failure.</li>
     *     <li>2.1.3) Set url's port to null, if port is url's scheme's default port; otherwise
     *         to port.</li>
     *     <li>2.1.4) Set buffer to the empty string.</li>
     *     </ul>
     *   </li>
     *   <li>2.2) If state override is given, then return.</li>
     *   <li>2.3) Set state to path start state and decrease pointer by 1.</li>
     *   </ul>
     * </li>
     * <li>3) Otherwise, port-invalid validation error, return failure.</li>
     * </ul>
     */
    private StateReturnType portState() {
        // 1
        if (InfraHelper.isAsciiDigit(input_.codepoint())) {
            appendToBuffer(input_.codepoint());
        }
        // 2
        else if (input_.codepointIsOneOf(CodepointHelper.CP_EOF, CodepointHelper.CP_SLASH,
                CodepointHelper.CP_QUESTION_MARK, CodepointHelper.CP_HASH)
                || (url_.isSpecial() && input_.codepointIs(CodepointHelper.CP_BACKSLASH)) || stateOverride_ != null) {
            // 2.1
            if (buffer().length() > 0) {
                // 2.1.1
                final Integer port = Integer.valueOf(buffer().toString(), 10);
                // 2.1.2
                if (port > 65535) {
                    throw validationException(ValidationError.PORT_OUT_OF_RANGE);
                }
                // 2.1.3
                final Integer defaultPort = UrlHelper.getDefaultSchemePort(url_.scheme_);
                if (port.equals(defaultPort)) {
                    url_.port_ = null;
                }
                else {
                    url_.port_ = port;
                }
                // 2.1.4
                clearBuffer();
            }
            // 2.2
            if (stateOverride_ != null) {
                return StateReturnType.RETURN;
            }
            // 2.3
            state(State.PATH_START);
            input_.decreasePointerByOne();
        }
        // 3
        else {
            throw validationException(ValidationError.PORT_INVALID);
        }
        return StateReturnType.CONTINUE;
    }

    protected void prependToBuffer(final CharSequence charSequence) {
        buffer_.insert(0, charSequence);
    }

    protected void prependToBuffer(final int codePoint) {
        final char[] chars = Character.toChars(codePoint);
        buffer_.insert(0, chars);
    }

    /**
     * Implementation of the
     * <a href="https://url.spec.whatwg.org/#query-state">query state</a>.
     * <ul>
     * <li>1) If encoding is not UTF-8 and one of the following is true:
     *   url is not special; url's scheme is "ws" or "wss";
     *   then set encoding to UTF-8.</li>
     * <li>2) If one of the following is true:
     *   state override is not given and c is U+0023 (#); c is the EOF code point;
     *   then:
     *   <ul>
     *   <li>2.1) Let queryPercentEncodeSet be the special-query percent-encode set if url is
     *       special; otherwise the query percent-encode set.</li>
     *   <li>2.2) Percent-encode after encoding, with encoding, buffer, and queryPercentEncodeSet,
     *       and append the result to url's query.
     *       <br>This operation cannot be invoked code-point-for-code-point due to the stateful
     *       ISO-2022-JP encoder.</li>
     *   <li>2.3) Set buffer to the empty string.</li>
     *   <li>2.4) If c is U+0023 (#), then set url's fragment to the empty string and state to
     *       fragment state.</li>
     *   </ul>
     * </li>
     * <li>3) Otherwise, if c is not the EOF code point:
     *   <ul>
     *   <li>3.1) If c is not a URL code point and not U+0025 (%), invalid-URL-unit
     *       validation error.</li>
     *   <li>3.2) If c is U+0025 (%) and remaining does not start with two ASCII hex digits,
     *       invalid-URL-unit validation error.</li>
     *   <li>3.3) Append c to buffer.</li>
     *   </ul>
     * </li>
     * </ul>
     */
    private StateReturnType queryState() {
        // 1
        if (!StandardCharsets.UTF_8.equals(encoding_)
                && (!url_.isSpecial()
                        || ("ws".equalsIgnoreCase(url_.scheme_)
                                || "wss".equalsIgnoreCase(url_.scheme_)))) {
            encoding_ = StandardCharsets.UTF_8;
        }
        // 2
        if ((stateOverride_ == null && input_.codepointIs(CodepointHelper.CP_HASH)) || input_.isEof()) {
            // 2.1, 2.2
            final IntPredicate queryPercentEncodeSet =
                    url_.isSpecial()
                    ? CodepointHelper::isSpecialQueryPercentEncodeSet
                    : CodepointHelper::isQueryPercentEncodeSet;
            // 2.2
            final String percentEncodeAfterEncoding = UrlHelper.percentEncodeAfterEncoding(encoder(), buffer_,
                    queryPercentEncodeSet, false);
            url_.appendQuery(percentEncodeAfterEncoding);
            // 2.3
            clearBuffer();
            // 2.4
            if (input_.codepointIs(CodepointHelper.CP_HASH)) {
                url_.fragment_ = "";
                state(State.FRAGMENT);
            }
        }
        // 3
        else if (input_.codepointIsNot(CodepointHelper.CP_EOF)) {
            // 3.1
            if (input_.codepointIsNot(CodepointHelper::isUrlCodepoint)
                    && input_.codepointIsNot(CodepointHelper.CP_PERCENT)) {
                validationError(ValidationError.INVALID_URL_UNIT);
            }
            // 3.2
            if (input_.codepointIs(CodepointHelper.CP_PERCENT)
                    && !input_.remainingMatch(2, (idx, cp) -> InfraHelper.isAsciiHexDigit(cp))) {
                validationError(ValidationError.INVALID_URL_UNIT);
            }
            // 3.3
            appendToBuffer(input_.codepoint());
        }
        return StateReturnType.CONTINUE;
    }

    /**
     * Implementation of the
     * <a href="https://url.spec.whatwg.org/#relative-slash-state">relative slash state</a>.
     * <ul>
     * <li>1) If url is special and c is U+002F (/) or U+005C (\), then:
     *   <ul>
     *   <li>1.1) If c is U+005C (\), invalid-reverse-solidus validation error.</li>
     *   <li>1.2) Set state to special authority ignore slashes state.</li>
     *   </ul>
     * </li>
     * <li>2) Otherwise, if c is U+002F (/), then set state to authority state.</li>
     * <li>3) Otherwise, set url's username to base's username, url's password to base's password,
     *     url's host to base's host, url's port to base's port, state to path state, and then
     *     decrease pointer by 1.</li>
     * </ul>
     */
    private StateReturnType relativeSlashState() {
        // 1
        if (url_.isSpecial() && (input_.codepointIsOneOf(CodepointHelper.CP_SLASH, CodepointHelper.CP_BACKSLASH))) {
            // 1.1
            if (input_.codepointIs(CodepointHelper.CP_BACKSLASH)) {
                validationError(ValidationError.INVALID_REVERSE_SOLIDUS);
            }
            // 1.2
            state(State.SPECIAL_AUTHORITY_IGNORE_SLASHES);
        }
        // 2
        else if (input_.codepointIs(CodepointHelper.CP_SLASH)) {
            state(State.AUTHORITY);
        }
        // 3
        else {
            url_.usernamePasswordHostPort(base_);
            state(State.PATH);
            input_.decreasePointerByOne();
        }
        return StateReturnType.CONTINUE;
    }

    /**
     * Implementation of the
     * <a href="https://url.spec.whatwg.org/#relative-state">relative state</a>.
     * <ul>
     * <li>1) Assert: base's scheme is not "file".</li>
     * <li>2) Set url's scheme to base's scheme.</li>
     * <li>3) If c is U+002F (/), then set state to relative slash state.</li>
     * <li>4) Otherwise, if url is special and c is U+005C (\), invalid-reverse-solidus validation
     *     error, set state to relative slash state.</li>
     * <li>5) Otherwise:
     *   <ul>
     *   <li>5.1) Set url's username to base's username, url's password to base's password, url's
     *       host to base's host, url's port to base's port, url's path to a clone of base's path,
     *       and url's query to base's query.</li>
     *   <li>5.2) If c is U+003F (?), then set url's query to the empty string, and state to query
     *       state.</li>
     *   <li>5.3) Otherwise, if c is U+0023 (#), set url's fragment to the empty string and state
     *       to fragment state.</li>
     *   <li>5.4) Otherwise, if c is not the EOF code point:
     *     <ul>
     *     <li>5.4.1) Set url's query to null.</li>
     *     <li>5.4.2) Shorten url's path.</li>
     *     <li>5.4.3) Set state to path state and decrease pointer by 1.</li>
     *     </ul>
     *   </li>
     *   </ul>
     * </li>
     * </ul>
     */
    private StateReturnType relativeState() {
        // TODO properly implement rule 1, throwing an exception
        // 2
        url_.scheme_ = base_.scheme_;
        // 3
        if (input_.codepointIs(CodepointHelper.CP_SLASH)) {
            state(State.RELATIVE_SLASH);
        }
        // 4
        else if (url_.isSpecial() && input_.codepointIs(CodepointHelper.CP_BACKSLASH)) {
            validationError(ValidationError.INVALID_REVERSE_SOLIDUS);
            state(State.RELATIVE_SLASH);
        }
        // 5
        else {
            // 5.1
            url_.usernamePasswordHostPortPath(base_);
            url_.query_ = base_.query_;
            // 5.2
            if (input_.codepointIs(CodepointHelper.CP_QUESTION_MARK)) {
                url_.query_ = "";
                state(State.QUERY);
            }
            // 5.3
            else if (input_.codepointIs(CodepointHelper.CP_HASH)) {
                url_.fragment_ = "";
                state(State.FRAGMENT);
            }
            // 5.4
            else if (!input_.isEof()) {
                url_.query_ = null;
                url_.shortenPath();
                state(State.PATH);
                input_.decreasePointerByOne();
            }
        }
        return StateReturnType.CONTINUE;
    }

    /**
     * Implementation of the
     * <a href="https://url.spec.whatwg.org/#scheme-start-state">scheme start state</a>.
     * <ul>
     * <li>1) If c is an ASCII alpha, append c, lowercased, to buffer, and set state to scheme
     *     state.</li>
     * <li>2) Otherwise, if state override is not given, set state to no scheme state and decrease
     *     pointer by 1.</li>
     * <li>3) Otherwise, return failure.
     *     <br>This indication of failure is used exclusively by the Location object's protocol
     *     setter.</li>
     * </ul>
     */
    private StateReturnType schemeStartState() {
        // 1
        if (InfraHelper.isAsciiAlpha(input_.codepoint())) {
            appendToBuffer(Character.toLowerCase(input_.codepoint()));
            state(State.SCHEME);
            return StateReturnType.CONTINUE;
        }
        // 2
        else if (stateOverride_ == null) {
            state(State.NO_SCHEME);
            input_.decreasePointerByOne();
            return StateReturnType.CONTINUE;
        }
        // 3
        throw validationException(ValidationError.INVALID_URL_UNIT);
    }

    /**
     * Implementation of the
     * <a href="https://url.spec.whatwg.org/#scheme-state">scheme state</a>.
     * <p>
     * This indication of failure is used exclusively by the Location object's protocol setter.
     * Furthermore, the non-failure termination earlier in this state is an intentional difference
     * for defining that setter.
     * </p>
     * <ul>
     * <li>1) If c is an ASCII alphanumeric, U+002B (+), U+002D (-), or U+002E (.), append c,
     *     lowercased, to buffer.</li>
     * <li>2) Otherwise, if c is U+003A (:), then:
     *   <ul>
     *   <li>2.1) If state override is given, then:
     *     <ul>
     *     <li>2.1.1) If url's scheme is a special scheme and buffer is not a special scheme,
     *         then return.</li>
     *     <li>2.1.2) If url's scheme is not a special scheme and buffer is a special scheme,
     *         then return.</li>
     *     <li>2.1.3) If url includes credentials or has a non-null port, and buffer is "file",
     *         then return.</li>
     *     <li>2.1.4) If url's scheme is "file" and its host is an empty host, then return.</li>
     *     </ul>
     *   </li>
     *   <li>2.2) Set url's scheme to buffer.</li>
     *   <li>2.3) If state override is given, then:
     *     <ul>
     *     <li>2.3.1) If url's port is url's scheme's default port, then set url's port to
     *         null.</li>
     *     <li>2.3.2) Return.</li>
     *     </ul>
     *   </li>
     *   <li>2.4) Set buffer to the empty string.</li>
     *   <li>2.5) If url's scheme is "file", then:
     *     <ul>
     *     <li>2.5.1) If remaining does not start with "//", special-scheme-missing-following-solidus
     *         validation error.</li>
     *     <li>2.5.2) Set state to file state.</li>
     *     </ul>
     *   </li>
     *   <li>2.6) Otherwise, if url is special, base is non-null, and base's scheme is url's
     *       scheme:
     *     <ul>
     *     <li>2.6.1) Assert: base is special (and therefore does not have an opaque path).</li>
     *     <li>2.6.2) Set state to special relative or authority state.</li>
     *     </ul>
     *   </li>
     *   <li>2.7) Otherwise, if url is special, set state to special authority slashes state.</li>
     *   <li>2.8) Otherwise, if remaining starts with U+002F (/), set state to path or authority
     *       state and increase pointer by 1.</li>
     *   <li>2.9) Otherwise, set url's path to the empty string and set state to opaque path
     *       state.</li>
     *   </ul>
     * </li>
     * <li>3) Otherwise, if state override is not given, set buffer to the empty string, state to
     *     no scheme state, and start over (from the first code point in input).</li>
     * <li>4) Otherwise, return failure.</li>
     * </ul>
     */
    private StateReturnType schemeState() {
        // 1
        if (InfraHelper.isAsciiAlphanumeric(input_.codepoint()) || input_.codepointIsOneOf(CodepointHelper.CP_PLUS,
                CodepointHelper.CP_MINUS, CodepointHelper.CP_PERIOD)) {
            appendToBuffer(Character.toLowerCase(input_.codepoint()));
            return StateReturnType.CONTINUE;
        }
        else if (input_.codepointIs(CodepointHelper.CP_COLON)) {
            // 2.1
            if (stateOverride_ != null) {
                // 2.1.1
                if (url_.isSpecial() && !UrlHelper.isSpecialScheme(buffer())) {
                    return StateReturnType.RETURN;
                }
                // 2.1.2
                if (!url_.isSpecial() && UrlHelper.isSpecialScheme(buffer())) {
                    return StateReturnType.RETURN;
                }
                // 2.1.3
                if (url_.includeCredentials() || (url_.port_ != null && buffer().toString().equals("file"))) {
                    return StateReturnType.RETURN;
                }
                // 2.1.4
                if ("file".equals(url_.scheme_) && url_.host_ instanceof EmptyHost) {
                    return StateReturnType.RETURN;
                }
            }
            // 2.2
            url_.scheme_ = buffer().toString();
            // 2.3
            if (stateOverride_ != null) {
                // 2.3.1
                final Integer defaultPort = UrlHelper.getDefaultSchemePort(url_.scheme_);
                if (defaultPort != null && defaultPort.equals(url_.port_)) {
                    url_.port_ = null;
                }
                // 2.3.2
                return StateReturnType.RETURN;
            }
            // 2.4
            clearBuffer();
            // 2.5
            if ("file".equals(url_.scheme_)) {
                if (!input_.remainingMatch(2, (idx, cp) -> cp == CodepointHelper.CP_SLASH)) {
                    validationError(ValidationError.SPECIAL_SCHEME_MISSING_FOLLOWING_SOLIDUS);
                }
                state(State.FILE);
            }
            // 2.6
            else if (url_.isSpecial() && base_ != null && base_.scheme_.equals(url_.scheme_)) {
                state(State.SPECIAL_RELATIVE_OR_AUTHORITY);
            }
            // 2.7
            else if (url_.isSpecial()) {
                state(State.SPECIAL_AUTHORITY_SLASHES);
            }
            // 2.8
            else if (input_.remainingMatch(1, (idx, cp) -> cp == CodepointHelper.CP_SLASH)) {
                state(State.PATH_OR_AUTHORITY);
                input_.increasePointerByOne();
            }
            // 2.9
            else {
                url_.setOpaqueState();
                state(State.OPAQUE_PATH);
            }
            return StateReturnType.CONTINUE;
        }
        // 3
        else if (stateOverride_ == null) {
            clearBuffer();
            state(State.NO_SCHEME);
            input_.startOver();
            return StateReturnType.CONTINUE;
        }
        // 4
        throw validationException(ValidationError.INVALID_URL_UNIT);
    }

    /**
     * Implementation of the
     * <a href="https://url.spec.whatwg.org/#special-authority-ignore-slashes-state">special
     * authority ignore slashes state</a>.
     * <ul>
     * <li>1) If c is neither U+002F (/) nor U+005C (\), then set state to authority state and
     *     decrease pointer by 1.</li>
     * <li>2) Otherwise, special-scheme-missing-following-solidus validation error.</li>
     * </ul>
     */
    private StateReturnType specialAuthorityIgnoreSlashState() {
        // 1
        if (!(input_.codepointIsOneOf(CodepointHelper.CP_SLASH, CodepointHelper.CP_BACKSLASH))) {
            state(State.AUTHORITY);
            input_.decreasePointerByOne();
        }
        else {
            validationError(ValidationError.SPECIAL_SCHEME_MISSING_FOLLOWING_SOLIDUS);
        }
        return StateReturnType.CONTINUE;
    }

    /**
     * Implementation of the
     * <a href="https://url.spec.whatwg.org/#special-authority-slashes-state">special authority
     * slashes state</a>.
     * <ul>
     * <li>1) If c is U+002F (/) and remaining starts with U+002F (/), then set state to special
     *     authority ignore slashes state and increase pointer by 1.</li>
     * <li>2) Otherwise, special-scheme-missing-following-solidus validation error, set state to
     *     special authority ignore slashes state and decrease pointer by 1.</li>
     * </ul>
     */
    private StateReturnType specialAuthoritySlashesState() {
        // 1
        if (input_.codepointIs(CodepointHelper.CP_SLASH)
                && input_.remainingMatch(1, (idx, cp) -> cp == CodepointHelper.CP_SLASH)) {
            state(State.SPECIAL_AUTHORITY_IGNORE_SLASHES);
            input_.increasePointerByOne();
        }
        // 2
        else {
            validationError(ValidationError.SPECIAL_SCHEME_MISSING_FOLLOWING_SOLIDUS);
            state(State.SPECIAL_AUTHORITY_IGNORE_SLASHES);
            input_.decreasePointerByOne();
        }
        return StateReturnType.CONTINUE;
    }

    /**
     * Implementation of the
     * <a href="https://url.spec.whatwg.org/#special-relative-or-authority-state">special relative
     * or authority state</a>.
     * <ul>
     * <li>1) If c is U+002F (/) and remaining starts with U+002F (/), then set state to special
     *     authority ignore slashes state and increase pointer by 1.</li>
     * <li>2) Otherwise, special-scheme-missing-following-solidus validation error, set state to
     *     relative state and decrease pointer by 1.</li>
     * </ul>
     */
    private StateReturnType specialRelativeOrAuthorityState() {
        if (input_.codepointIs(CodepointHelper.CP_SLASH)
                && input_.remainingMatch(1, (idx, cp) -> cp == CodepointHelper.CP_SLASH)) {
            state(State.SPECIAL_AUTHORITY_IGNORE_SLASHES);
            input_.increasePointerByOne();
        }
        else {
            validationError(ValidationError.SPECIAL_SCHEME_MISSING_FOLLOWING_SOLIDUS);
            state(State.RELATIVE);
            input_.decreasePointerByOne();
        }
        return StateReturnType.CONTINUE;
    }

    protected State state() {
        return state_;
    }

    protected void state(final State state) {
        state_ = Objects.requireNonNull(state);
    }

    protected void stateMachine() {
        logger.log(Level.FINER, () -> "Starting state machine - position: " + input_.pointer() + " - base: " + base_
                + " - state override: " + stateOverride_);
        while (true) {
            logInfo();
            StateReturnType returnValue = null;
            switch (state()) {
                case SCHEME_START:
                    returnValue = schemeStartState();
                    break;
                case SCHEME:
                    returnValue = schemeState();
                    break;
                case NO_SCHEME:
                    returnValue = noSchemeState();
                    break;
                case SPECIAL_RELATIVE_OR_AUTHORITY:
                    returnValue = specialRelativeOrAuthorityState();
                    break;
                case PATH_OR_AUTHORITY:
                    returnValue = pathOrAuthorityState();
                    break;
                case RELATIVE:
                    returnValue = relativeState();
                    break;
                case RELATIVE_SLASH:
                    returnValue = relativeSlashState();
                    break;
                case SPECIAL_AUTHORITY_SLASHES:
                    returnValue = specialAuthoritySlashesState();
                    break;
                case SPECIAL_AUTHORITY_IGNORE_SLASHES:
                    returnValue = specialAuthorityIgnoreSlashState();
                    break;
                case AUTHORITY:
                    returnValue = authorityState();
                    break;
                case HOST:
                    returnValue = hostState();
                    break;
                case HOSTNAME:
                    returnValue = hostnameState();
                    break;
                case PORT:
                    returnValue = portState();
                    break;
                case FILE:
                    returnValue = fileState();
                    break;
                case FILE_SLASH:
                    returnValue = fileSlashState();
                    break;
                case FILE_HOST:
                    returnValue = fileHostState();
                    break;
                case PATH_START:
                    returnValue = pathStartState();
                    break;
                case PATH:
                    returnValue = pathState();
                    break;
                case OPAQUE_PATH:
                    returnValue = opaquePathState();
                    break;
                case QUERY:
                    returnValue = queryState();
                    break;
                case FRAGMENT:
                    returnValue = fragmentState();
                    break;
            }
            if (returnValue == StateReturnType.RETURN) {
                return;
            }
            if (input_.codepointIs(CodepointHelper.CP_EOF)) {
                return;
            }
            input_.increasePointerByOne();
        }
    }

    CharsetEncoder utf8Encoder() {
        if (utf8Encoder_ == null) {
            utf8Encoder_ = StandardCharsets.UTF_8.newEncoder();
        }
        return utf8Encoder_;
    }

    void validationError(final ValidationError error) {
        url_.validationError(error);
    }

    ValidationException validationException(final ValidationError error) {
        validationError(error);
        return new ValidationException(error);
    }

    private enum StateReturnType {
        RETURN, CONTINUE,
    }

    public enum State {
        SCHEME_START, SCHEME, NO_SCHEME, SPECIAL_RELATIVE_OR_AUTHORITY, PATH_OR_AUTHORITY, RELATIVE, RELATIVE_SLASH,
        SPECIAL_AUTHORITY_SLASHES, SPECIAL_AUTHORITY_IGNORE_SLASHES, AUTHORITY, HOST, HOSTNAME, PORT, FILE, FILE_SLASH,
        FILE_HOST, PATH_START, PATH, OPAQUE_PATH, QUERY, FRAGMENT,
    }
}
