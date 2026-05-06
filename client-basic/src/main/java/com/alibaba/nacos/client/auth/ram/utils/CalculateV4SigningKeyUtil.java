/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.auth.ram.utils;

import com.alibaba.nacos.client.auth.ram.RamConstants;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * CalculateV4SigningKeyUtil.
 *
 * @author xiweng.yy
 */
public class CalculateV4SigningKeyUtil {
    
    private static final String PREFIX = "aliyun_v4";
    
    private static final String CONSTANT = "aliyun_v4_request";
    
    private static final DateTimeFormatter V4_SIGN_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    
    private static final ZoneId UTC_0 = ZoneId.of("GMT+00:00");
    
    private static byte[] firstSigningKey(String secret, String date, String signMethod)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(signMethod);
        mac.init(new SecretKeySpec((PREFIX + secret).getBytes(StandardCharsets.UTF_8), signMethod));
        return mac.doFinal(date.getBytes(StandardCharsets.UTF_8));
    }
    
    private static byte[] regionSigningKey(String secret, String date, String region, String signMethod)
            throws NoSuchAlgorithmException, InvalidKeyException {
        byte[] firstSignkey = firstSigningKey(secret, date, signMethod);
        Mac mac = Mac.getInstance(signMethod);
        mac.init(new SecretKeySpec(firstSignkey, signMethod));
        return mac.doFinal(region.getBytes(StandardCharsets.UTF_8));
    }
    
    private static byte[] finalSigningKey(String secret, String date, String region, String productCode,
            String signMethod) {
        try {
            byte[] secondSignkey = regionSigningKey(secret, date, region, signMethod);
            Mac mac = Mac.getInstance(signMethod);
            mac.init(new SecretKeySpec(secondSignkey, signMethod));
            byte[] thirdSigningKey = mac.doFinal(productCode.getBytes(StandardCharsets.UTF_8));
            // 计算最终派生秘钥
            mac = Mac.getInstance(signMethod);
            mac.init(new SecretKeySpec(thirdSigningKey, signMethod));
            return mac.doFinal(CONSTANT.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("unsupported Algorithm:" + signMethod);
        } catch (InvalidKeyException e) {
            throw new RuntimeException("InvalidKey");
        }
    }
    
    /**
     * Return V4 signature key with base64 encode.
     *
     * @param secret      secret key
     * @param date        date  with utc format, like 20211222
     * @param region      region id
     * @param productCode cloud product code
     * @param signMethod  sign method
     * @return V4 signature key with base64 encode
     */
    public static String finalSigningKeyString(String secret, String date, String region, String productCode,
            String signMethod) {
        return Base64.getEncoder().encodeToString(finalSigningKey(secret, date, region, productCode, signMethod));
    }
    
    /**
     * Return V4 signature key with base64 encode for some default information.
     *
     * <li>
     *     <ul>date = current date</ul>
     *     <ul>produceCode = mse</ul>
     *     <ul>signMethod = HMAC-SHA256</ul>
     * </li>
     *
     * @param secret secret key
     * @param region region id
     * @return V4 signature key with base64 encode
     */
    public static String finalSigningKeyStringWithDefaultInfo(String secret, String region) {
        String signDate = LocalDateTime.now(UTC_0).format(V4_SIGN_DATE_FORMATTER);
        return finalSigningKeyString(secret, signDate, region, RamConstants.SIGNATURE_V4_PRODUCE,
                RamConstants.SIGNATURE_V4_METHOD);
    }
}
