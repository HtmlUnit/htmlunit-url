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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

/**
 * Helper class providing utilities for the Infra Living Standard primitives used during URL processing.
 *
 * @author Stephane Bastian
 * @author Ronald Brill
 */
public final class InfraHelper {

    private InfraHelper() {
        // utility class
    }

    /**
     * To collect a sequence of code points meeting a condition from a string input,
     * given a position variable tracking the position of the calling algorithm within input:
     * <ul>
     *   <li>1) Let result be the empty string.</li>
     *   <li>2) While position doesn't point past the end of input and the code point
     *   at position within input meets the condition:
     *   <ul>
     *     <li>2.1) Append that code point to the end of result.</li>
     *     <li>2.2) Advance position by 1.</li>
     *   </ul>
     *   </li>
     *   <li>3) Return result.</li>
     * </ul>
     *
     * @param input     the input string to collect from
     * @param position  the position to start collecting codepoints
     * @param condition a predicate indicating whether a given codepoint should be collected
     * @return a string representation of all collected codepoints
     */
    public static String collectCodePoints(final String input, int position, final IntPredicate condition) {
        // 1
        final StringBuilder result = new StringBuilder();
        if (input != null) {
            // 2
            while (position < input.length() && condition.test(input.charAt(position))) {
                // 2.1
                result.append(input.charAt(position));
                // 2.2
                position++;
            }
        }
        // 3
        return result.toString();
    }

    static int doDecode(final CharsetDecoder decoder, final ByteBuffer inputBuffer,
            final ErrorMode errorMode, final Consumer<CharBuffer> resultHandler) {
        Objects.requireNonNull(decoder);
        Objects.requireNonNull(inputBuffer);
        Objects.requireNonNull(errorMode);
        Objects.requireNonNull(resultHandler);
        if (inputBuffer.remaining() == 0) {
            // nothing to decode
            return -1;
        }
        // make sure outputBuffer is 'big enough' to do the whole thing in one shot
        final CharBuffer outputBuffer = CharBuffer.allocate(inputBuffer.capacity() * 5);
        CoderResult coderResult = null;
        while (inputBuffer.remaining() > 0) {
            outputBuffer.clear();
            coderResult = decoder.decode(inputBuffer, outputBuffer, true);
            outputBuffer.flip();
            // notify the caller
            resultHandler.accept(outputBuffer);
            // handle errors
            if (coderResult.isError()) {
                outputBuffer.clear();
                final byte byteInError = inputBuffer.get(inputBuffer.position());
                // skip the codepoint in error so that the next encode won't loop over the same error
                if (inputBuffer.position() < inputBuffer.limit() + 1) {
                    inputBuffer.position(inputBuffer.position() + 1);
                }
                if (errorMode == ErrorMode.REPLACEMENT) {
                    outputBuffer.put('\uFFFD');
                    outputBuffer.flip();
                    resultHandler.accept(outputBuffer);
                }
                else if (errorMode == ErrorMode.HTML) {
                    outputBuffer.put('&');
                    outputBuffer.put('#');
                    Integer.toString((int) byteInError, 10).chars().forEach(achar -> outputBuffer.put((char) achar));
                    // result's code point's value in base ten
                    outputBuffer.put(';');
                    outputBuffer.flip();
                    resultHandler.accept(outputBuffer);
                }
                else if (errorMode == ErrorMode.FATAL) {
                    return byteInError;
                }
            }
        }
        return -1;
    }

