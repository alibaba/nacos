/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.pathencoder.impl;

import com.alibaba.nacos.common.pathencoder.PathEncoder;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Encode path if illegal char reach in Windows.
 *
 * @author daydreamer-ia
 */
public class WindowsEncoder implements PathEncoder {

    /**
     * 不应该含有 / : ? " < > | \.
     */
    private static final String PATTERN_EXP = "[^/:*?\"<>|\\\\]+";

    private static final Map<String, String> REG_MAPPING = new HashMap<>();

    private static final Map<String, String> CHAR_MAPPING = new HashMap<>();

    private static final Pattern PATTERN = Pattern.compile(PATTERN_EXP);

    static {
        // reg
        REG_MAPPING.put("\\\\", "%A1%");
        REG_MAPPING.put("/", "%A2%");
        REG_MAPPING.put(":", "%A3%");
        REG_MAPPING.put("\\*", "%A4%");
        REG_MAPPING.put("\\?", "%A5%");
        REG_MAPPING.put("\"", "%A6%");
        REG_MAPPING.put("<", "%A7%");
        REG_MAPPING.put(">", "%A8%");
        REG_MAPPING.put("\\|", "%A9%");

        // char
        CHAR_MAPPING.put("%A1%", "\\\\");
        CHAR_MAPPING.put("%A2%", "/");
        CHAR_MAPPING.put("%A3%", ":");
        CHAR_MAPPING.put("%A4%", "*");
        CHAR_MAPPING.put("%A5%", "?");
        CHAR_MAPPING.put("%A6%", "\"");
        CHAR_MAPPING.put("%A7%", "<");
        CHAR_MAPPING.put("%A8%", ">");
        CHAR_MAPPING.put("%A9%", "|");
    }

    @Override
    public String encode(String str, String charset) {
        for (Map.Entry<String, String> entry : REG_MAPPING.entrySet()) {
            str = str.replaceAll(entry.getKey(), entry.getValue());
        }
        return str;
    }

    @Override
    public String decode(String str, String charset) {
        for (Map.Entry<String, String> entry : CHAR_MAPPING.entrySet()) {
            str = str.replaceAll(entry.getKey(), entry.getValue());
        }
        return str;
    }

    @Override
    public String name() {
        return "window";
    }

    @Override
    public boolean needEncode(String key) {
        if (key == null) {
            return false;
        }
        return !PATTERN.matcher(key).matches();
    }
}
