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
 * nacos Number util.
 *
 * @author wujian
 */
public class NumberUtil {
    
    /**
     * <p>Checks whether the String a valid Java number.</p>
     *
     * <p>Valid numbers include hexadecimal marked with the <code>0x</code> or
     * <code>0X</code> qualifier, octal numbers, scientific notation and
     * numbers marked with a type qualifier (e.g. 123L).</p>
     *
     * <p>Non-hexadecimal strings beginning with a leading zero are
     * treated as octal values. Thus the string <code>09</code> will return
     * <code>false</code>, since <code>9</code> is not a valid octal value.
     * However, numbers beginning with {@code 0.} are treated as decimal.</p>
     *
     * <p><code>null</code> and empty/blank {@code String} will return
     * <code>false</code>.</p>
     *
     * @param str the <code>String</code> to check
     * @return <code>true</code> if the string is a correctly formatted number
     */
    public static boolean isNumber(final String str) {
        return isCreatable(str);
    }
    
    /**
     * <p>Checks whether the String a valid Java number.</p>
     *
     * <p>Valid numbers include hexadecimal marked with the <code>0x</code> or
     * <code>0X</code> qualifier, octal numbers, scientific notation and
     * numbers marked with a type qualifier (e.g. 123L).</p>
     *
     * <p>Non-hexadecimal strings beginning with a leading zero are
     * treated as octal values. Thus the string <code>09</code> will return
     * <code>false</code>, since <code>9</code> is not a valid octal value.
     * However, numbers beginning with {@code 0.} are treated as decimal.</p>
     *
     * <p><code>null</code> and empty/blank {@code String} will return
     * <code>false</code>.</p>
     *
     * @param str the <code>String</code> to check
     * @return <code>true</code> if the string is a correctly formatted number
     */
    @SuppressWarnings({"PMD.UndefineMagicConstantRule", "PMD.AvoidComplexConditionRule"})
    public static boolean isCreatable(final String str) {
        if (StringUtils.isEmpty(str)) {
            return false;
        }
        final char[] chars = str.toCharArray();
        int sz = chars.length;
        boolean hasExp = false;
        boolean hasDecPoint = false;
        boolean allowSigns = false;
        boolean foundDigit = false;
        // deal with any possible sign up front
        final int start = chars[0] == '-' || chars[0] == '+' ? 1 : 0;
        // leading 0, skip if is a decimal number
        if (sz > start + 1 && chars[start] == '0' && !StringUtils.contains(str, '.')) {
            // leading 0x/0X
            if (chars[start + 1] == 'x' || chars[start + 1] == 'X') {
                int i = start + 2;
                if (i == sz) {
                    // str == "0x"
                    return false;
                }
                // checking hex (it can't be anything else)
                for (; i < chars.length; i++) {
                    if ((chars[i] < '0' || chars[i] > '9') && (chars[i] < 'a' || chars[i] > 'f') && (chars[i] < 'A'
                            || chars[i] > 'F')) {
                        return false;
                    }
                }
                return true;
            } else if (Character.isDigit(chars[start + 1])) {
                // leading 0, but not hex, must be octal
                int i = start + 1;
                for (; i < chars.length; i++) {
                    if (chars[i] < '0' || chars[i] > '7') {
                        return false;
                    }
                }
                return true;
            }
        }
        sz--; // don't want to loop to the last char, check it afterwords
        // for type qualifiers
        int i = start;
        // loop to the next to last char or to the last char if we need another digit to
        // make a valid number (e.g. chars[0..5] = "1234E")
        while (i < sz || i < sz + 1 && allowSigns && !foundDigit) {
            if (chars[i] >= '0' && chars[i] <= '9') {
                foundDigit = true;
                allowSigns = false;
                
            } else if (chars[i] == '.') {
                if (hasDecPoint || hasExp) {
                    // two decimal points or dec in exponent
                    return false;
                }
                hasDecPoint = true;
            } else if (chars[i] == 'e' || chars[i] == 'E') {
                // we've already taken care of hex.
                if (hasExp) {
                    // two E's
                    return false;
                }
                if (!foundDigit) {
                    return false;
                }
                hasExp = true;
                allowSigns = true;
            } else if (chars[i] == '+' || chars[i] == '-') {
                if (!allowSigns) {
                    return false;
                }
                allowSigns = false;
                // we need a digit after the E
                foundDigit = false;
            } else {
                return false;
            }
            i++;
        }
        if (i < chars.length) {
            if (chars[i] >= '0' && chars[i] <= '9') {
                // no type qualifier, OK
                return true;
            }
            if (chars[i] == 'e' || chars[i] == 'E') {
                // can't have an E at the last byte
                return false;
            }
            if (chars[i] == '.') {
                if (hasDecPoint || hasExp) {
                    // two decimal points or dec in exponent
                    return false;
                }
                // single trailing decimal point after non-exponent is ok
                return foundDigit;
            }
            if (!allowSigns && (chars[i] == 'd' || chars[i] == 'D' || chars[i] == 'f' || chars[i] == 'F')) {
                return foundDigit;
            }
            if (chars[i] == 'l' || chars[i] == 'L') {
                // not allowing L with an exponent or decimal point
                return foundDigit && !hasExp && !hasDecPoint;
            }
            // last character is illegal
            return false;
        }
        // allowSigns is true iff the val ends in 'E'
        // found digit it to make sure weird stuff like '.' and '1E-' doesn't pass
        return !allowSigns && foundDigit;
    }
}
