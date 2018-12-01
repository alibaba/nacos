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
package com.alibaba.nacos.client.logger.util;

/**
 * Error msg format
 *
 * @author Nacos
 */
public class MessageUtil {

    public static String formatMessage(String format, Object[] argArray) {
        FormattingTuple ft = MessageFormatter.arrayFormat(format, argArray);
        return ft.getMessage();
    }

    public static String getMessage(String message) {
        return getMessage(null, message);
    }

    public static String getMessage(String context, String message) {
        return getMessage(context, null, message);
    }

    public static String getMessage(String context, String errorCode, String message) {
        if (context == null) {
            context = "";
        }

        if (errorCode == null) {
            errorCode = "";
        }
        return "[" + context + "] [] [" + errorCode + "] " + message;
    }
}
