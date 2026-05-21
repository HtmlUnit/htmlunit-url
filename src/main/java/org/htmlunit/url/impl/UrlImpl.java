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

import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.htmlunit.url.Url;
import org.htmlunit.url.UrlSearchParams;
import org.htmlunit.url.ValidationError;
import org.htmlunit.url.impl.UrlParser.State;

/**
 * Implementation of the {@link org.htmlunit.url.Url} interface following the WHATWG URL Living Standard.
 *
 * @author Stephane Bastian
 * @author Ronald Brill
 */
public class UrlImpl implements Url {
    // A URL’s scheme is an ASCII string that identifies the type of URL and can be
    // used to dispatch a
    // URL for further processing after parsing. It is initially the empty string.
    String scheme_ = "";

    // A URL’s username is an ASCII string identifying a username. It is initially
    // the empty string.
    String username_ = "";

    // A URL’s password is an ASCII string identifying a password. It is initially
    // the empty string.
    String password_ = "";

    // A URL’s host is null or a host. It is initially null.
    Host host_;

    // A URL’s port is either null or a 16-bit unsigned integer that identifies a
    // networking port. It
    // is initially null.
    Integer port_;

    // A URL’s path is either a URL path segment or a list of zero or more URL path
    // segments, usually
    // identifying a location. It is initially « ».
    List<String> path_ = new ArrayList<>();

    // A URL’s query is either null or an ASCII string. It is initially null.
    String query_;

    // A URL’s fragment is either null or an ASCII string that can be used for
    // further processing on
    // the resource the URL’s other components identify. It is initially null.
    String fragment_;

    private boolean hasAnOpaquePath_;
    private UrlSearchParamsImpl searchParams_;
    // lets cache the utf8Encoder
    private CharsetEncoder utf8Encoder_;
    private List<ValidationError> validationErrors_;

    UrlImpl() {
    }

    public static Url create(final String input, final String baseUrl) {
        if (input == null && baseUrl == null) {
            return new UrlImpl();
        }
        Objects.requireNonNull(input);
        final UrlParser parser = new UrlParser();
        UrlImpl parsedBaseUrl = null;
        if (baseUrl != null) {
            parsedBaseUrl = parser.basicParse(baseUrl, null, null);
        }
        return parser.basicParse(input, parsedBaseUrl, null);
    }

    void appendFragment(final String fragment) {
        Objects.requireNonNull(fragment);
        if (fragment_ == null) {
            fragment_ = fragment;
        }
        else {
            fragment_ += fragment;
        }
    }

    public Url appendQuery(final String value) {
        Objects.requireNonNull(value);
        if (query_ == null) {
            query_ = value;
        }
        else {
            query_ = query_ + value;
        }
        return this;
    }

    /**
     * A URL cannot have a username/password/port if its host is null or the empty
     * string, or its scheme is "file".
     *
     * @return true or false
     */
    boolean canNotHaveUsernamePasswordHost() {
        return host_ == null || ("".equals(host_.toString())) || "file".contentEquals(scheme_);
    }

    boolean hasAnOpaquePath() {
        return hasAnOpaquePath_;
    }

    /**
     * The hash getter steps are:
     * <ul>
     * <li>1) If this’s URL’s fragment is either null or the empty string, then
     * return the empty string.</li>
     * <li>2) Return U+0023 (#), followed by this’s URL’s fragment.</li>
     * </ul>
     */
    @Override
    public String hash() {
        // 1
        if (fragment_ == null || fragment_.isEmpty()) {
            return "";
        }
        // 2
        return "#" + fragment_;
    }

