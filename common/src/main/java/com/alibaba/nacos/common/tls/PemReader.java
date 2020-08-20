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

package com.alibaba.nacos.common.tls;

import com.alibaba.nacos.common.utils.IoUtils;
import sun.misc.BASE64Decoder;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a PEM file and converts it into a list of DERs. Support PKCS #8 format
 *
 * @author wangwei
 * @date 2020/8/20 9:32 AM
 */
final class PemReader {
    
    /**
     * Header + Base64 text + Footer.
     */
    private static final Pattern KEY_PATTERN = Pattern.compile(
            "-+BEGIN\\s+.*PRIVATE\\s+KEY[^-]*-+(?:\\s|\\r|\\n)+" + "([a-z0-9+/=\\r\\n]+)"
                    + "-+END\\s+.*PRIVATE\\s+KEY[^-]*-+", Pattern.CASE_INSENSITIVE);
    
    private static final String ENCODE_US_ASCII = "US-ASCII";
    
    static byte[] readPrivateKey(String keyPath) throws KeyException, IOException {
        try {
            InputStream in = new FileInputStream(keyPath);
            try {
                String content;
                try {
                    content = IoUtils.toString(in, ENCODE_US_ASCII);
                } catch (IOException e) {
                    throw new KeyException("failed to read key input stream", e);
                }
                
                Matcher m = KEY_PATTERN.matcher(content);
                if (!m.find()) {
                    throw new KeyException("could not find a PKCS #8 private key in input stream");
                }
                
                final BASE64Decoder base64 = new BASE64Decoder();
                return base64.decodeBuffer(m.group(1));
            } finally {
                IoUtils.closeQuietly(in);
            }
        } catch (FileNotFoundException e) {
            throw new KeyException("could not fine key file: " + keyPath);
        }
    }
}
