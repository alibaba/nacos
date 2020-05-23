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

import java.util.Arrays;
import java.util.List;

/**
 * @author lin-mt
 */
public class BooleanUtils {

    public static boolean and(boolean... array) {
        if (array == null) {
            throw new IllegalArgumentException("The Array must not be null");
        }
        if (array.length == 0) {
            throw new IllegalArgumentException("Array is empty");
        }
        for (final boolean element : array) {
            if (!element) {
                return false;
            }
        }
        return true;
    }

    private static final List<String> TRUE_LOWER_STR = Arrays.asList("true", "t", "on", "yes", "y");
    private static final List<String> FALSE_LOWER_STR = Arrays.asList("false", "f", "no", "n", "off");

    public static boolean toBoolean(final String str) {
        return toBooleanObject(str).equals(Boolean.TRUE);
    }

    public static Boolean toBooleanObject(final String str) {
        if (str == null) {
            return null;
        }
        String strTmp = str.toLowerCase();
        if (TRUE_LOWER_STR.contains(strTmp)) {
            return Boolean.TRUE;
        }
        if (FALSE_LOWER_STR.contains(strTmp)) {
            return Boolean.FALSE;
        }
        return null;
    }
}
