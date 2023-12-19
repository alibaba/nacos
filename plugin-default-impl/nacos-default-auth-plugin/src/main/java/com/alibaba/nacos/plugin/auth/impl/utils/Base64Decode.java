/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.utils;

import java.util.Arrays;

/**
 * Base64Decoder.
 *
 * @author xYohn
 * @date 2023/8/7
 */
public class Base64Decode {
    private static final char[] BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    private static final int[] BASE64_IALPHABET = new int[256];

    private static final int IALPHABET_MAX_INDEX = BASE64_IALPHABET.length - 1;

    private static final int[] IALPHABET = BASE64_IALPHABET;

    static {
        Arrays.fill(BASE64_IALPHABET, -1);
        for (int i = 0, iS = BASE64_ALPHABET.length; i < iS; i++) {
            BASE64_IALPHABET[BASE64_ALPHABET[i]] = i;
        }
        BASE64_IALPHABET['='] = 0;
    }

    /**
     * Decodes a Base64 encoded String into a newly-allocated byte array using the Base64 encoding scheme.
     *
     * @param input the string to decode
     * @return a byte array containing binary data
     */
    public static byte[] decode(String input) {

        // Check special case
        if (input == null || input.equals("")) {
            return new byte[0];
        }
        char[] sArr = input.toCharArray();
        int sLen = sArr.length;
        if (sLen == 0) {
            return new byte[0];
        }

        int sIx = 0;
        // Start and end index after trimming.
        int eIx = sLen - 1;

        // Trim illegal chars from start
        while (sIx < eIx && IALPHABET[sArr[sIx]] < 0) {
            sIx++;
        }

        // Trim illegal chars from end
        while (eIx > 0 && IALPHABET[sArr[eIx]] < 0) {
            eIx--;
        }

        // get the padding count (=) (0, 1 or 2)
        // Count '=' at end.
        int pad = sArr[eIx] == '=' ? (sArr[eIx - 1] == '=' ? 2 : 1) : 0;
        // Content count including possible separators
        int cCnt = eIx - sIx + 1;
        int sepCnt = sLen > 76 ? (sArr[76] == '\r' ? cCnt / 78 : 0) << 1 : 0;
        // The number of decoded bytes
        int len = ((cCnt - sepCnt) * 6 >> 3) - pad;
        // Preallocate byte[] of exact length
        byte[] dArr = new byte[len];

        // Decode all but the last 0 - 2 bytes.
        int d = 0;
        int three = 3;
        int eight = 8;
        for (int cc = 0, eLen = (len / three) * three; d < eLen; ) {

            // Assemble three bytes into an int from four "valid" characters.
            int i = ctoi(sArr[sIx++]) << 18 | ctoi(sArr[sIx++]) << 12 | ctoi(sArr[sIx++]) << 6 | ctoi(sArr[sIx++]);

            // Add the bytes
            dArr[d++] = (byte) (i >> 16);
            dArr[d++] = (byte) (i >> 8);
            dArr[d++] = (byte) i;

            // If line separator, jump over it.
            if (sepCnt > 0 && ++cc == 19) {
                sIx += 2;
                cc = 0;
            }
        }

        if (d < len) {
            // Decode last 1-3 bytes (incl '=') into 1-3 bytes
            int i = 0;
            for (int j = 0; sIx <= eIx - pad; j++) {
                i |= ctoi(sArr[sIx++]) << (18 - j * 6);
            }

            for (int r = 16; d < len; r -= eight) {
                dArr[d++] = (byte) (i >> r);
            }
        }

        return dArr;
    }

    private static int ctoi(char c) {
        int i = c > IALPHABET_MAX_INDEX ? -1 : IALPHABET[c];
        if (i < 0) {
            String msg = "Illegal base64 character: '" + c + "'";
            throw new IllegalArgumentException(msg);
        }
        return i;
    }

}