    /**
     * The hash setter steps are:
     * <ul>
     * <li>1) If the given value is the empty string:
     * <ul>
     * <li>1.1) Set this’s URL’s fragment to null.</li>
     * <li>1.2) Potentially strip trailing spaces from an opaque path with this.</li>
     * <li>1.3) Return.</li>
     * </ul>
     * </li>
     * <li>2) Let input be the given value with a single leading U+0023 (#) removed,
     * if any.</li>
     * <li>3) Set this’s URL’s fragment to the empty string.</li>
     * <li>4) Basic URL parse input with this’s URL as url and fragment state as
     * state override.</li>
     * </ul>
     */
    @Override
    public Url hash(String value) {
        Objects.requireNonNull(value);
        // 1
        if (value.isEmpty()) {
            // 1.1
            fragment_ = null;
            // 1.2
            potentiallySkipTrailingSpaceFromAnOpaquePath();
            // 1.3
            return this;
        }
        // 2
        if (value.charAt(0) == '#') {
            value = value.substring(1);
        }
        // 3
        fragment_ = "";
        // 4
        try {
            new UrlParser().basicParse(value, null, null, this, State.FRAGMENT);
        }
        catch (final Exception ignored) {
        }
        return this;
    }

    /**
     * The host getter steps are:
     * <ul>
     * <li>Let url be this’s URL.</li>
     * <li>If url’s host is null, then return the empty string.</li>
     * <li>If url’s port is null, return url’s host, serialized.</li>
     * <li>Return url’s host, serialized, followed by U+003A (:) and url’s port,
     * serialized.erialized.</li>
     * </ul>
     */
    @Override
    public String host() {
        if (host_ == null) {
            return "";
        }
        final StringBuilder buffer = new StringBuilder();
        SerializerHelper.serializeHost(host_, buffer);
        if (port_ != null) {
            buffer.append(":").append(SerializerHelper.serializeInteger(port_));
        }
        return buffer.toString();
    }

    /**
     * The host setter steps are:
     * <ul>
     * <li>If this’s URL has an opaque path, then return.</li>
     * <li>Basic URL parse the given value with this’s URL as url and host state as
     * state override.</li>
     * </ul>
     */
    @Override
    public Url host(final String value) {
        Objects.requireNonNull(value);
        if (hasAnOpaquePath()) {
            return this;
        }
        try {
            new UrlParser().basicParse(value, null, null, this, State.HOST);
        }
        catch (final Exception ignored) {
        }
        return this;
    }

    /**
     * The hostname getter steps are:
     * <ul>
     * <li>If this’s URL’s host is null, then return the empty string.</li>
     * <li>Return this’s URL’s host, serialized.</li>
     * </ul>
     */
    @Override
    public String hostname() {
        if (host_ == null) {
            return "";
        }
        final StringBuilder buffer = new StringBuilder();
        SerializerHelper.serializeHost(host_, buffer);
        return buffer.toString();
    }

    /**
     * The hostname setter steps are:
     * <ul>
     * <li>If this’s URL has an opaque path, then return.</li>
     * <li>Basic URL parse the given value with this’s URL as url and hostname state
     * as state override.</li>
     * </ul>
     */
    @Override
    public Url hostname(final String value) {
        Objects.requireNonNull(value);
        if (hasAnOpaquePath()) {
            return this;
        }
        try {
            new UrlParser().basicParse(value, null, null, this, State.HOSTNAME);
        }
        catch (final Exception ignored) {
        }
        return this;
    }

    /**
     * The href getter steps and the toJSON() method steps are to return the
     * serialization of this’s URL.
     */
    @Override
    public String href() {
        final StringBuilder result = new StringBuilder();
        SerializerHelper.serialize(this, false, result);
        return result.toString();
    }

