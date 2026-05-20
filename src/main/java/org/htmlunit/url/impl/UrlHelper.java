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

import java.io.ByteArrayOutputStream;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.IntPredicate;

import org.htmlunit.url.ValidationError;
import org.htmlunit.url.ValidationException;

import com.ibm.icu.text.IDNA;

/**
 * Helper class providing miscellaneous URL-processing utilities used by the URL parser.
 *
 * @author Stephane Bastian
 * @author Ronald Brill
 */
final class UrlHelper {

    private UrlHelper() {
        //utility class
    }

    private static IDNA UTS46NonStrictInstance_ = IDNA.getUTS46Instance(IDNA.CHECK_BIDI | IDNA.CHECK_CONTEXTJ
            | /* Transitional_Processing set to false */ IDNA.NONTRANSITIONAL_TO_ASCII
            | IDNA.NONTRANSITIONAL_TO_UNICODE);
    private static IDNA UTS46strictInstance_ = IDNA.getUTS46Instance(IDNA.CHECK_BIDI | IDNA.CHECK_CONTEXTJ
            | /* Transitional_Processing set to false */ IDNA.NONTRANSITIONAL_TO_ASCII | IDNA.NONTRANSITIONAL_TO_UNICODE
            | /* strict set to true */ IDNA.USE_STD3_RULES);

    public static int codePoint(final CharSequence input, final int pointer) {
        Objects.requireNonNull(input);
        if (pointer >= 0 && pointer < input.length()) {
            return Character.codePointAt(input, pointer);
        }
        if (input.length() == 0) {
            return CodepointHelper.CP_EOF;
        }
        if (pointer >= input.length()) {
            return CodepointHelper.CP_EOF;
        }
        return CodepointHelper.CP_BOF;
    }

    /**
     * Returns the codepoints of the given string as an int array.
     *
     * @param value the string to convert
     * @return an int array of Unicode code points
     */
    public static int[] codepoints(final String value) {
        Objects.requireNonNull(value);
        return value.codePoints().toArray();
    }

    /**
     * <pre>
     *   The domain to ASCII algorithm, given a string domain and a boolean beStrict, runs these steps:
     *   <ul>
     *     <li>1) Let result be the result of running Unicode ToASCII with
     *     domain_name set to domain,
     *     UseSTD3ASCIIRules set to beStrict,
     *     CheckHyphens set to false,
     *     CheckBidi set to true,
     *     CheckJoiners set to true,
     *     Transitional_Processing set to false,
     *     and VerifyDnsLength set to beStrict. [UTS46]</li>
     *     <br>
     *     <br>If beStrict is false, domain is an ASCII string, and strictly splitting
     *     domain on U+002E (.) does not produce any item that starts with an ASCII
     *     case-insensitive match for "xn--", this step is equivalent to ASCII lowercasing domain.</li>
     *     <li>2) If result is a failure value, domain-to-ASCII validation error, return failure.</li>
     *     <li>3) If result is the empty string, domain-to-ASCII validation error, return failure.</li>
     *     <li>4) Return result.</li>
     *   </ul>
     * </pre>
     *
     * @param domain   the domain to convert
     * @param beStrict a boolean set to true to perform a strict conversion
     * @return the converted value
     */
    public static String domainToAscii(final String domain, final boolean beStrict) {
        // special case for domain with empty labels
        if (".".equals(domain) || "..".equals(domain)) {
            return domain;
        }
        // 1
        // we've got to use UTR46 from ICU4J otherwise, IDN built-in java choke on some
        // domain names
        final StringBuilder result = new StringBuilder(domain.length());
        final IDNA.Info idnaInfo = new IDNA.Info();
        if (beStrict) {
            UTS46strictInstance_.nameToASCII(domain, result, idnaInfo);
        }
        else {
            UTS46NonStrictInstance_.nameToASCII(domain, result, idnaInfo);
        }
        // 2
        for (final IDNA.Error error : idnaInfo.getErrors()) {
            // equivalent to checkHyphens==false
            if (IDNA.Error.HYPHEN_3_4.equals(error) || IDNA.Error.LEADING_HYPHEN.equals(error)
                    || IDNA.Error.TRAILING_HYPHEN.equals(error)) {
                continue;
            }
            // in non-strict mode let ignore label-too-long or domain-too-long
            if (!beStrict
                    && (IDNA.Error.DOMAIN_NAME_TOO_LONG.equals(error) || IDNA.Error.LABEL_TOO_LONG.equals(error))) {
                continue;
            }
            // lets ignore empty labels
            if (IDNA.Error.EMPTY_LABEL.equals(error)) {
                continue;
            }
            throw new ValidationException(ValidationError.DOMAIN_TO_ASCII);
        }
        // 3
        if (result.length() == 0) {
            throw new ValidationException(ValidationError.DOMAIN_TO_ASCII);
        }
        // 4
        return result.toString();
    }

