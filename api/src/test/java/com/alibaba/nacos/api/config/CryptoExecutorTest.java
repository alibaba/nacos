package com.alibaba.nacos.api.config;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * CryptoExecutorTest.
 *
 * @author lixiaoshuang
 */
public class CryptoExecutorTest {
    
    @Before
    public void setUp() {
        CryptoManager.join(new CryptoSpi() {
            @Override
            public String encrypt(String secretKey, String content) {
                return content;
            }
            
            @Override
            public String decrypt(String secretKey, String content) {
                return content;
            }
            
            @Override
            public String generateSecretKey() {
                return "1234567890";
            }
            
            @Override
            public String named() {
                return "aes";
            }
        });
    }
    
    @Test
    public void testExecuteEncrypt() {
        CryptoSpi cryptoSpi = CryptoExecutor.cryptoInstance("cipher-aes-test");
        String secretKey = cryptoSpi.generateSecretKey();
        String encrypt = CryptoExecutor.executeEncrypt(cryptoSpi::encrypt, secretKey, "nacos");
        Assert.assertNotNull(encrypt);
    }
    
    @Test
    public void testExecuteDecrypt() {
        String encrypt = CryptoExecutor.executeDecrypt("cipher-aes-test", "1234567890", "nacos");
        Assert.assertNotNull(encrypt);
    }
    
    @Test
    public void testCryptoInstance() {
        CryptoSpi cryptoSpi = CryptoExecutor.cryptoInstance("cipher-aes-test");
        Assert.assertNotNull(cryptoSpi);
    }
}