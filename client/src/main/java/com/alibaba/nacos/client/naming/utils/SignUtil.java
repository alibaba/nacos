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

package com.alibaba.nacos.client.naming.utils;

import com.alibaba.nacos.common.codec.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Sign util.
 *
 * @author pbting
 * @date 2019-01-22 10:20 PM
 */
public class SignUtil {
    
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    
    public SignUtil() {
    }
    
    /**
     * Sign.
     *
     * @param data data
     * @param key  key
     * @return signature
     * @throws Exception exception
     */
    public static String sign(String data, String key) throws Exception {
        try {
            byte[] signature = sign(data.getBytes(UTF8), key.getBytes(UTF8), SignUtil.SigningAlgorithm.HmacSHA1);
            return new String(Base64.encodeBase64(signature));
        } catch (Exception ex) {
            throw new Exception("Unable to calculate a request signature: " + ex.getMessage(), ex);
        }
    }
    
    private static byte[] sign(byte[] data, byte[] key, SignUtil.SigningAlgorithm algorithm) throws Exception {
        try {
            Mac mac = Mac.getInstance(algorithm.toString());
            mac.init(new SecretKeySpec(key, algorithm.toString()));
            return mac.doFinal(data);
        } catch (Exception ex) {
            throw new Exception("Unable to calculate a request signature: " + ex.getMessage(), ex);
        }
    }
    
    public enum SigningAlgorithm {
        // Hmac SHA1 algorithm
        HmacSHA1;
        
        SigningAlgorithm() {
        }
    }
}
