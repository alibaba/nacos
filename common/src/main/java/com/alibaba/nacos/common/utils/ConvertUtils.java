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

import java.util.Set;

/**
 * Value Convert Utils.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class ConvertUtils {

    private ConvertUtils() {
    }
    
    private static final String NULL_STR = "null";
    
    public static final Set<String> TRUE_SET = CollectionUtils.set("y", "yes", "on", "true", "t");
    
    public static final Set<String> FALSE_SET = CollectionUtils.set("n", "no", "off", "false", "f");
    
    /**
     * Convert String value to int value if parameter value is legal. And it automatically defaults to 0 if parameter
     * value is null or blank str.
     *
     * @param val String value which need to be converted to int value.
     * @return Converted int value and its default value is 0.
     */
    public static int toInt(String val) {
        return toInt(val, 0);
    }
    
    /**
     * Convert String value to int value if parameter value is legal. And return default value if parameter value is
     * null or blank str.
     *
     * @param val          value
     * @param defaultValue default value
     * @return int value if input value is legal, otherwise default value
     */
    public static int toInt(String val, int defaultValue) {
        if (StringUtils.equalsIgnoreCase(val, NULL_STR)) {
            return defaultValue;
        }
        if (StringUtils.isBlank(val)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }
    
    /**
     * Convert Object value to long value if parameter value is legal.
     * And it automatically defaults to 0 if parameter value is null or other object.
     *
     * @param val object value
     * @return Converted long value and its default value is 0.
     */
    public static long toLong(Object val) {
        if (val instanceof Long) {
            return (Long) val;
        }
        return toLong(val.toString());
    }
    
    /**
     * Convert String value to long value if parameter value is legal. And it automatically defaults to 0 if parameter
     * value is null or blank str.
     *
     * @param val String value which need to be converted to int value.
     * @return Converted long value and its default value is 0.
     */
    public static long toLong(String val) {
        return toLong(val, 0L);
    }
    
    /**
     * Convert String value to long value if parameter value is legal. And return default value if parameter value is
     * null or blank str.
     *
     * @param val          value
     * @param defaultValue default value
     * @return long value if input value is legal, otherwise default value
     */
    public static long toLong(String val, long defaultValue) {
        if (StringUtils.isBlank(val)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }
    
    /**
     * Convert String value to boolean value if parameter value is legal. And return default value if parameter value is
     * null or blank str.
     *
     * @param val          value
     * @param defaultValue default value
     * @return boolean value if input value is legal, otherwise default value
     */
    public static boolean toBoolean(String val, boolean defaultValue) {
        if (StringUtils.isBlank(val)) {
            return defaultValue;
        }
        return Boolean.parseBoolean(val);
    }
    
    //   The following utility functions are extracted from <link>org.apache.commons.lang3</link>
    //   start
    
    /**
     * <p>Converts a String to a boolean (optimised for performance).</p>
     *
     * <p>{@code 'true'}, {@code 'on'}, {@code 'y'}, {@code 't'} or {@code 'yes'}
     * (case insensitive) will return {@code true}. Otherwise, {@code false} is returned.</p>
     *
     * <p>This method performs 4 times faster (JDK1.4) than
     * {@code Boolean.valueOf(String)}. However, this method accepts 'on' and 'yes', 't', 'y' as true values.
     *
     * <pre>
     *   BooleanUtils.toBoolean(null)    = false
     *   BooleanUtils.toBoolean("true")  = true
     *   BooleanUtils.toBoolean("TRUE")  = true
     *   BooleanUtils.toBoolean("tRUe")  = true
     *   BooleanUtils.toBoolean("on")    = true
     *   BooleanUtils.toBoolean("yes")   = true
     *   BooleanUtils.toBoolean("false") = false
     *   BooleanUtils.toBoolean("x gti") = false
     *   BooleanUtils.toBooleanObject("y") = true
     *   BooleanUtils.toBooleanObject("n") = false
     *   BooleanUtils.toBooleanObject("t") = true
     *   BooleanUtils.toBooleanObject("f") = false
     * </pre>
     *
     * @param str the String to check
     * @return the boolean value of the string, {@code false} if no match or the String is null
     */
    public static boolean toBoolean(final String str) {
        return Boolean.TRUE.equals(toBooleanObject(str));
    }
    
    /**
     * <p>Converts a String to a Boolean.</p>
     *
     * <p>{@code 'true'}, {@code 'on'}, {@code 'y'}, {@code 't'} or {@code 'yes'}
     * (case insensitive) will return {@code true}. {@code 'false'}, {@code 'off'}, {@code 'n'}, {@code 'f'} or {@code
     * 'no'} (case insensitive) will return {@code false}. Otherwise, {@code null} is returned.</p>
     *
     * <p>NOTE: This returns null and will throw a NullPointerException if autoboxed to a boolean. </p>
     *
     * <pre>
     *   // N.B. case is not significant
     *   BooleanUtils.toBooleanObject(null)    = null
     *   BooleanUtils.toBooleanObject("true")  = Boolean.TRUE
     *   BooleanUtils.toBooleanObject("T")     = Boolean.TRUE // i.e. T[RUE]
     *   BooleanUtils.toBooleanObject("false") = Boolean.FALSE
     *   BooleanUtils.toBooleanObject("f")     = Boolean.FALSE // i.e. f[alse]
     *   BooleanUtils.toBooleanObject("No")    = Boolean.FALSE
     *   BooleanUtils.toBooleanObject("n")     = Boolean.FALSE // i.e. n[o]
     *   BooleanUtils.toBooleanObject("on")    = Boolean.TRUE
     *   BooleanUtils.toBooleanObject("ON")    = Boolean.TRUE
     *   BooleanUtils.toBooleanObject("off")   = Boolean.FALSE
     *   BooleanUtils.toBooleanObject("oFf")   = Boolean.FALSE
     *   BooleanUtils.toBooleanObject("yes")   = Boolean.TRUE
     *   BooleanUtils.toBooleanObject("Y")     = Boolean.TRUE // i.e. Y[ES]
     *   BooleanUtils.toBooleanObject("blue")  = null
     *   BooleanUtils.toBooleanObject("true ") = null // trailing space (too long)
     *   BooleanUtils.toBooleanObject("ono")   = null // does not match on or no
     * </pre>
     *
     * @param str the String to check; upper and lower case are treated as the same
     * @return the Boolean value of the string, {@code null} if no match or {@code null} input
     */
    @SuppressWarnings("all")
    public static Boolean toBooleanObject(String str) {
        String formatStr = (str == null ? StringUtils.EMPTY : str).toLowerCase();
    
        if (TRUE_SET.contains(formatStr)) {
            return true;
        } else if (FALSE_SET.contains(formatStr)) {
            return false;
        } else {
            return null;
        }
    }
    
    //   end
    
}