    /**
     * doEncode is equivalent to processQueue/ProcessItem whose algorithms are described below.
     * It does its job and calls the resultHandler whenever output is produced.
     * <br>
     * ProcessQueue: To encode an I/O queue of scalar values ioQueue given an encoding encoding
     * and an optional I/O queue of bytes output (default &laquo; &raquo;), run these steps:
     * <ul>
     *   <li>1) Let encoder be the result of getting an encoder from encoding.</li>
     *   <li>2) Process a queue with encoder, ioQueue, output, and "html".</li>
     *   <li>3) Return output.</li>
     * </ul>
     * ProcessItem: To process an item given an item item, encoding's encoder or decoder instance
     * encoderDecoder, I/O queue input, I/O queue output, and error mode mode:
     * <ul>
     *   <li>1) Assert: if encoderDecoder is an encoder instance, mode is not "replacement".</li>
     *   <li>2) Assert: if encoderDecoder is a decoder instance, mode is not "html".</li>
     *   <li>3) Assert: if encoderDecoder is an encoder instance, item is not a surrogate.</li>
     *   <li>4) Let result be the result of running encoderDecoder's handler on input and item.</li>
     *   <li>5) If result is finished:
     *   <ul>
     *     <li>5.1) Push end-of-queue to output.</li>
     *     <li>5.2) Return result.</li>
     *   </ul>
     *   </li>
     *   <li>6) Otherwise, if result is one or more items:
     *   <ul>
     *     <li>6.1) Assert: if encoderDecoder is a decoder instance, result does not contain
     *     any surrogates.</li>
     *     <li>6.2) Push result to output.</li>
     *   </ul>
     *   </li>
     *   <li>7) Otherwise, if result is an error, switch on mode and run the associated steps:
     *   <ul>
     *     <li>"replacement": Push U+FFFD (&#xFFFD;) to output.</li>
     *     <li>"html": Push 0x26 (&amp;), 0x23 (#), followed by the shortest sequence of
     *     0x30 (0) to 0x39 (9), inclusive, representing result's code point's value in base ten,
     *     followed by 0x3B (;) to output.</li>
     *     <li>"fatal": Return result.</li>
     *   </ul>
     *   </li>
     *   <li>8) Return continue.</li>
     * </ul>
     *
     * @param encoder       the charset encoder to use
     * @param inputBuffer   the input characters to encode
     * @param errorMode     the error handling mode
     * @param resultHandler consumer that receives each encoded output buffer
     * @return -1 on success, or the code point value that caused a fatal error
     */
    static int doEncode(final CharsetEncoder encoder, final CharBuffer inputBuffer, final ErrorMode errorMode,
            final Consumer<ByteBuffer> resultHandler) {
        Objects.requireNonNull(encoder);
        Objects.requireNonNull(inputBuffer);
        Objects.requireNonNull(errorMode);
        Objects.requireNonNull(resultHandler);
        if (inputBuffer.remaining() == 0) {
            // nothing to encode
            return -1;
        }
        // make sure outputBuffer is 'big enough'
        final int outputBufferLength = (int) (inputBuffer.remaining() * encoder.maxBytesPerChar());
        final ByteBuffer outputBuffer = ByteBuffer.allocate(outputBufferLength);
        encoder.reset();
        CoderResult coderResult = CoderResult.OVERFLOW;
        while (coderResult != CoderResult.UNDERFLOW) {
            outputBuffer.clear();
            coderResult = inputBuffer.hasRemaining() ? encoder.encode(inputBuffer, outputBuffer, true)
                    : CoderResult.UNDERFLOW;
            // make sure we call flush because some encoder (iso-200-jp for instance) write
            // stuff at the end
            if (coderResult.isUnderflow()) {
                coderResult = encoder.flush(outputBuffer);
            }
            outputBuffer.flip();
            // notify the caller
            resultHandler.accept(outputBuffer);
            // handle errors
            if (coderResult.isError()) {
                // clear the output buffer to report errors if applicable
                outputBuffer.clear();
                final char charInError = inputBuffer.get(inputBuffer.position());
                // skip the codepoint in error so that the next encode won't loop over the same error
                if (inputBuffer.position() < inputBuffer.limit() + 1) {
                    inputBuffer.position(inputBuffer.position() + 1);
                }
                if (errorMode == ErrorMode.REPLACEMENT) {
                    outputBuffer.putChar('\uFFFD');
                    outputBuffer.flip();
                    resultHandler.accept(outputBuffer);
                }
                else if (errorMode == ErrorMode.HTML) {
                    outputBuffer.putChar('&');
                    outputBuffer.putChar('#');
                    Integer.toString((int) charInError, 10).chars().forEach(outputBuffer::putInt);
                    // result's code point's value in base ten
                    outputBuffer.putChar(';');
                    outputBuffer.flip();
                    resultHandler.accept(outputBuffer);
                }
                else if (errorMode == ErrorMode.FATAL) {
                    return charInError;
                }
            }
        }
        return -1;
    }

