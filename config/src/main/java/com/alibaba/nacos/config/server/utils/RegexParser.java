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
 * 用于ConfigCenter可支持的通配字符通配判定以及标准正则转换的通用类
 *
 * @author tianhu E-mail:
 * @version 创建时间：2008-12-30 下午07:09:52 类说明
 */
public class RegexParser {

    private final static char QUESTION_MARK = '?';

    /**
     * 替换输入字符串中非正则特殊字符为标准正则表达式字符串; <br> '*'替换为 ‘.*’ '?'替换为'{n}'，n为连续?的个数; <br> 其他非字母或数字的特殊字符前均添加'\'.
     *
     * @param regex
     * @return
     */
    static public String regexFormat(String regex) {
        if (regex == null) {
            throw new NullPointerException("regex string can't be null");
        }
        StringBuffer result = new StringBuffer();
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

    static public boolean containsWildcard(String regex) {
        return (regex.contains("?") || regex.contains("*"));
    }

}
