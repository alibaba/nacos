/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.utils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.net.URLDecoder;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * URL codec utils.
 * @author TFdream
 */
public class UrlCodecUtils {
    private UrlCodecUtils() {}
    
    private static final String DEFAULT_CHARSET = StandardCharsets.UTF_8.name();
    
    /**
     * Translates a string into {@code application/x-www-form-urlencoded}
     *      format using utf-8.
     * @param data {@code String} to be translated.
     * @return
     */
    public static String encode(String data) {
        try {
            return URLEncoder.encode(data, DEFAULT_CHARSET);
        } catch (UnsupportedEncodingException e) {
            //never happens
            throw new IllegalArgumentException(e);
        }
    }
    
    /**
     * Translates a string into {@code application/x-www-form-urlencoded}
     *      format using a specific encoding scheme.
     * @param data {@code String} to be translated
     * @param encoding the name of a supported encoding.
     * @return
     */
    public static String encode(String data, Charset encoding) {
        try {
            return URLEncoder.encode(data, encoding.name());
        } catch (UnsupportedEncodingException e) {
            //never happens
            throw new IllegalArgumentException(e);
        }
    }
    
    /**
     * Translates a string into {@code application/x-www-form-urlencoded}
     *      format using a specific encoding scheme.
     * @param data {@code String} to be translated
     * @param encoding the name of a supported encoding.
     * @return the translated {@code String}.
     * @throws UnsupportedEncodingException if character encoding is not supported
     */
    public static String encode(String data, String encoding) throws UnsupportedEncodingException {
        return URLEncoder.encode(data, encoding);
    }
    
    //==========
    
    /**
     * Decodes a {@code application/x-www-form-urlencoded} string using utf-8.
     * @param data the {@code String} to decode.
     * @return
     */
    public static String decode(String data) {
        try {
            return URLDecoder.decode(data, DEFAULT_CHARSET);
        } catch (UnsupportedEncodingException e) {
            //never to appear
            throw new IllegalArgumentException(e);
        }
    }
    
    /**
     * Decodes a {@code application/x-www-form-urlencoded} string using a specific
     *      encoding scheme.
     * @param data the {@code String} to decode
     * @param encoding the name of a supported encoding.
     * @return
     */
    public static String decode(String data, Charset encoding) {
        try {
            return URLDecoder.decode(data, encoding.name());
        } catch (UnsupportedEncodingException e) {
            //never to appear
            throw new IllegalArgumentException(e);
        }
    }
    
    /**
     * Decodes a {@code application/x-www-form-urlencoded} string using a specific
     *      encoding scheme.
     * @param data the {@code String} to decode
     * @param encoding the name of a supported encoding.
     * @return the newly decoded {@code String}
     * @throws UnsupportedEncodingException if character encoding is not supported
     */
    public static String decode(String data, String encoding) throws UnsupportedEncodingException {
        return URLDecoder.decode(data, encoding);
    }
}
