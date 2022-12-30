/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.common.utils.url;

import com.alibaba.nacos.common.utils.StringUtils;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Url component builder.
 *
 * @author Weizhan▪Yun
 * @date 2022/12/29 19:17
 */
public class HttpUrlComponentsBuilder {
    
    private static final String PATH_SEPARATOR = "/";
    
    private static final String DEFAULT_HTTP_SCHEME = "http";
    
    private static final String HTTPS_SCHEME = "https";
    
    private static final String HTTP_PORT = "80";
    
    private static final String HTTPS_PORT = "443";
    
    private static final String HTTP_PATTERN = "(?i)(http|https):";
    
    private static final String USERINFO_PATTERN = "([^@\\[/?#]*)";
    
    private static final String HOST_IPV4_PATTERN = "[^\\[/?#:]*";
    
    private static final String HOST_IPV6_PATTERN = "\\[[\\p{XDigit}:.]*[%\\p{Alnum}]*]";
    
    private static final String HOST_PATTERN = "(" + HOST_IPV6_PATTERN + "|" + HOST_IPV4_PATTERN + ")";
    
    private static final String PORT_PATTERN = "(\\{[^}]+\\}?|[^/?#]*)";
    
    private static final String PATH_PATTERN = "([^?#]*)";
    
    private static final String QUERY_PATTERN = "([^#]*)";
    
    private static final String LAST_PATTERN = "(.*)";
    
    private static final Pattern HTTP_URL_PATTERN = Pattern.compile(
            "^" + HTTP_PATTERN + "(//(" + USERINFO_PATTERN + "@)?" + HOST_PATTERN + "(:" + PORT_PATTERN + ")?" + ")?"
                    + PATH_PATTERN + "(\\?" + QUERY_PATTERN + ")?" + "(#" + LAST_PATTERN + ")?");
    
    @Nullable
    private String scheme;
    
    private String host;
    
    @Nullable
    private String port;
    
    private StringJoiner path = createRootPath();
    
    protected HttpUrlComponentsBuilder() {
    }
    
    /**
     * create a new HttpUrlComponentsBuilder by http url.
     *
     * @param httpUrl the source URL
     * @return the URL components of the URL
     */
    public static HttpUrlComponentsBuilder fromHttpUrl(String httpUrl) {
        Objects.requireNonNull(httpUrl, "HTTP URL must not be null");
        Matcher matcher = HTTP_URL_PATTERN.matcher(httpUrl);
        if (matcher.matches()) {
            HttpUrlComponentsBuilder builder = new HttpUrlComponentsBuilder();
            String scheme = matcher.group(1);
            builder.scheme(scheme != null ? scheme.toLowerCase() : null);
            String host = matcher.group(5);
            if (StringUtils.hasLength(scheme) && !StringUtils.hasLength(host)) {
                throw new IllegalArgumentException("[" + httpUrl + "] is not a valid HTTP URL");
            }
            builder.host(host);
            String port = matcher.group(7);
            if (StringUtils.hasLength(port)) {
                builder.port(port);
            }
            builder.path(matcher.group(8));
            return builder;
        } else {
            throw new IllegalArgumentException("[" + httpUrl + "] is not a valid HTTP URL");
        }
    }
    
    /**
     * Set the http url host, and may also be {@code null} to clear the host of this builder.
     *
     * @param host the URL host
     */
    public HttpUrlComponentsBuilder host(String host) {
        validate(() -> StringUtils.isNotBlank(host), "host must not be null");
        this.host = host;
        return this;
    }
    
    /**
     * Set the http url port. Passing {@code -1} will clear the port of this builder.
     *
     * @param port the URI port
     */
    public HttpUrlComponentsBuilder port(int port) {
        validate(() -> port >= -1, "Port must be >= -1");
        if (port > -1) {
            this.port = String.valueOf(port);
        } else {
            this.port = null;
        }
        return this;
    }
    
    /**
     * Set the http url port . Use this method only when the port needs to be parameterized with a URI variable.
     * Otherwise use {@link #port(int)}. Passing {@code null} will clear the port of this builder.
     *
     * @param port the http url port
     */
    public HttpUrlComponentsBuilder port(@Nullable String port) {
        this.port = port;
        return this;
    }
    
    private static void validate(Supplier<Boolean> predicator, String errMessage) {
        if (predicator.get()) {
            return;
        }
        throw new IllegalArgumentException(errMessage);
    }
    
    public HttpUrlComponentsBuilder scheme(@Nullable String scheme) {
        this.scheme = scheme;
        return this;
    }
    
    /**
     * Set the http url path. Passing {@code null} will clear the path of this builder.
     *
     * @param path the URI path
     */
    public HttpUrlComponentsBuilder path(String path) {
        if (StringUtils.isBlank(path) || PATH_SEPARATOR.equals(path)) {
            this.path = createRootPath();
            return this;
        }
        
        this.path = createRootPath();
        addPath(path);
        
        return this;
    }
    
    private static StringJoiner createRootPath() {
        
        return new StringJoiner("/", "/", "");
    }
    
    /**
     * Build an immutable http url.
     *
     * @return HttpUrlComponents
     */
    public HttpUrlComponents build() {
        return new HttpUrlComponents(hasScheme() ? scheme : DEFAULT_HTTP_SCHEME, host,
                hasPort() ? port : getDefaultPort(), path.toString());
    }
    
    private String getDefaultPort() {
        if (hasScheme() && HTTPS_SCHEME.equals(this.scheme)) {
            return HTTPS_PORT;
        }
        
        return HTTP_PORT;
    }
    
    public boolean hasScheme() {
        return StringUtils.isNotBlank(this.scheme);
    }
    
    public boolean hasHost() {
        return StringUtils.isNotBlank(this.host);
    }
    
    public boolean hasPort() {
        return StringUtils.isNotBlank(this.port);
    }
    
    /**
     * append a path to existing path.
     *
     * @param path http url path
     * @return this HttpUrlComponentsBuilder
     */
    public HttpUrlComponentsBuilder addPath(String path) {
        Objects.requireNonNull(path, "path must not be null");
        if (StringUtils.isBlank(path) || PATH_SEPARATOR.equals(path)) {
            return this;
        }
        
        for (String s : path.split(PATH_SEPARATOR)) {
            if (StringUtils.isNotBlank(s) && !PATH_SEPARATOR.equals(s)) {
                this.path.add(s);
            }
        }
        
        return this;
    }
    
}
