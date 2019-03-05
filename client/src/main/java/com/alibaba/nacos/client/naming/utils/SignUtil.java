/*
 * Copyright (C) 2019 the original author or authors.
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

import com.alibaba.nacos.client.identify.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.Charset;

/**
 * @author pbting
 * @date 2019-01-22 10:20 PM
 */
public class SignUtil {
    public static final Charset UTF8 = Charset.forName("UTF-8");

    public SignUtil() {
    }

    public static String sign(String data, String key) throws Exception {
        try {
            byte[] signature = sign(data.getBytes(UTF8), key.getBytes(UTF8),
                SignUtil.SigningAlgorithm.HmacSHA1);
            return new String(Base64.encodeBase64(signature));
        } catch (Exception var3) {
            throw new Exception(
                "Unable to calculate a request signature: " + var3.getMessage(),
                var3);
        }
    }

    private static byte[] sign(byte[] data, byte[] key,
                               SignUtil.SigningAlgorithm algorithm) throws Exception {
        try {
            Mac mac = Mac.getInstance(algorithm.toString());
            mac.init(new SecretKeySpec(key, algorithm.toString()));
            return mac.doFinal(data);
        } catch (Exception var4) {
            throw new Exception(
                "Unable to calculate a request signature: " + var4.getMessage(),
                var4);
        }
    }

    public enum SigningAlgorithm {
        // Hmac SHA1 algorithm
        HmacSHA1;

        SigningAlgorithm() {
        }
    }
}
