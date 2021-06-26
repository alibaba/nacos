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

/**
 * boolean util.
 *
 * @author zzq
 */
public class BooleanUtils {
    
    private static final String TRUE = Boolean.TRUE.toString();
    
    /**
     * <p>Converts a String to a boolean (optimised for performance).</p>
     *
     * <p>{@code 'true'}, {@code 'on'}, {@code 'y'}, {@code 't'} or {@code 'yes'}
     * (case insensitive) will return {@code true}. Otherwise,
     * {@code false} is returned.</p>
     *
     * <p>This method performs 4 times faster (JDK1.4) than
     * {@code Boolean.valueOf(String)}. However, this method accepts
     * 'on' and 'yes', 't', 'y' as true values.
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
     * @param str  the String to check
     * @return the boolean value of the string, {@code false} if no match or the String is null
     */
    public static boolean toBoolean(final String str) {
        return Boolean.TRUE.equals(toBooleanObject(str));
    }
    
    /**
     * <p>Converts a String to a Boolean.</p>
     *
     * <p>{@code 'true'}, {@code 'on'}, {@code 'y'}, {@code 't'} or {@code 'yes'}
     * (case insensitive) will return {@code true}.
     * {@code 'false'}, {@code 'off'}, {@code 'n'}, {@code 'f'} or {@code 'no'}
     * (case insensitive) will return {@code false}.
     * Otherwise, {@code null} is returned.</p>
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
     * @param str  the String to check; upper and lower case are treated as the same
     * @return the Boolean value of the string, {@code null} if no match or {@code null} input
     */
    public static Boolean toBooleanObject(final String str) {
        // Previously used equalsIgnoreCase, which was fast for interned 'true'.
        // Non interned 'true' matched 15 times slower.
        //
        // Optimisation provides same performance as before for interned 'true'.
        // Similar performance for null, 'false', and other strings not length 2/3/4.
        // 'true'/'TRUE' match 4 times slower, 'tRUE'/'True' 7 times slower.
        if (str == TRUE) {
            return Boolean.TRUE;
        }
        if (str == null) {
            return null;
        }
        switch (str.length()) {
            case 1: {
                final char ch0 = str.charAt(0);
                if (case1Condition1(ch0)) {
                    return Boolean.TRUE;
                }
                if (case1Condition2(ch0)) {
                    return Boolean.FALSE;
                }
                break;
            }
            case 2: {
                final char ch0 = str.charAt(0);
                final char ch1 = str.charAt(1);
                if (case2Condition1(ch0, ch1)) {
                    return Boolean.TRUE;
                }
                if (case2Condition2(ch0, ch1)) {
                    return Boolean.FALSE;
                }
                break;
            }
            case 3: {
                final char ch0 = str.charAt(0);
                final char ch1 = str.charAt(1);
                final char ch2 = str.charAt(2);
                if (case3Condition1(ch0, ch1, ch2)) {
                    return Boolean.TRUE;
                }
                if (case3Condition2(ch0, ch1, ch2)) {
                    return Boolean.FALSE;
                }
                break;
            }
            case 4: {
                final char ch0 = str.charAt(0);
                final char ch1 = str.charAt(1);
                final char ch2 = str.charAt(2);
                final char ch3 = str.charAt(3);
                if (case4Condition1(ch0, ch1, ch2, ch3)) {
                    return Boolean.TRUE;
                }
                break;
            }
            case 5: {
                final char ch0 = str.charAt(0);
                final char ch1 = str.charAt(1);
                final char ch2 = str.charAt(2);
                final char ch3 = str.charAt(3);
                final char ch4 = str.charAt(4);
                if (case5Condition1(ch0, ch1, ch2, ch3, ch4)) {
                    return Boolean.FALSE;
                }
                break;
            }
            default:
                break;
        }
        
        return null;
    }
    