    /**
     * The href setter steps are:
     * <ul>
     * <li>Let parsedURL be the result of running the basic URL parser on the given
     * value.</li>
     * <li>If parsedURL is failure, then throw a TypeError.</li>
     * <li>Set this’s URL to parsedURL.</li>
     * <li>Empty this’s query object’s list.</li>
     * <li>Let query be this’s URL’s query.</li>
     * <li>If query is non-null, then set this’s query object’s list to the result
     * of parsing query.</li>
     * </ul>
     *
     * @param value the href to set
     * @return this for a fluent interface
     */
    public Url href(final String value) {
        final UrlImpl parsedUrl = (UrlImpl) Url.create(value);
        fragment_ = parsedUrl.fragment_;
        hasAnOpaquePath_ = parsedUrl.hasAnOpaquePath_;
        host_ = parsedUrl.host_;
        password_ = parsedUrl.password_;
        path_ = parsedUrl.path_;
        port_ = parsedUrl.port_;
        query_ = parsedUrl.query_;
        scheme_ = parsedUrl.scheme_;
        searchParams_ = parsedUrl.searchParams_;
        username_ = parsedUrl.username_;
        return this;
    }

    boolean includeCredentials() {
        return !username_.isEmpty() || !password_.isEmpty();
    }

    boolean isSpecial() {
        return UrlHelper.isSpecialScheme(scheme_);
    }

    @Override
    public String origin() {
        final StringBuilder buffer = new StringBuilder();
        SerializerHelper.serializeOrigin(this, buffer);
        return buffer.toString();
    }

    /**
     * The password getter steps are to return this’s URL’s password.
     */
    @Override
    public String password() {
        return password_;
    }

    /**
     * The password setter steps are:
     * <ul>
     * <li>If this’s URL cannot have a username/password/port, then return.</li>
     * <li>Set the password given this’s URL and the given value.</li>
     * </ul>
     */
    @Override
    public Url password(final String value) {
        Objects.requireNonNull(value);
        if (canNotHaveUsernamePasswordHost()) {
            return this;
        }
        password_ = UrlHelper.utf8PercentEncode(utf8Encoder(), value, CodepointHelper::isInUserInfoPercentEncodeSet);
        return this;
    }

    void path(final UrlImpl url) {
        path_.clear();
        path_.addAll(url.path_);
        hasAnOpaquePath_ = url.hasAnOpaquePath_;
    }

    /**
     * The pathname getter steps are to return the result of URL path serializing
     * this’s URL.
     */
    @Override
    public String pathname() {
        final StringBuilder buffer = new StringBuilder();
        SerializerHelper.serializePath(this, buffer);
        return buffer.toString();
    }

    /**
     * The pathname setter steps are:
     * <ul>
     * <li>If this’s URL has an opaque path, then return.</li>
     * <li>Empty this’s URL’s path.</li>
     * <li>Basic URL parse the given value with this’s URL as url and path start
     * state as state override.</li>
     * </ul>
     */
    @Override
    public Url pathname(final String value) {
        Objects.requireNonNull(value);
        // 1
        if (hasAnOpaquePath_) {
            return this;
        }
        // 2
        path_.clear();
        // 3
        try {
            new UrlParser().basicParse(value, null, null, this, State.PATH_START);
        }
        catch (final Exception ignored) {
        }
        return this;
    }

    /**
     * The port getter steps are:
     * <ul>
     * <li>1) If this’s URL’s port is null, then return the empty string.</li>
     * <li>2) Return this’s URL’s port, serialized.</li>
     * </ul>
     */
    @Override
    public String port() {
        if (port_ == null) {
            return "";
        }
        return SerializerHelper.serializeInteger(port_);
    }

    /**
     * The port setter steps are:
     * <ul>
     * <li>1) If this’s URL cannot have a username/password/port, then return.</li>
     * <li>2) If the given value is the empty string, then set this’s URL’s port to
     * null.</li>
     * <li>3) Otherwise, basic URL parse the given value with this’s URL as url and
     * port state as state override.</li>
     * </ul>
     */
    @Override
    public Url port(final String value) {
        Objects.requireNonNull(value);
        // 1
        if (canNotHaveUsernamePasswordHost()) {
            return this;
        }
        // 2
        if (value.isEmpty()) {
            port_ = null;
        }
        // 3
        else {
            try {
                new UrlParser().basicParse(value, null, null, this, State.PORT);
            }
            catch (final Exception ignored) {
            }
        }
        return this;
    }