    /**
     * To encode or fail an I/O queue of scalar values ioQueue given an encoder instance encoder
     * and an I/O queue of bytes output, run these steps:
     * <ul>
     *   <li>1) Let potentialError be the result of processing a queue with encoder,
     *   ioQueue, output, and "fatal".</li>
     *   <li>2) Push end-of-queue to output.</li>
     *   <li>3) If potentialError is an error, then return error's code point's value.</li>
     *   <li>4) Return null.</li>
     * </ul>
     *
     * @param encoder       the encoder to use
     * @param inputBuffer   the input to encode
     * @param resultHandler the result handler
     * @return -1 on success, or the code point value that caused a fatal encoding error
     */
    public static int encodeOrFail(final CharsetEncoder encoder, final CharBuffer inputBuffer,
            final Consumer<ByteBuffer> resultHandler) {
        // doEncode may call the callback several times, unless an error occurred in
        // which case it stops the encoding process
        return doEncode(encoder, inputBuffer, ErrorMode.FATAL, resultHandler);
    }

    /**
     * Returns the four bytes of the given 32-bit integer value, most significant byte first.
     *
     * @param value the integer value to convert
     * @return a four-element byte array containing the big-endian representation of value
     */
    static byte[] getBytes(final int value) {
        return new byte[] {(byte) (value >> 24 & 0x00FF), (byte) (value >> 16 & 0x00FF),
                           (byte) (value >> 8 & 0x00FF), (byte) (value & 0x00FF) };
    }

    /**
     * The isomorph value of a byte is a code point whose value is the byte's value.
     *
     * @param aByte the byte value
     * @return the isomorph int value (unsigned byte value in the range 0-255)
     */
    public static int getIsomorphInt(final byte aByte) {
        return Byte.toUnsignedInt(aByte);
    }

    /**
     * To get an output encoding from an encoding encoding, run these steps:
     * If encoding is replacement, UTF-16BE, or UTF-16LE, return UTF-8.
     * Return encoding.
     *
     * @param encoding the encoding to use
     * @return the output encoding, guaranteed never to be UTF-16BE or UTF-16LE
     */
    public static Charset getOutputEncoding(final Charset encoding) {
        if (encoding == StandardCharsets.UTF_16BE || encoding == StandardCharsets.UTF_16LE) {
            return StandardCharsets.UTF_8;
        }
        return encoding;
    }

    /**
     * An ASCII alpha is an ASCII upper alpha or ASCII lower alpha.
     * Code points U+0041 (A) to U+005A (Z) or U+0061 (a) to U+007A (z), inclusive.
     *
     * @param codepoint the codepoint to test
     * @return true if the codepoint is an ASCII alpha, false otherwise
     */
    public static boolean isAsciiAlpha(final int codepoint) {
        return isAsciiUpperAlpha(codepoint) || isAsciiLowerAlpha(codepoint);
    }

    /**
     * An ASCII alphanumeric is an ASCII digit or ASCII alpha.
     *
     * @param codepoint the codepoint to test
     * @return true if the codepoint is an ASCII alphanumeric, false otherwise
     */
    public static boolean isAsciiAlphanumeric(final int codepoint) {
        return isAsciiDigit(codepoint) || isAsciiAlpha(codepoint);
    }

    /**
     * An ASCII digit is a code point in the range U+0030 (0) to U+0039 (9), inclusive.
     *
     * @param codepoint the codepoint to test
     * @return true if the codepoint is an ASCII digit, false otherwise
     */
    public static boolean isAsciiDigit(final int codepoint) {
        return codepoint >= 0x0030 && codepoint <= 0x0039;
    }