    /**
     * <pre>
     *   The domain to Unicode algorithm, given a domain domain and a boolean beStrict, runs these steps:
     *   <ul>
     *     <li>1) Let result be the result of running Unicode ToUnicode with
     *     domain_name set to domain,
     *     CheckHyphens set to false,
     *     CheckBidi set to true,
     *     CheckJoiners set to true,
     *     UseSTD3ASCIIRules set to beStrict,
     *     and Transitional_Processing set to false. [UTS46]
     *     </li>
     *     <li>2) Signify domain-to-Unicode validation errors for any returned errors, and then, return result.</li>
     *   </ul>
     * </pre>
     *
     * @param domain   the domain to convert
     * @param beStrict a boolean set to true to perform a strict conversion
     * @return the converted value
     */
    public static String domainToUnicode(final String domain, final boolean beStrict) {
        try {
            // 1
            // we've got to use UTR46 from ICU4J otherwise, IDN built-in java choke on some
            // domain names
            final StringBuilder result = new StringBuilder(domain.length());
            final IDNA.Info idnaInfo = new IDNA.Info();
            if (beStrict) {
                UTS46strictInstance_.nameToUnicode(domain, result, idnaInfo);
            }
            else {
                UTS46NonStrictInstance_.nameToUnicode(domain, result, idnaInfo);
            }
            if (result.length() == 0) {
                throw new ValidationException(ValidationError.DOMAIN_TO_ASCII);
            }
            // 4
            return result.toString();
        }
        catch (final Exception e) {
            // 2
            throw new ValidationException(ValidationError.DOMAIN_TO_ASCII);
        }
    }

    public static Integer getDefaultSchemePort(final String scheme) {
        switch (scheme) {
            case "ftp":
                return 21;
            case "http":
                return 80;
            case "https":
                return 443;
            case "ws":
                return 80;
            case "wss":
                return 443;
        }
        return null;
    }

    public static boolean hasAsciiTabOrNewline(final int[] codepoints) {
        return numberOfAsciiTabOrNewline(codepoints) > 0;
    }

    public static boolean hasLeadingOrTrailingC0ControlOrSpace(final int[] codepoints) {
        return codepoints != null && codepoints.length > 0 && (InfraHelper.isC0ControlOrSpace(codepoints[0])
                || InfraHelper.isC0ControlOrSpace(codepoints[codepoints.length - 1]));
    }

    /**
     * A double-dot path segment must be ".." or an ASCII case-insensitive match for
     * ".%2e", "%2e.", or "%2e%2e".
     *
     * @param value the path segment to check
     * @return true if the value is a double-dot path segment, false otherwise
     */
    public static boolean isDoubleDotPathSegment(final CharSequence value) {
        return "..".contentEquals(value) || ".%2e".contentEquals(value) || ".%2E".contentEquals(value)
                || "%2e.".contentEquals(value) || "%2E.".contentEquals(value) || "%2e%2e".contentEquals(value)
                || "%2e%2E".contentEquals(value) || "%2E%2e".contentEquals(value) || "%2E%2E".contentEquals(value);
    }

    /**
     * A normalized Windows drive letter is a Windows drive letter of which the
     * second code point is U+003A (:).
     *
     * @param value the string to check
     * @return true if the value is a normalized Windows drive letter, false otherwise
     */
    public static boolean isNormalizedWindowsDriveLetter(final String value) {
        return value.length() == 2 && isWindowsDriveLetter(value, 0) && value.codePointAt(1) == 0x003A;
    }

    /**
     * A single-dot path segment must be "." or an ASCII case-insensitive match for
     * "%2e".
     *
     * @param value the path segment to check
     * @return true if the value is a single-dot path segment, false otherwise
     */
    public static boolean isSingleDotPathSegment(final CharSequence value) {
        return ".".contentEquals(value) || "%2e".contentEquals(value) || "%2E".contentEquals(value);
    }

