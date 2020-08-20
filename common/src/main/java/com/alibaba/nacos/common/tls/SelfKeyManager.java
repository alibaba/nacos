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

import com.alibaba.nacos.api.utils.StringUtils;
import com.alibaba.nacos.common.utils.IoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Collection;

/**
 * A KeyManager tool returns the specified KeyManager.
 *
 * @author wangwei
 * @date 2020/8/19 4:53 PM
 */
@SuppressWarnings("checkstyle:LineLength")
public final class SelfKeyManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SelfKeyManager.class);
    
    public static final char[] EMPTY_CHARS = {};
    
    /**
     * Returns the Array of {@link KeyManager} for {@link SSLContext#init(KeyManager[], TrustManager[], SecureRandom)}.
     *
     * <p>Returns {@code null} if certificate file path or private key file path missing
     *
     * @param certPath    certificate file path
     * @param keyPath     private key file path
     * @param keyPassword password of private key file
     * @return Array of {@link KeyManager }
     * @throws SSLException If build keyManagerFactory failed
     */
    public static KeyManager[] keyManager(String certPath, String keyPath, String keyPassword) throws SSLException {
        if (StringUtils.isEmpty(certPath) || StringUtils.isEmpty(keyPath)) {
            return null;
        }
        KeyManagerFactory kmf;
        InputStream in = null;
        try {
            
            String algorithm = KeyManagerFactory.getDefaultAlgorithm();
            kmf = KeyManagerFactory.getInstance(algorithm);
            
            KeyStore trustKeyStore = KeyStore.getInstance("JKS");
            trustKeyStore.load(null, null);
            
            in = new FileInputStream(certPath);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            
            Collection<X509Certificate> certs = (Collection<X509Certificate>) cf.generateCertificates(in);
            
            char[] keyPasswordChars = keyPassword == null ? EMPTY_CHARS : keyPassword.toCharArray();
            
            PrivateKey privateKey = getPrivateKey(keyPath, keyPassword);
            
            trustKeyStore.setKeyEntry("key", privateKey, keyPasswordChars, certs.toArray(new X509Certificate[0]));
            
            kmf.init(trustKeyStore, keyPasswordChars);
            return kmf.getKeyManagers();
        } catch (Exception e) {
            LOGGER.error("build client keyManagerFactory failed", e);
            throw new SSLException(e);
        } finally {
            IoUtils.closeQuietly(in);
        }
    }
    
    private static PrivateKey getPrivateKey(String keyPath, String keyPassword)
            throws NoSuchAlgorithmException, InvalidKeySpecException, IOException, KeyException, InvalidAlgorithmParameterException, NoSuchPaddingException {
        byte[] keyBytes = PemReader.readPrivateKey(keyPath);
        PKCS8EncodedKeySpec encodedKeySpec = generateKeySpec(keyPassword == null ? null : keyPassword.toCharArray(),
                keyBytes);
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(encodedKeySpec);
        } catch (InvalidKeySpecException ignore) {
            try {
                return KeyFactory.getInstance("DSA").generatePrivate(encodedKeySpec);
            } catch (InvalidKeySpecException ignored) {
                try {
                    return KeyFactory.getInstance("EC").generatePrivate(encodedKeySpec);
                } catch (InvalidKeySpecException e) {
                    throw new InvalidKeySpecException("Neither RSA, DSA nor EC worked", e);
                }
            }
        }
    }
    
    private static PKCS8EncodedKeySpec generateKeySpec(char[] password, byte[] key)
            throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeySpecException, InvalidKeyException, InvalidAlgorithmParameterException {
        
        if (password == null) {
            return new PKCS8EncodedKeySpec(key);
        }
        
        EncryptedPrivateKeyInfo encryptedPrivateKeyInfo = new EncryptedPrivateKeyInfo(key);
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(encryptedPrivateKeyInfo.getAlgName());
        PBEKeySpec pbeKeySpec = new PBEKeySpec(password);
        SecretKey pbeKey = keyFactory.generateSecret(pbeKeySpec);
        
        Cipher cipher = Cipher.getInstance(encryptedPrivateKeyInfo.getAlgName());
        cipher.init(Cipher.DECRYPT_MODE, pbeKey, encryptedPrivateKeyInfo.getAlgParameters());
        
        return encryptedPrivateKeyInfo.getKeySpec(cipher);
    }
}