    /**
     * An ASCII hex digit is an ASCII upper hex digit or ASCII lower hex digit.
     * That is, a code point in the range U+0030 (0) to U+0039 (9),
     * U+0041 (A) to U+0046 (F), or U+0061 (a) to U+0066 (f), inclusive.
     *
     * @param codepoint the codepoint to test
     * @return true if the codepoint is an ASCII hex digit, false otherwise
     */
    public static boolean isAsciiHexDigit(final int codepoint) {
        return isAsciiUpperHexDigit(codepoint) || isAsciiLowerHexDigit(codepoint);
    }

    /**
     * An ASCII lower alpha is a code point in the range U+0061 (a) to U+007A (z), inclusive.
     *
     * @param codepoint the codepoint to test
     * @return true if the codepoint is an ASCII lower alpha, false otherwise
     */
    public static boolean isAsciiLowerAlpha(final int codepoint) {
        return codepoint >= 0x0061 && codepoint <= 0x007A;
    }

    /**
     * An ASCII lower hex digit is an ASCII digit or a code point in the range
     * U+0061 (a) to U+0066 (f), inclusive.
     *
     * @param codepoint the codepoint to test
     * @return true if the codepoint is an ASCII lower hex digit, false otherwise
     */
    public static boolean isAsciiLowerHexDigit(final int codepoint) {
        return isAsciiDigit(codepoint) || codepoint >= 0x0061 && codepoint <= 0x0066;
    }

    /**
     * Returns true if the codepoint is an ASCII tab (U+0009), line feed (U+000A),
     * or carriage return (U+000D).
     *
     * @param codepoint the codepoint to test
     * @return true if the codepoint is an ASCII tab or newline, false otherwise
     */
    public static boolean isAsciiTabOrNewLine(final int codepoint) {
        return codepoint == CodepointHelper.CP_TAB
                || codepoint == CodepointHelper.CP_LF
                || codepoint == CodepointHelper.CP_CR;
    }

    /**
     * An ASCII upper alpha is a code point in the range U+0041 (A) to U+005A (Z), inclusive.
     *
     * @param codepoint the codepoint to test
     * @return true if the codepoint is an ASCII upper alpha, false otherwise
     */
    public static boolean isAsciiUpperAlpha(final int codepoint) {
        return codepoint >= 0x0041 && codepoint <= 0x005A;
    }

    /**
     * An ASCII upper hex digit is an ASCII digit or a code point in the range
     * U+0041 (A) to U+0046 (F), inclusive.
     *
     * @param codepoint the codepoint to test
     * @return true if the codepoint is an ASCII upper hex digit, false otherwise
     */
    public static boolean isAsciiUpperHexDigit(final int codepoint) {
        return isAsciiDigit(codepoint) || codepoint >= 0x0041 && codepoint <= 0x0046;
    }

    /**
     * A C0 control is a code point in the range U+0000 NULL to U+001F INFORMATION
     * SEPARATOR ONE, inclusive.
     *
     * @param codepoint the codepoint to test
     * @return true if the codepoint is a C0 control, false otherwise
     */
    static boolean isC0Control(final int codepoint) {
        return codepoint >= 0x0000 && codepoint <= 0x001F;
    }

    /**
     * A C0 control or space is a C0 control or U+0020 SPACE.
     *
     * @param codepoint the codepoint to test
     * @return true if the codepoint is a C0 control or space, false otherwise
     */
    public static boolean isC0ControlOrSpace(final int codepoint) {
        return isC0Control(codepoint) || codepoint == CodepointHelper.CP_SPACE;
    }

    /**
     * A control is a C0 control or a code point in the range U+007F DELETE to
     * U+009F APPLICATION PROGRAM COMMAND, inclusive.
     *
     * @param codepoint the codepoint to test
     * @return true if the codepoint is a control, false otherwise
     */
    public static boolean isControl(final int codepoint) {
        return isC0Control(codepoint) || (codepoint >= CodepointHelper.CP_DELETE && codepoint <= 0x009F);
    }

    /**
     * A scalar value is a code point that is not a surrogate.
     * Surrogates are code points in the range U+D800 to U+DFFF, inclusive.
     *
     * @param codepoint the codepoint to test
     * @return true if the codepoint is a scalar value, false otherwise
     */
    public static boolean isScalarValue(final int codepoint) {
        return !isSurrogate(codepoint);
    }