    /**
     * To potentially strip trailing spaces from an opaque path given a URL object
     * url:
     * <ul>
     * <li>1) If url’s URL does not have an opaque path, then return.</li>
     * <li>2) If url’s URL’s fragment is non-null, then return.</li>
     * <li>3) If url’s URL’s query is non-null, then return.</li>
     * <li>4) Remove all trailing U+0020 SPACE code points from url’s URL’s
     * path.</li>
     * </ul>
     */
    private void potentiallySkipTrailingSpaceFromAnOpaquePath() {
        // 1
        if (!hasAnOpaquePath_) {
            return;
        }
        // 2
        if (fragment_ != null) {
            return;
        }
        // 3
        if (query_ != null) {
            return;
        }
        // 4
        final ListIterator<String> segmentIterator = path_.listIterator();
        while (segmentIterator.hasNext()) {
            final String segment = segmentIterator.next();
            if (!segment.isEmpty()) {
                int lastValidIndex = segment.length();
                while (lastValidIndex > 1 && segment.charAt(lastValidIndex - 1) == 0x20) {
                    lastValidIndex--;
                }
                if (lastValidIndex < segment.length()) {
                    final String trimmedSegment = segment.substring(0, lastValidIndex);
                    segmentIterator.set(trimmedSegment);
                }
            }
        }
    }

    /**
     * The protocol getter steps are to return this’s URL’s scheme, followed by
     * U+003A (:).
     */
    @Override
    public String protocol() {
        return scheme_ + ":";
    }

    /**
     * The protocol setter steps are to basic URL parse the given value, followed by
     * U+003A (:), with this’s URL as url and scheme start state as state override.
     */
    @Override
    public Url protocol(final String value) {
        Objects.requireNonNull(value);
        try {
            new UrlParser().basicParse(value + ":", null, null, this, State.SCHEME_START);
        }
        catch (final Exception e) {
        }
        return this;
    }

    /**
     * The search getter steps are:
     * <ul>
     * <li>If this’s URL’s query is either null or the empty string, then return the
     * empty string.</li>
     * <li>Return U+003F (?), followed by this’s URL’s query.</li>
     * </ul>
     */
    @Override
    public String search() {
        // 1
        if (query_ == null || query_.isEmpty()) {
            return "";
        }
        // 2
        return "?" + query_;
    }

    /**
     * The search setter steps are:
     * <ul>
     * <li>1) Let url be this’s URL.</li>
     * <li>2) If the given value is the empty string:
     * <ul>
     * <li>2.1) Set url’s query to null.</li>
     * <li>2.2) Empty this’s query object’s list.</li>
     * <li>2.3) Potentially strip trailing spaces from an opaque path with
     * this.</li>
     * <li>2.4) Return.</li>
     * </ul>
     * </li>
     * <li>3) Let input be the given value with a single leading U+003F (?) removed,
     * if any.</li>
     * <li>4) Set url’s query to the empty string.</li>
     * <li>5) Basic URL parse input with url as url and query state as state
     * override.</li>
     * <li>6) Set this’s query object’s list to the result of parsing input.</li>
     * </ul>
     * <br>
     * <br>
     * The search setter has the potential to remove trailing U+0020 SPACE code
     * points from this’s URL’s path. It does this so that running the URL parser on
     * the output of running the URL serializer on this’s URL does not yield a URL
     * that is not equal.
     */
    @Override
    public Url search(String value) {
        Objects.requireNonNull(value);
        // 2
        if (value.isEmpty()) {
            query_ = null;
            searchParams_ = null;
            potentiallySkipTrailingSpaceFromAnOpaquePath();
            return this;
        }
        // 3
        if (value.charAt(0) == '?') {
            value = value.substring(1);
        }
        // 4
        query_ = "";
        // 5
        try {
            new UrlParser().basicParse(value, null, null, this, State.QUERY);
        }
        catch (final Exception ignored) {
        }
        return this;
    }

