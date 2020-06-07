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

package com.alibaba.nacos.common.utils;

import org.apache.commons.lang3.StringUtils;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class ConvertUtils {

    private static final String NULL_STR = "null";

    public static int toInt(String val, int defaultValue) {
        if (StringUtils.equalsIgnoreCase(val, NULL_STR)) {
            return defaultValue;
        }
        if (StringUtils.isBlank(val)) {
            return defaultValue;
        }
        return Integer.parseInt(val);
    }

    public static long toLong(String val, long defaultValue) {
        if (StringUtils.isBlank(val)) {
            return defaultValue;
        }
        return Long.parseLong(val);
    }

    public static boolean toBool(String val, boolean defaultValue) {
        if (StringUtils.isBlank(val)) {
            return defaultValue;
        }
        return Boolean.parseBoolean(val);
    }

}