    public static boolean isSpecialScheme(final CharSequence scheme) {
        return "ftp".contentEquals(scheme) || "file".contentEquals(scheme) || "http".contentEquals(scheme)
                || "https".contentEquals(scheme) || "ws".contentEquals(scheme) || "wss".contentEquals(scheme);
    }

    public static boolean isWindowsDriveLetter(final int cp1, final int cp2) {
        return InfraHelper.isAsciiAlpha(cp1) && (cp2 == 0x003A || cp2 == 0x007C);
    }

    /**
     * A Windows drive letter is two code points, of which the first is an ASCII
     * alpha and the second is either U+003A (:) or U+007C (|).
     *
     * @param input  the string to check
     * @param offset the offset in the string at which to start checking
     * @return true if the two code points at offset form a Windows drive letter, false otherwise
     */
    public static boolean isWindowsDriveLetter(final String input, final int offset) {
        if (offset + 1 < input.length()) {
            return isWindowsDriveLetter(input.codePointAt(offset), input.codePointAt(offset + 1));
        }
        return false;
    }

    /**
     * To isomorphic decode a byte sequence input, return a string whose length is
     * equal to input's length and whose code points have the same values as input's
     * bytes, in the same order.
     *
     * @param bytes   the byte array to decode
     * @param offset  the start offset within the array
     * @param length  the number of bytes to decode
     * @param charset the charset to use for decoding
     * @return the decoded string
     */
    public static String isomorphicDecode(final byte[] bytes, final int offset,
            final int length, final Charset charset) {
        return new String(bytes, offset, length, charset);
    }

    /**
     * Returns the number of ASCII tab and newline code points in the given array.
     *
     * @param codepoints the array to inspect
     * @return the number of ASCII tab and newline code points
     */
    public static int numberOfAsciiTabOrNewline(final int[] codepoints) {
        int result = 0;
        for (int i = 0; i < codepoints.length; i++) {
            if (InfraHelper.isAsciiTabOrNewLine(codepoints[i])) {
                result++;
            }
        }
        return result;
    }

    /**
     * <pre>
     *   The application/x-www-form-urlencoded parser takes a byte sequence input, and then runs these steps:
     *   <ul>
     *     <li>1) Let sequences be the result of splitting input on 0x26 (&).</li>
     *     <li>2) Let output be an initially empty list of name-value tuples where both name and value hold a string.</li>
     *     <li>3) For each byte sequence bytes in sequences:
     *       <ul>
     *         <li>3.1) If bytes is the empty byte sequence, then continue.</li>
     *         <li>3.2) If bytes contains a 0x3D (=), then let name be the bytes from the start of bytes up to but
     *         excluding its first 0x3D (=), and let value be the bytes, if any, after the first 0x3D (=) up to the
     *         end of bytes. If 0x3D (=) is the first byte, then name will be the empty byte sequence. If it is the
     *         last, then value will be the empty byte sequence.</li>
     *         <li>3.3) Otherwise, let name have the value of bytes and let value be the empty byte sequence.</li>
     *         <li>3.4) Replace any 0x2B (+) in name and value with 0x20 (SP).</li>
     *         <li>3.5) Let nameString and valueString be the result of running UTF-8 decode without BOM on the
     *         percent-decoding of name and value, respectively.</li>
     *         <li>3.6) Append (nameString, valueString) to output.</li>
     *       </ul>
     *     </li>
     *     <li>4) Return output.</li>
     *   </ul>
     * </pre>
     *
     * @param input the application/x-www-form-urlencoded string to parse
     * @return a list of name-value string pairs
     */
    public static List<List<String>> parseFormUrlEncoded(final String input) {
        Objects.requireNonNull(input);
        // 1
        final List<List<String>> output = new ArrayList<>();
        // 2
        final String[] sequences = input.split("&");
        for (final String sequence : sequences) {
            // 3.1
            if (sequence.isEmpty()) {
                continue;
            }
            // 3.2
            String name = null;
            String value = null;
            final int idxOfEqual = sequence.indexOf('=');
            if (idxOfEqual != -1) {
                name = sequence.substring(0, idxOfEqual);
                value = sequence.substring(idxOfEqual + 1);
            }
            // 3.3
            else {
                name = sequence;
                value = "";
            }
            // 3.4
            name = name.replace('=', ' ');
            value = value.replace('=', ' ');
            // 3.5
            name = EncodingHelper.utf8DecodeWithoutBom(percentDecode(name));
            value = EncodingHelper.utf8DecodeWithoutBom(percentDecode(value));
            // 3.6
            final List<String> entry = new ArrayList<>();
            entry.add(name);
            entry.add(value);
            output.add(entry);
        }
        return output;
    }