    @Override
    public UrlSearchParams searchParams() {
        if (searchParams_ == null) {
            searchParams_ = new UrlSearchParamsImpl();
            if (query_ != null) {
                searchParams_.init(query_);
            }
        }
        return searchParams_;
    }

    void setOpaqueState() {
        path_.clear();
        hasAnOpaquePath_ = true;
    }

    /**
     * To shorten a url’s path: 1) Let path be url’s path. 2) If path is empty, then
     * return. 3) If url’s scheme is "file", path’s size is 1, and path[0] is a
     * normalized Windows drive letter, then return. 4) Remove path’s last item.
     */
    void shortenPath() {
        // 2
        if (path_.isEmpty()) {
            return;
        }
        if ("file".equals(scheme_) && path_.size() == 1 && UrlHelper.isNormalizedWindowsDriveLetter(path_.get(0))) {
            return;
        }
        path_.remove(path_.size() - 1);
    }

    @Override
    public String toJSON() {
        return href();
    }

    @Override
    public String toString() {
        return href();
    }

    /**
     * The username getter steps are to return this’s URL’s username.
     */
    @Override
    public String username() {
        return username_;
    }

    /**
     * The username setter steps are:
     * <ul>
     * <li>1) If this’s URL cannot have a username/password/port, then return.</li>
     * <li>2) Set the username given this’s URL and the given value.
     * </ul>
     */
    @Override
    public Url username(final String value) {
        Objects.requireNonNull(value);
        if (canNotHaveUsernamePasswordHost()) {
            return this;
        }
        username_ = UrlHelper.utf8PercentEncode(utf8Encoder(), value, CodepointHelper::isInUserInfoPercentEncodeSet);
        return this;
    }

    void usernamePasswordHostPort(final UrlImpl url) {
        username_ = url.username_;
        password_ = url.password_;
        host_ = url.host_;
        port_ = url.port_;
    }

    void usernamePasswordHostPortPath(final UrlImpl url) {
        usernamePasswordHostPort(url);
        path(url);
    }

    @Override
    public List<ValidationError> validationErrors() {
        return validationErrors_ != null ? validationErrors_ : Collections.emptyList();
    }

    void validationError(final ValidationError error) {
        Objects.requireNonNull(error);
        if (validationErrors_ == null) {
            validationErrors_ = new ArrayList<>();
        }
        validationErrors_.add(error);
    }

    private CharsetEncoder utf8Encoder() {
        if (utf8Encoder_ == null) {
            utf8Encoder_ = StandardCharsets.UTF_8.newEncoder();
        }
        return utf8Encoder_;
    }

    class UrlSearchParamsImpl implements UrlSearchParams {
        private List<UrlSearchParam> parameters_;

        UrlSearchParamsImpl() {
            parameters_ = new ArrayList<>();
        }

        void init(final String init) {
            Objects.requireNonNull(init);
            parameters_.clear();
            final List<List<String>> sequence = UrlHelper.parseFormUrlEncoded(init);
            for (final List<String> innerSequence : sequence) {
                // 1
                if (innerSequence.size() != 2) {
                    throw new RuntimeException("The list must contain 2 items");
                }
                // 2
                append(innerSequence.get(0), innerSequence.get(1));
            }
        }

        @Override
        public UrlSearchParams append(final String name, final String value) {
            Objects.requireNonNull(name);
            parameters_.add(new UrlSearchParam(name, value));
            updateSteps();
            return this;
        }

        @Override
        public Collection<String> delete(final String name) {
            Objects.requireNonNull(name);
            final Collection<String> result = new ArrayList<>();
            final Iterator<UrlSearchParam> it = parameters_.iterator();
            while (it.hasNext()) {
                final UrlSearchParam param = it.next();
                if (Objects.equals(name, param.name())) {
                    it.remove();
                    result.add(param.value());
                }
            }
            updateSteps();
            return result;
        }

