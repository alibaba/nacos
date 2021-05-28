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

import org.apache.commons.lang3.CharUtils;

/**
 * Generic classes for wildcard characters, decisions, and standard canonical transformations that can be supported by
 * ConfigCenter.
 *
 * @author tianhu E-mail:
 */
public class RegexParser {
    
    private static final char QUESTION_MARK = '?';
    
    /**
     * Replace input string non-regular special characters with standard regular expression strings; Replace '*' with
     * '.* '? 'is replaced by '{n}', n is the number of consecutive ?; Other special characters that are not alphabetic
     * or numeric are preceded by '\'.
     *
     * @param regex  The expression to be formatted
     * @return format content.
     */
    public static String regexFormat(String regex) {
        if (regex == null) {
            throw new NullPointerException("regex string can't be null");
        }
        StringBuilder result = new StringBuilder();
        result.append("^");
        for (int i = 0; i < regex.length(); i++) {
            char ch = regex.charAt(i);
            if (CharUtils.isAsciiAlphanumeric(ch) || CharUtils.isAsciiNumeric(ch)) {
                result.append(ch);
            } else if (ch == '*') {
                result.append(".*");
            } else if (ch == QUESTION_MARK) {
                int j = 0;
                for (; j < regex.length() - i && ch == QUESTION_MARK; j++) {
                    ch = regex.charAt(i + j);
                }
                if (j == regex.length() - i) {
                    result.append(".{" + j + "}");
                    break;
                } else {
                    j -= 1;
                    result.append(".{" + (j) + "}");
                    i += j - 1;
                }
            } else {
                result.append("\\" + ch);
            }
        }
        result.append("$");
        return result.toString();
    }
    
    public static boolean containsWildcard(String regex) {
        return (regex.contains("?") || regex.contains("*"));
    }
    
}
