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
 * Content utils
 *
 * @author Nacos
 */
public class ContentUtils {

    public static void verifyIncrementPubContent(String content) {

        if (content == null || content.length() == 0) {
            throw new IllegalArgumentException("发布/删除内容不能为空");
        }
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\r' || c == '\n') {
                throw new IllegalArgumentException("发布/删除内容不能包含回车和换行");
            }
            if (c == Constants.WORD_SEPARATOR.charAt(0)) {
                throw new IllegalArgumentException("发布/删除内容不能包含(char)2");
            }
        }
    }

    public static String getContentIdentity(String content) {
        int index = content.indexOf(WORD_SEPARATOR);
        if (index == -1) {
            throw new IllegalArgumentException("内容没有包含分隔符");
        }
        return content.substring(0, index);
    }

    public static String getContent(String content) {
        int index = content.indexOf(WORD_SEPARATOR);
        if (index == -1) {
            throw new IllegalArgumentException("内容没有包含分隔符");
        }
        return content.substring(index + 1);
    }

    public static String truncateContent(String content) {
        if (content == null) {
            return "";
        } else if (content.length() <= LIMIT_CONTENT_SIZE) {
            return content;
        } else {
            return content.substring(0, 100) + "...";
        }
    }

    private final static int LIMIT_CONTENT_SIZE = 100;
}