        @Override
        public boolean delete(final String name, final String value) {
            Objects.requireNonNull(name);
            boolean result = false;
            final Iterator<UrlSearchParam> it = parameters_.iterator();
            while (it.hasNext()) {
                final UrlSearchParam param = it.next();
                if (Objects.equals(name, param.name()) && Objects.equals(value, param.value())) {
                    it.remove();
                    result = true;
                }
            }
            if (result) {
                updateSteps();
            }
            return result;
        }

        @Override
        public UrlSearchParams entries(final BiConsumer<String, String> consumer) {
            Objects.requireNonNull(consumer);
            parameters_.forEach(param -> {
                consumer.accept(param.name(), param.value());
            });
            return this;
        }

        @Override
        public String get(final String name) {
            Objects.requireNonNull(name);
            for (final UrlSearchParam param : parameters_) {
                if (Objects.equals(name, param.name())) {
                    return param.value();
                }
            }
            return null;
        }

        @Override
        public Collection<String> getAll(final String name) {
            Objects.requireNonNull(name);
            final Collection<String> result = new ArrayList<>();
            for (final UrlSearchParam param : parameters_) {
                if (Objects.equals(name, param.name())) {
                    result.add(param.value());
                }
            }
            return result;
        }

        @Override
        public boolean has(final String name) {
            Objects.requireNonNull(name);
            for (final UrlSearchParam param : parameters_) {
                if (Objects.equals(name, param.name())) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean has(final String name, final String value) {
            Objects.requireNonNull(name);
            for (final UrlSearchParam param : parameters_) {
                if (Objects.equals(name, param.name()) && Objects.equals(value, param.value())) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public UrlSearchParams set(final String name, final String value) {
            Objects.requireNonNull(name);
            Objects.requireNonNull(value);
            boolean hasFoundName = false;
            final Iterator<UrlSearchParam> paramIterator = parameters_.iterator();
            while (paramIterator.hasNext()) {
                final UrlSearchParam param = paramIterator.next();
                if (Objects.equals(name, param.name())) {
                    if (hasFoundName) {
                        paramIterator.remove();
                    }
                    else {
                        param.value(value);
                        hasFoundName = true;
                    }
                }
            }
            if (hasFoundName) {
                updateSteps();
            }
            else {
                append(name, value);
            }
            return this;
        }

        @Override
        public UrlSearchParams sort() {
            parameters_.sort(Comparator.comparing(UrlSearchParam::name));
            updateSteps();
            return this;
        }

        @Override
        public int size() {
            return parameters_.size();
        }

        public String toString() {
            final StringBuilder buffer = new StringBuilder();
            SerializerHelper.serializeFormUrlEncoded(parameters_, utf8Encoder(), buffer);
            return buffer.toString();
        }

        /**
         * To update a URLSearchParams object query:
         * <ul>
         * <li>1) If query’s URL object is null, then return.</li>
         * <li>2) Let serializedQuery be the serialization of query’s list.</li>
         * <li>3) If serializedQuery is the empty string, then set serializedQuery to
         * null.</li>
         * <li>4) Set query’s URL object’s URL’s query to serializedQuery.</li>
         * <li>5) If serializedQuery is null, then potentially strip trailing spaces
         * from an opaque path with query’s URL object.</li>
         * <li></li>
         * <li></li>
         * </ul>
         */
        void updateSteps() {
            // 1 is not appropriate since we always have a URL object
            // 2
            final StringBuilder buffer = new StringBuilder();
            SerializerHelper.serializeFormUrlEncoded(parameters_, utf8Encoder(), buffer);
            // 3
            final String serializedQuery = buffer.length() > 0 ? buffer.toString() : null;
            // 4
            query_ = serializedQuery;
            // 5
            if (serializedQuery == null) {
                UrlImpl.this.potentiallySkipTrailingSpaceFromAnOpaquePath();
            }
        }
    }
}