    /**
     * A surrogate is a code point that is in the range U+D800 to U+DFFF, inclusive.
     *
     * @param codePoint the codepoint to test
     * @return true if the codepoint is a surrogate, false otherwise
     */
    public static boolean isSurrogate(final int codePoint) {
        return codePoint >= 0xD800 && codePoint <= 0xDFFF;
    }

    /**
     * To isomorphic decode a byte sequence input, return a string whose code point length
     * is equal to input's length and whose code points have the same values as the values
     * of input's bytes, in the same order.
     *
     * @param bytes the bytes to decode
     * @return the decoded string
     */
    public static String isomorphicDecode(final byte[] bytes) {
        final int[] codepoints = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            codepoints[i] = (int) bytes[i];
        }
        return new String(codepoints, 0, codepoints.length);
    }

    /**
     * To strictly split a string input on a particular delimiter code point delimiter:
     * <ul>
     *   <li>1) Let position be a position variable for input, initially pointing at
     *   the start of input.</li>
     *   <li>2) Let tokens be a list of strings, initially empty.</li>
     *   <li>3) Let token be the result of collecting a sequence of code points that
     *   are not equal to delimiter from input, given position.</li>
     *   <li>4) Append token to tokens.</li>
     *   <li>5) While position is not past the end of input:
     *   <ul>
     *     <li>5.1) Assert: the code point at position within input is delimiter.</li>
     *     <li>5.2) Advance position by 1.</li>
     *     <li>5.3) Let token be the result of collecting a sequence of code points that
     *     are not equal to delimiter from input, given position.</li>
     *     <li>5.4) Append token to tokens.</li>
     *   </ul>
     *   </li>
     *   <li>6) Return tokens.</li>
     * </ul>
     *
     * @param value     the value to split
     * @param delimiter the delimiter character to split on
     * @return the ordered list of tokens between delimiters
     */
    public static List<String> strictSplit(final String value, final char delimiter) {
        // 1
        int position = 0;
        // 2
        final List<String> tokens = new ArrayList<>();
        // 3
        String token = collectCodePoints(value, position, codepoint -> delimiter != codepoint);
        // 4
        tokens.add(token);
        position += token.length();
        // 5
        while (position < value.length()) {
            // 5.2
            position++;
            // 5.3
            token = collectCodePoints(value, position, codepoint -> delimiter != codepoint);
            // 5.4
            tokens.add(token);
            position += token.length();
        }
        // 6
        return tokens;
    }

    /**
     * Converts the specified byte value (unsigned) to its hexadecimal representation
     * as two ASCII upper hex digits (0-9, A-F).
     *
     * @param value the byte value to convert
     * @return a two-element char array containing the upper-case hex representation
     */
    static char[] toHexChars(final byte value) {
        return new char[] {Character.toUpperCase(Character.forDigit((value >> 4) & 0xf, 16)),
                Character.toUpperCase(Character.forDigit(value & 0xf, 16))};
    }

    /**
     * A scalar value string is a string whose code points are all scalar values.
     * A scalar value string is useful for any kind of I/O or other kind of operation
     * where UTF-8 encode comes into play.
     * To convert a string into a scalar value string, replace any surrogates with U+FFFD (&#xFFFD;).
     *
     * @param codepoints the codepoints array whose surrogates need to be replaced
     * @return the input array with all surrogates replaced by U+FFFD (modifies the array in place)
     */
    public static int[] toScalarCodepoints(final int[] codepoints) {
        Objects.requireNonNull(codepoints);
        for (int i = 0; i < codepoints.length; i++) {
            final int codepoint = codepoints[i];
            if (!isScalarValue(codepoint)) {
                // replace the surrogate
                codepoints[i] = '\uFFFD';
            }
        }
        return codepoints;
    }

    /**
     * Error mode used to control how encoding and decoding errors are handled.
     */
    public enum ErrorMode {
        /** Replace the erroneous input with U+FFFD REPLACEMENT CHARACTER. */
        REPLACEMENT,
        /** Terminate encoding or decoding and return the error. */
        FATAL,
        /** Encode the erroneous code point as an HTML numeric character reference. */
        HTML
    }
}