    /**
     * <pre>
     * To percent-decode a byte sequence input, run these steps: <br>
     * Using anything but UTF-8 decode without BOM when input contains bytes that are not ASCII bytes
     * might be insecure and is not recommended.
     * <ul>
     *   <li>1) Let output be an empty byte sequence.</li>
     *   <li>2) For each byte byte in input:
     *     <ul>
     *       <li>2.1) If byte is not 0x25 (%), then append byte to output.</li>
     *       <li>2.2) Otherwise, if byte is 0x25 (%) and the next two bytes after byte in input are not in
     *       the ranges 0x30 (0) to 0x39 (9), 0x41 (A) to 0x46 (F), and 0x61 (a) to 0x66 (f), all inclusive,
     *       append byte to output.</li>
     *       <li>2.3) Otherwise:
     *         <ul>
     *           <li>2.3.1) Let bytePoint be the two bytes after byte in input, decoded, and then interpreted as
     *           hexadecimal number.</li>
     *           <li>2.3.2) Append a byte whose value is bytePoint to output.</li>
     *           <li>2.3.3) Skip the next two bytes in input.</li>
     *         </ul>
     *       </li>
     *     </ul>
     *   </li>
     *   <li>3) Return output.</li>
     * </ul>
     * </pre>
     *
     * @param input the byte sequence to percent-decode
     * @return the decoded byte sequence
     */
    public static byte[] percentDecode(final byte[] input) {
        // 1
        final ByteArrayOutputStream output = new ByteArrayOutputStream(input.length);
        // 2
        final int length = input.length;
        for (int i = 0; i < length; i++) {
            // 2.1
            if (input[i] != CodepointHelper.CP_PERCENT) {
                output.write(input[i]);
            }
            // 2.2
            else if (i + 2 < length && InfraHelper.isAsciiHexDigit(input[i + 1])
                    && InfraHelper.isAsciiHexDigit(input[i + 2])) {
                final String isomorphicDecode = InfraHelper.isomorphicDecode(new byte[] {input[i + 1], input[i + 2]});
                final int bytePoint = Integer.parseInt(isomorphicDecode, 16);
                output.write(bytePoint);
                i += 2;
            }
            // 2.3
            else {
                output.write(input[i]);
            }
        }
        return output.toByteArray();
    }

