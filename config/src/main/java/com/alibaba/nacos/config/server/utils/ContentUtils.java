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

import static com.alibaba.nacos.config.server.constant.Constants.WORD_SEPARATOR;

/**
 * Content utils.
 *
 * @author Nacos
 */
public class ContentUtils {
    
    /**
     * verify the pub config content.
     *
     * @param content content
     */
    public static void verifyIncrementPubContent(String content) {
        if (content == null || content.length() == 0) {
            throw new IllegalArgumentException("The content for publishing or deleting cannot be null!");
        }
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\r' || c == '\n') {
                throw new IllegalArgumentException("The content for publishing or deleting cannot contain enter and next line symbol!");
            }
            if (c == Constants.WORD_SEPARATOR.charAt(0)) {
                throw new IllegalArgumentException("The content for publishing or deleting cannot contain (char)2!");
            }
        }
    }
    
    public static String getContentIdentity(String content) {
        int index = content.indexOf(WORD_SEPARATOR);
        if (index == -1) {
            throw new IllegalArgumentException("The content does not contain separator!");
        }
        return content.substring(0, index);
    }
    
    public static String getContent(String content) {
        int index = content.indexOf(WORD_SEPARATOR);
        if (index == -1) {
            throw new IllegalArgumentException("The content does not contain separator!");
        }
        return content.substring(index + 1);
    }
    
    /**
     * Truncate the content.
     *
     * @param content content
     * @return content after truncate.
     */
    public static String truncateContent(String content) {
        if (content == null) {
            return "";
        } else if (content.length() <= LIMIT_CONTENT_SIZE) {
            return content;
        } else {
            return content.substring(0, 100) + "...";
        }
    }
    
    private static final int LIMIT_CONTENT_SIZE = 100;
}
