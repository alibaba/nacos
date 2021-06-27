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
import com.alibaba.nacos.client.identify.CredentialService;
import com.alibaba.nacos.common.codec.Base64;
import com.alibaba.nacos.common.utils.StringUtils;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.HashMap;
import java.util.Map;

/**
 * adapt spas interface.
 *
 * @author Nacos
 */
public class SpasAdapter {
    
    private static final String TIMESTAMP_HEADER = "Timestamp";
    
    private static final String SIGNATURE_HEADER = "Spas-Signature";
    
    private static final String GROUP_KEY = "group";
    
    public static final String TENANT_KEY = "tenant";
    
    private static final String SHA_ENCRYPT = "HmacSHA1";
    
    public static Map<String, String> getSignHeaders(String resource, String secretKey) {
        Map<String, String> header = new HashMap<String, String>(2);
        String timeStamp = String.valueOf(System.currentTimeMillis());
        header.put(TIMESTAMP_HEADER, timeStamp);
        if (secretKey != null) {
            String signature;
            if (StringUtils.isBlank(resource)) {
                signature = signWithHmacSha1Encrypt(timeStamp, secretKey);
            } else {
                signature = signWithHmacSha1Encrypt(resource + "+" + timeStamp, secretKey);
            }
            header.put(SIGNATURE_HEADER, signature);
        }
        return header;
    }
    
    public static Map<String, String> getSignHeaders(String groupKey, String tenant, String secretKey) {
        if (StringUtils.isBlank(groupKey) && StringUtils.isBlank(tenant)) {
            return null;
        }
        
        String resource = "";
        if (StringUtils.isNotBlank(groupKey) && StringUtils.isNotBlank(tenant)) {
            resource = tenant + "+" + groupKey;
        } else {
            if (!StringUtils.isBlank(groupKey)) {
                resource = groupKey;
            }
        }
        return getSignHeaders(resource, secretKey);
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
            // Construct a key according to the given byte array, and the second parameter specifies the name of a key algorithm
            SecretKey secretKey = new SecretKeySpec(data, SHA_ENCRYPT);
            // Generate a Mac object specifying Mac algorithm
            Mac mac = Mac.getInstance(SHA_ENCRYPT);
            // Initialize the Mac object with the given key
            mac.init(secretKey);
            byte[] text = encryptText.getBytes(Constants.ENCODE);
            byte[] textFinal = mac.doFinal(text);
            // Complete Mac operation, base64 encoding, convert byte array to string
            return new String(Base64.encodeBase64(textFinal), Constants.ENCODE);
        } catch (Exception e) {
            throw new RuntimeException("signWithhmacSHA1Encrypt fail", e);
        }
    }
}
