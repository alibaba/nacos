/*
 *   Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.alibaba.nacos.client.config;

import com.alibaba.nacos.api.config.CryptoSpi;
import com.alibaba.nacos.api.utils.StringUtils;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.SecureRandom;

/**
 * AES Encryption algorithm implementation.
 *
 * @author lixiaoshuang
 */
public class AesCrypto implements CryptoSpi {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AesCrypto.class);
    
    public static final String AES_NAME = "aes";
    
    private static final String AES_MODE = "AES/ECB/PKCS5Padding";
    
    private static final String DEFAULT_SECRET_KEY = "nacos6b31e19f931a7603ae5473250b4";
    
    @Override
    public String encrypt(String secretKey, String content) {
        if (StringUtils.isBlank(secretKey)) {
            return content;
        }
        try {
            Key key = new SecretKeySpec(Hex.decodeHex(secretKey), AES_NAME);
            Cipher cipher = Cipher.getInstance(AES_MODE);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] result = cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return Hex.encodeHexString(result);
        } catch (Exception e) {
            LOGGER.error("encrypt error", e);
        }
        return content;
    }
    
    @Override
    public String decrypt(String secretKey, String content) {
        if (StringUtils.isBlank(secretKey) || StringUtils.isBlank(content)) {
            return content;
        }
        try {
            Key key = new SecretKeySpec(Hex.decodeHex(secretKey), AES_NAME);
            Cipher cipher = Cipher.getInstance(AES_MODE);
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] result = cipher.doFinal(Hex.decodeHex(content));
            return new String(result);
        } catch (Exception e) {
            LOGGER.error("decrypt error", e);
        }
        return content;
    }
    
    @Override
    public String generateSecretKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(AES_NAME);
            keyGenerator.init(new SecureRandom());
            SecretKey secretKey = keyGenerator.generateKey();
            byte[] byteKey = secretKey.getEncoded();
            return Hex.encodeHexString(byteKey);
        } catch (Exception e) {
            LOGGER.error("generate key error", e);
        }
        return DEFAULT_SECRET_KEY;
    }
    
    @Override
    public String named() {
        return AES_NAME;
    }
}
