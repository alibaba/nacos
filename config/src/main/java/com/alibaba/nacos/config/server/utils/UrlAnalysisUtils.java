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

package com.alibaba.nacos.config.server.utils;

import com.alibaba.nacos.config.server.constant.Constants;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Url util.
 *
 * @author leiwen.zh
 */
public class UrlAnalysisUtils {
    
    private static final Pattern URL_PATTERN = Pattern.compile("^(\\w+://)?([\\w\\.]+:)(\\d*)?(\\??.*)");
    
    public static String getContentIdentity(String content) {
        
        if (!verifyIncrementPubContent(content)) {
            return null;
        }
        
        Matcher matcher = URL_PATTERN.matcher(content);
        StringBuilder buf = new StringBuilder();
        if (matcher.find()) {
            String scheme = matcher.group(1);
            String address = matcher.group(2);
            String port = matcher.group(3);
            if (scheme != null) {
                buf.append(scheme);
            }
            buf.append(address);
            if (port != null) {
                buf.append(port);
            }
        }
        return buf.toString();
    }
    
    private static boolean verifyIncrementPubContent(String content) {
        
        if (content == null || content.length() == 0) {
            return false;
        }
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\r' || c == '\n') {
                return false;
            }
            if (c == Constants.WORD_SEPARATOR.charAt(0)) {
                return false;
            }
        }
        return true;
    }
}
