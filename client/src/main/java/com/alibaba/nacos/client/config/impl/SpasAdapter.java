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

package com.alibaba.nacos.client.config.impl;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.codec.Base64;
import com.alibaba.nacos.client.identify.CredentialService;
import com.alibaba.nacos.common.utils.StringUtils;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.HashMap;
import java.util.Map;

/**
 * 适配spas接口.
 *
 * @author Nacos
 */
public class SpasAdapter {

    public static Map<String, String> getSignHeaders(String resource, String secretKey) {
        Map<String, String> header = new HashMap<String, String>(2);
        String timeStamp = String.valueOf(System.currentTimeMillis());
        header.put("Timestamp", timeStamp);
        if (secretKey != null) {
            String signature = "";
            if (StringUtils.isBlank(resource)) {
                signature = signWithHmacSha1Encrypt(timeStamp, secretKey);
            } else {
                signature = signWithHmacSha1Encrypt(resource + "+" + timeStamp, secretKey);
            }
            header.put("Spas-Signature", signature);
        }
        return header;
    }

    public static Map<String, String> getSignHeaders(Map<String, String> paramValues, String secretKey) {
        if (null == paramValues) {
            return null;
        }
        
        String resource = "";
        if (paramValues.containsKey(TENANT_KEY) && paramValues.containsKey(GROUP_KEY)) {
            resource = paramValues.get(TENANT_KEY) + "+" + paramValues.get(GROUP_KEY);
        } else {
            if (!StringUtils.isBlank(paramValues.get(GROUP_KEY))) {
                resource = paramValues.get(GROUP_KEY);
            }
        }
        return getSignHeaders(resource, secretKey);
    }
    
    public static String getSk() {
        return CredentialService.getInstance().getCredential().getSecretKey();
    }
    
    public static String getAk() {
        return CredentialService.getInstance().getCredential().getAccessKey();
    }
    
    public static void freeCredentialInstance() {
        CredentialService.freeInstance();
    }
    
    /**
     * Sign with hmac SHA1 encrtpt.
     *
     * @param encryptText encrypt text
     * @param encryptKey  encrypt key
     * @return base64 string
     */
    public static String signWithHmacSha1Encrypt(String encryptText, String encryptKey) {
        try {
            byte[] data = encryptKey.getBytes(Constants.ENCODE);
            // 根据给定的字节数组构造一个密钥,第二参数指定一个密钥算法的名称
            SecretKey secretKey = new SecretKeySpec(data, "HmacSHA1");
            // 生成一个指定 Mac 算法 的 Mac 对象
            Mac mac = Mac.getInstance("HmacSHA1");
            // 用给定密钥初始化 Mac 对象
            mac.init(secretKey);
            byte[] text = encryptText.getBytes(Constants.ENCODE);
            byte[] textFinal = mac.doFinal(text);
            // 完成 Mac 操作, base64编码，将byte数组转换为字符串
            return new String(Base64.encodeBase64(textFinal), Constants.ENCODE);
        } catch (Exception e) {
            throw new RuntimeException("signWithhmacSHA1Encrypt fail", e);
        }
    }
    
    private static final String GROUP_KEY = "group";
    
    public static final String TENANT_KEY = "tenant";
}
