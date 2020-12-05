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
package com.alibaba.nacos.test.naming;

import java.util.*;

/**
 * Created by wangtong.wt on 2018/6/20.
 *
 * @author wangtong.wt
 * @date 2018/6/20
 */
public class RandomUtils {
    private static Random rd = new Random();
    private static int UNICODE_START = 19968;
    private static int UNICODE_END = 40864;
    private static final String  STRING_POOL = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private RandomUtils() {
    }

    public static long getLong() {
        return rd.nextLong();
    }

    public static long getLongMoreThanZero() {
        long res;
        for(res = rd.nextLong(); res <= 0L; res = rd.nextLong()) {
        }

        return res;
    }

    public static long getLongLessThan(long n) {
        long res = rd.nextLong();
        return res % n;
    }

    public static long getLongMoreThanZeroLessThan(long n) {
        long res;
        for(res = getLongLessThan(n); res <= 0L; res = getLongLessThan(n)) {
        }

        return res;
    }

    public static long getLongBetween(long n, long m) {
        if (m <= n) {
            return n;
        } else {
            long res = getLongMoreThanZero();
            return n + res % (m - n);
        }
    }

    public static int getInteger() {
        return rd.nextInt();
    }

    public static int getIntegerMoreThanZero() {
        int res;
        for(res = rd.nextInt(); res <= 0; res = rd.nextInt()) {
        }

        return res;
    }

    public static int getIntegerLessThan(int n) {
        int res = rd.nextInt();
        return res % n;
    }

    public static int getIntegerMoreThanZeroLessThan(int n) {
        int res;
        for(res = rd.nextInt(n); res == 0; res = rd.nextInt(n)) {
        }

        return res;
    }

    public static int getIntegerBetween(int n, int m) {
        if (m == n) {
            return n;
        } else {
            int res = getIntegerMoreThanZero();
            return n + res % (m - n);
        }
    }

    private static char getChar(int[] arg) {
        int size = arg.length;
        int c = rd.nextInt(size / 2);
        c *= 2;
        return (char)getIntegerBetween(arg[c], arg[c + 1]);
    }

    private static String getString(int n, int[] arg) {
        StringBuilder res = new StringBuilder();

        for(int i = 0; i < n; ++i) {
            res.append(getChar(arg));
        }

        return res.toString();
    }

    public static String getStringWithCharacter(int n) {
        int[] arg = new int[]{97, 123, 65, 91};
        return getString(n, arg);
    }

    public static String getStringWithNumber(int n) {
        int[] arg = new int[]{48, 58};
        return getString(n, arg);
    }

    public static String getStringWithNumAndCha(int n) {
        int[] arg = new int[]{97, 123, 65, 91, 48, 58};
        return getString(n, arg);
    }

    public static String getRandomString(int length) {
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        int range = STRING_POOL.length();

        for(int i = 0; i < length; ++i) {
            sb.append(STRING_POOL.charAt(random.nextInt(range)));
        }

        return sb.toString();
    }

    public static String getStringShortenThan(int n) {
        int len = getIntegerMoreThanZeroLessThan(n);
        return getStringWithCharacter(len);
    }

    public static String getStringWithNumAndChaShortenThan(int n) {
        int len = getIntegerMoreThanZeroLessThan(n);
        return getStringWithNumAndCha(len);
    }

    public static String getStringBetween(int n, int m) {
        int len = getIntegerBetween(n, m);
        return getStringWithCharacter(len);
    }

    public static String getStringWithNumAndChaBetween(int n, int m) {
        int len = getIntegerBetween(n, m);
        return getStringWithNumAndCha(len);
    }

    public static String getStringWithPrefix(int n, String prefix) {
        int len = prefix.length();
        if (n <= len) {
            return prefix;
        } else {
            len = n - len;
            StringBuilder res = new StringBuilder(prefix);
            res.append(getStringWithCharacter(len));
            return res.toString();
        }
    }

    public static String getStringWithSuffix(int n, String suffix) {
        int len = suffix.length();
        if (n <= len) {
            return suffix;
        } else {
            len = n - len;
            StringBuilder res = new StringBuilder();
            res.append(getStringWithCharacter(len));
            res.append(suffix);
            return res.toString();
        }
    }

    public static String getStringWithBoth(int n, String prefix, String suffix) {
        int len = prefix.length() + suffix.length();
        StringBuilder res = new StringBuilder(prefix);
        if (n <= len) {
            return res.append(suffix).toString();
        } else {
            len = n - len;
            res.append(getStringWithCharacter(len));
            res.append(suffix);
            return res.toString();
        }
    }

    public static String getCheseWordWithPrifix(int n, String prefix) {
        int len = prefix.length();
        if (n <= len) {
            return prefix;
        } else {
            len = n - len;
            StringBuilder res = new StringBuilder(prefix);
            res.append(getCheseWord(len));
            return res.toString();
        }
    }

    public static String getCheseWordWithSuffix(int n, String suffix) {
        int len = suffix.length();
        if (n <= len) {
            return suffix;
        } else {
            len = n - len;
            StringBuilder res = new StringBuilder();
            res.append(getCheseWord(len));
            res.append(suffix);
            return res.toString();
        }
    }

    public static String getCheseWordWithBoth(int n, String prefix, String suffix) {
        int len = prefix.length() + suffix.length();
        StringBuilder res = new StringBuilder(prefix);
        if (n <= len) {
            return res.append(suffix).toString();
        } else {
            len = n - len;
            res.append(getCheseWord(len));
            res.append(suffix);
            return res.toString();
        }
    }

    public static String getCheseWord(int len) {
        StringBuilder res = new StringBuilder();

        for(int i = 0; i < len; ++i) {
            char str = getCheseChar();
            res.append(str);
        }

        return res.toString();
    }

    private static char getCheseChar() {
        return (char)(UNICODE_START + rd.nextInt(UNICODE_END - UNICODE_START));
    }

    public static boolean getBoolean() {
        return getIntegerMoreThanZeroLessThan(3) == 1;
    }

    public static String getStringByUUID() {
        return UUID.randomUUID().toString();
    }

    public static int[] getRandomArray(int min, int max, int n) {
        int len = max - min + 1;
        if (max >= min && n <= len) {
            int[] source = new int[len];

            for(int i = min; i < min + len; source[i - min] = i++) {
            }

            int[] result = new int[n];
            Random rd = new Random();

            for(int i = 0; i < result.length; ++i) {
                int index = Math.abs(rd.nextInt() % len--);
                result[i] = source[index];
                source[index] = source[len];
            }

            return result;
        } else {
            return null;
        }
    }

    public static Collection<Integer> getRandomCollection(int min, int max, int n) {
        Set<Integer> res = new HashSet();
        int mx = max;
        int mn = min;
        int i;
        if (n == max + 1 - min) {
            for(i = 1; i <= n; ++i) {
                res.add(i);
            }

            return res;
        } else {
            for(i = 0; i < n; ++i) {
                int v = getIntegerBetween(mn, mx);
                if (v == mx) {
                    --mx;
                }

                if (v == mn) {
                    ++mn;
                }

                while(res.contains(v)) {
                    v = getIntegerBetween(mn, mx);
                    if (v == mx) {
                        mx = v;
                    }

                    if (v == mn) {
                        mn = v;
                    }
                }

                res.add(v);
            }

            return res;
        }
    }
}
