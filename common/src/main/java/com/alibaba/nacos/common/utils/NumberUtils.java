/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

/**
 * Number utils.
 * @author zzq
 */
public class NumberUtils {

    private NumberUtils() {
    }
    
    /**
     * Convert a <code>String</code> to an <code>int</code>, returning
     * <code>zero</code> if the conversion fails.
     *
     * @param str  the string to convert, may be null
     * @return the int represented by the string, or <code>zero</code> if
     *  conversion fails
     */
    public static int toInt(String str) {
        return toInt(str, 0);
    }
    
    /**
     * Convert a <code>String</code> to an <code>int</code>, returning a
     * default value if the conversion fails.
     *
     * @param str  the string to convert, may be null
     * @param defaultValue  the default value
     * @return the int represented by the string, or the default if conversion fails
     */
    public static int toInt(String str, int defaultValue) {
        if (str == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException nfe) {
            return defaultValue;
        }
    }
    
    /**
     * Convert a <code>String</code> to a <code>long</code>, returning a
     * default value if the conversion fails.
     *
     * @param str  the string to convert, may be null
     * @param defaultValue  the default value
     * @return the long represented by the string, or the default if conversion fails
     */
    public static long toLong(String str, long defaultValue) {
        if (str == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(str);
        } catch (NumberFormatException nfe) {
            return defaultValue;
        }
    }
    
    /**
     * Convert a <code>String</code> to a <code>double</code>, returning a
     * default value if the conversion fails.
     *
     * @param str the string to convert, may be <code>null</code>
     * @param defaultValue the default value
     * @return the double represented by the string, or defaultValue
     *  if conversion fails
     */
    public static double toDouble(String str, double defaultValue) {
        if (str == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException nfe) {
            return defaultValue;
        }
    }

    /**
     * Checks whether the <code>String</code> contains only
     * digit characters.
     *
     * @param str  the <code>String</code> to check
     * @return <code>true</code> if str contains only unicode numeric
     */
    public static boolean isDigits(String str) {
        if (StringUtils.isEmpty(str)) {
            return false;
        }
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Convert a <code>String</code> to a <code>float</code>, returning
     * <code>0.0f</code> if the conversion fails.
     *
     * @param str the string to convert, may be <code>null</code>
     * @return the float represented by the string, or <code>0.0f</code>
     *  if conversion fails
     */
    public static float toFloat(final String str) {
        return toFloat(str, 0.0f);
    }
    
    /**
     * Convert a <code>String</code> to a <code>float</code>, returning a
     * default value if the conversion fails.
     *
     * @param str the string to convert, may be null
     * @param defaultValue the default value
     * @return the float represented by the string, or defaultValue
     *  if conversion fails
     */
    public static float toFloat(final String str, final float defaultValue) {
        if (str == null) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(str);
        } catch (final NumberFormatException nfe) {
            return defaultValue;
        }
    }
    
}