    /**
     * <p>Performs an and on a set of booleans.</p>
     *
     * <pre>
     *   BooleanUtils.and(true, true)         = true
     *   BooleanUtils.and(false, false)       = false
     *   BooleanUtils.and(true, false)        = false
     *   BooleanUtils.and(true, true, false)  = false
     *   BooleanUtils.and(true, true, true)   = true
     * </pre>
     *
     * @param array  an array of {@code boolean}s
     * @return {@code true} if the and is successful.
     * @throws IllegalArgumentException if {@code array} is {@code null}
     * @throws IllegalArgumentException if {@code array} is empty.
     * @since 3.0.1
     */
    public static boolean and(final boolean... array) {
        // Validates input
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
    
    /**
     * <p>Performs an and on an array of Booleans.</p>
     *
     * <pre>
     *   BooleanUtils.and(Boolean.TRUE, Boolean.TRUE)                 = Boolean.TRUE
     *   BooleanUtils.and(Boolean.FALSE, Boolean.FALSE)               = Boolean.FALSE
     *   BooleanUtils.and(Boolean.TRUE, Boolean.FALSE)                = Boolean.FALSE
     *   BooleanUtils.and(Boolean.TRUE, Boolean.TRUE, Boolean.TRUE)   = Boolean.TRUE
     *   BooleanUtils.and(Boolean.FALSE, Boolean.FALSE, Boolean.TRUE) = Boolean.FALSE
     *   BooleanUtils.and(Boolean.TRUE, Boolean.FALSE, Boolean.TRUE)  = Boolean.FALSE
     * </pre>
     *
     * @param array  an array of {@code Boolean}s
     * @return {@code true} if the and is successful.
     * @throws IllegalArgumentException if {@code array} is {@code null}
     * @throws IllegalArgumentException if {@code array} is empty.
     * @throws IllegalArgumentException if {@code array} contains a {@code null}
     * @since 3.0.1
     */
    public static Boolean and(final Boolean... array) {
        if (array == null) {
            throw new IllegalArgumentException("The Array must not be null");
        }
        if (array.length == 0) {
            throw new IllegalArgumentException("Array is empty");
        }
        try {
            final boolean[] primitive = ArrayUtils.toPrimitive(array);
            return and(primitive) ? Boolean.TRUE : Boolean.FALSE;
        } catch (final NullPointerException ex) {
            throw new IllegalArgumentException("The array must not contain any null elements");
        }
    }
    
    private static Boolean case1Condition1(char ch0) {
        return ch0 == 'y'
                || ch0 == 'Y'
                || ch0 == 't'
                || ch0 == 'T';
    }
    
    private static Boolean case1Condition2(char ch0) {
        return ch0 == 'n'
                || ch0 == 'N'
                || ch0 == 'f'
                || ch0 == 'F';
    }
    
    private static Boolean case2Condition1(char ch0, char ch1) {
        return (ch0 == 'o'
                || ch0 == 'O')
                && (ch1 == 'n'
                || ch1 == 'N');
    }
    
    private static Boolean case2Condition2(char ch0, char ch1) {
        return (ch0 == 'n'
                || ch0 == 'N')
                && (ch1 == 'o'
                || ch1 == 'O');
    }
    
    private static Boolean case3Condition1(char ch0, char ch1, char ch2) {
        return (ch0 == 'y'
                || ch0 == 'Y')
                && (ch1 == 'e'
                || ch1 == 'E')
                && (ch2 == 's'
                || ch2 == 'S');
    }
    
    private static Boolean case3Condition2(char ch0, char ch1, char ch2) {
        return (ch0 == 'o'
                || ch0 == 'O')
                && (ch1 == 'f'
                || ch1 == 'F')
                && (ch2 == 'f'
                || ch2 == 'F');
    }
    
    private static Boolean case4Condition1(char ch0, char ch1, char ch2, char ch3) {
        return (ch0 == 't'
                || ch0 == 'T')
                && (ch1 == 'r'
                || ch1 == 'R')
                && (ch2 == 'u'
                || ch2 == 'U')
                && (ch3 == 'e'
                || ch3 == 'E');
    }
    
    private static Boolean case5Condition1(char ch0, char ch1, char ch2, char ch3, char ch4) {
        return (ch0 == 'f'
                || ch0 == 'F')
                && (ch1 == 'a'
                || ch1 == 'A')
                && (ch2 == 'l'
                || ch2 == 'L')
                && (ch3 == 's'
                || ch3 == 'S')
                && (ch4 == 'e'
                || ch4 == 'E');
    }
}