    /**
     * To string percent decode a string input, run these steps:
     * 1) Let bytes be the UTF-8 encoding of input.
     * 2) Return the percent decoding of bytes.
     *
     * @param input the string to percent-decode
     * @return the decoded byte sequence
     */
    public static byte[] percentDecode(final String input) {
        return percentDecode(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * To percent-encode a byte, return a string consisting of U+0025 (%), followed
     * by two ASCII upper hex digits representing the byte.
     *
     * @param abyte the byte to percent-encode
     * @return the percent-encoded string (e.g. {@code "%2F"})
     */
    public static String percentEncode(final byte abyte) {
        // unsigned byte
        final int i = abyte & 0xFF;
        final String result = Integer.toHexString(i).toUpperCase();
        if (result.length() == 1) {
            // make sure the result is always 2 digits
            return "%0" + result;
        }
        return "%" + result;
    }

    /**
     * To percent-encode a byte, write U+0025 (%) followed by two ASCII upper hex
     * digits representing the byte into the given output stream.
     *
     * @param abyte  the byte to percent-encode
     * @param result the output stream to write the percent-encoded bytes into
     */
    public static void percentEncode(final byte abyte, final ByteArrayOutputStream result) {
        result.write('%');
        final char[] hexChars = InfraHelper.toHexChars(abyte);
        result.write(hexChars[0]);
        result.write(hexChars[1]);
    }

    public static String percentEncodeAfterEncoding(final CharsetEncoder encoder,
            final int codepoint, final IntPredicate isInEncodeSet, final boolean spaceAsPlus) {
        return percentEncodeAfterEncoding(encoder, new String(Character.toChars(codepoint)), isInEncodeSet,
                spaceAsPlus);
    }

    /**
     * <pre>
     *   To percent-encode after encoding, given an encoding encoding, scalar value
     *   string input, a percentEncodeSet, and an optional boolean spaceAsPlus
     *   (default false):
     *   <ul>
     *     <li>1) Let encoder be the result of getting an encoder from encoding.</li>
     *     <li>2) Let inputQueue be input converted to an I/O queue.</li>
     *     <li>3) Let output be the empty string.</li>
     *     <li>4) Let potentialError be 0.
     *     <br>This needs to be a non-null value to initiate the subsequent while loop.
     *     </li>
     *     <li>5) While potentialError is non-null:
     *       <ul>
     *         <li>5.1) Let encodeOutput be an empty I/O queue.</li>
     *         <li>5.2) Set potentialError to the result of running encode or fail with inputQueue, encoder,
     *         and encodeOutput.</li>
     *         <li>5.3) For each byte of encodeOutput converted to a byte sequence:
     *           <ul>
     *             <li>5.3.1) If spaceAsPlus is true and byte is 0x20 (SP), then append U+002B (+) to output
     *             and continue.</li>
     *             <li>5.3.3) Assert: percentEncodeSet includes all non-ASCII code points.</li>
     *             <li>5.3.4) If isomorph is not in percentEncodeSet, then append isomorph to output.</li>
     *             <li>5.3.5) Otherwise, percent-encode byte and append the result to output.</li>
     *           </ul>
     *         </li>
     *         <li>5.4) If potentialError is non-null, then append "%26%23", followed by the shortest sequence
     *         of ASCII digits representing potentialError in base ten, followed by "%3B", to output.
     *           <br>
     *           <br>This can happen when encoding is not UTF-8.
     *         </li>
     *       </ul>
     *     </li>
     *     <li>6) Return output.</li>
     *   </ul>
     * </pre>
     *
     * @param encoder        the charset encoder to use
     * @param input          the scalar value string to encode
     * @param isInEncodeSet  predicate that returns true if a code point must be percent-encoded
     * @param spaceAsPlus    if true, encode U+0020 SPACE as {@code +} instead of {@code %20}
     * @return the percent-encoded string
     */
    public static String percentEncodeAfterEncoding(final CharsetEncoder encoder, final CharSequence input,
            final IntPredicate isInEncodeSet, final boolean spaceAsPlus) {
        // early out
        if (input.length() == 0) {
            return "";
        }
        // 1
        // 2
        final CharBuffer inputBuffer = CharBuffer.wrap(input);
        // 3
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        // 4
        int potentialError = 0;
        // 5
        while (potentialError >= 0) {
            // 5.1
            // 5.2
            potentialError = InfraHelper.encodeOrFail(encoder, inputBuffer, encodingResult -> {
                // 5.3
                for (int i = 0; i < encodingResult.limit(); i++) {
                    final byte aByte = encodingResult.get(i);
                    // 5.3.1
                    if (spaceAsPlus && aByte == CodepointHelper.CP_SPACE) {
                        output.write(CodepointHelper.CP_PLUS);
                    }
                    else {
                        // 5.3.2
                        final int isomorph = InfraHelper.getIsomorphInt(aByte);
                        // 5.3.3
                        // 5.3.4
                        if (!isInEncodeSet.test(isomorph)) {
                            output.write(isomorph);
                        }
                        // 5.3.5
                        else {
                            percentEncode(aByte, output);
                        }
                    }
                }
            });
            // 5.4
            if (potentialError >= 0) {
                output.write('%');
                output.write('2');
                output.write('6');
                output.write('%');
                output.write('2');
                output.write('3');
                final String asciiError = Integer.toString(potentialError, 10);
                for (int i = 0; i < asciiError.length(); i++) {
                    output.write(asciiError.charAt(i));
                }
                output.write('%');
                output.write('3');
                output.write('B');
            }
        }
        return output.toString();
    }

    public static boolean remainingMatch(final String input, final int pointer,
            final int numberOfCodepointsToMatch, final BiPredicate<Integer, Integer> predicate) {
        if (numberOfCodepointsToMatch > 0 && pointer + numberOfCodepointsToMatch < input.length()) {
            for (int i = 0; i < numberOfCodepointsToMatch; i++) {
                final int codepoint = input.codePointAt(pointer + 1 + i);
                final boolean result = predicate.test(i, codepoint);
                if (!result) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public static int[] removeAsciiTabAndNewline(final int[] codepoints) {
        final int numberOfAsciiTabOrNewline = numberOfAsciiTabOrNewline(codepoints);
        final int[] result = new int[codepoints.length - numberOfAsciiTabOrNewline];
        for (int i = 0, j = 0; i < codepoints.length; i++) {
            if (!InfraHelper.isAsciiTabOrNewLine(codepoints[i])) {
                result[j++] = codepoints[i];
            }
        }
        return result;
    }

    public static int[] removeLeadingOrTrailingC0ControlOrSpace(final int[] codepoints) {
        int nbrOfLeading = 0;
        for (int i = 0; i < codepoints.length; i++) {
            if (InfraHelper.isC0ControlOrSpace(codepoints[i])) {
                nbrOfLeading++;
            }
            else {
                break;
            }
        }
        int nbrOfTrailing = 0;
        for (int i = codepoints.length - 1; i >= nbrOfLeading; i--) {
            if (InfraHelper.isC0ControlOrSpace(codepoints[i])) {
                nbrOfTrailing++;
            }
            else {
                break;
            }
        }
        if (nbrOfLeading > 0 || nbrOfTrailing > 0) {
            final int newLength = codepoints.length - nbrOfLeading - nbrOfTrailing;
            final int[] result = new int[newLength];
            System.arraycopy(codepoints, nbrOfLeading, result, 0, newLength);
            return result;
        }
        return codepoints;
    }

    /**
     * A string starts with a Windows drive letter if all of the following are true:
     * its length is greater than or equal to 2, its first two code points are a
     * Windows drive letter, and its length is 2 or its third code point is U+002F (/),
     * U+005C (\), U+003F (?), or U+0023 (#).
     *
     * @param input the codepoints to check
     * @return true if the input starts with a Windows drive letter, false otherwise
     */
    public static boolean startsWithWindowsDriveLetter(final Codepoints input) {
        Objects.requireNonNull(input);
        final int remaining = input.remaining();
        final int cp1 = input.codepointAt(input.pointer());
        final int cp2 = input.codepointAt(input.pointer() + 1);
        final int cp3 = input.codepointAt(input.pointer() + 2);
        return remaining >= 2 && UrlHelper.isWindowsDriveLetter(cp1, cp2)
                && (remaining == 2
                        || (remaining > 2 && (cp3 == CodepointHelper.CP_SLASH || cp3 == CodepointHelper.CP_BACKSLASH
                                || cp3 == CodepointHelper.CP_QUESTION_MARK || cp3 == CodepointHelper.CP_HASH)));
    }

    public static String utf8PercentEncode(final CharSequence input, final IntPredicate isInPercentEncodeSet) {
        return utf8PercentEncode(StandardCharsets.UTF_8.newEncoder(), input, isInPercentEncodeSet);
    }

    public static String utf8PercentEncode(final CharsetEncoder utf8Encoder, final CharSequence input,
            final IntPredicate isInPercentEncodeSet) {
        final StringBuilder result = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            final int codePoint = Character.codePointAt(input, i);
            result.append(utf8PercentEncode(utf8Encoder, codePoint, isInPercentEncodeSet));
        }
        return result.toString();
    }

    /**
     * To UTF-8 percent-encode a scalar value scalarValue using a percentEncodeSet,
     * return the result of running percent-encode after encoding with UTF-8,
     * scalarValue as a string, and percentEncodeSet.
     *
     * @param utf8Encoder          the UTF-8 charset encoder to use
     * @param codepoint            the Unicode code point to encode
     * @param isInPercentEncodeSet predicate that returns true if a code point must be percent-encoded
     * @return the percent-encoded string representation of the code point
     */
    public static String utf8PercentEncode(final CharsetEncoder utf8Encoder, final int codepoint,
            final IntPredicate isInPercentEncodeSet) {
        return percentEncodeAfterEncoding(utf8Encoder, codepoint, isInPercentEncodeSet, false);
    }
}
