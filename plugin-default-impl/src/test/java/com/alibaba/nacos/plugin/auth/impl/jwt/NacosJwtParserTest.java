package com.alibaba.nacos.plugin.auth.impl.jwt;

import com.alibaba.nacos.plugin.auth.exception.AccessException;
import junit.framework.TestCase;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * NacosJwtParserTest.
 *
 * @author Weizhan▪Yun
 * @date 2023/2/1 16:32
 */
public class NacosJwtParserTest extends TestCase {
    
    @Test
    public void testParseWithOriginKey() {
        new NacosJwtParser("SecretKey012345678901234567890123456789012345678901234567890123456789");
    }
    
    @Test
    public void testParseWith16Key() throws AccessException {
        Exception e = null;
        try {
            new NacosJwtParser("SecretKey0123456");
        } catch (Exception exception) {
            e = exception;
        }
        
        assertEquals(IllegalArgumentException.class, e.getClass());
        
    }
    
    @Test
    public void testParseWith32Key() {
        NacosJwtParser parser = new NacosJwtParser(encode("SecretKey01234567890123456789012"));
        String token = parser.jwtBuilder().setUserName("nacos").setExpiredTime(100L).compact();
        
        assertTrue(token.startsWith(NacosSignatureAlgorithm.HS256.getHeader()));
    }
    
    @Test
    public void testParseWith48Key() {
        NacosJwtParser parser = new NacosJwtParser(encode("SecretKey012345678901234567890120124568aa9012345"));
        String token = parser.jwtBuilder().setUserName("nacos").setExpiredTime(100L).compact();
        
        assertTrue(token.startsWith(NacosSignatureAlgorithm.HS384.getHeader()));
    }
    
    @Test
    public void testParseWith64Key() {
        NacosJwtParser parser = new NacosJwtParser(
                encode("SecretKey012345678901234567SecretKey0123456789012345678901289012"));
        String token = parser.jwtBuilder().setUserName("nacos").setExpiredTime(100L).compact();
        
        assertTrue(token.startsWith(NacosSignatureAlgorithm.HS512.getHeader()));
    }
    
    private String encode(String key) {
        return Base64.getEncoder().encodeToString(key.getBytes(StandardCharsets.UTF_8));
    }
}