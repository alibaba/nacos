package com.alibaba.nacos.plugin.auth.impl;

import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthSystemTypes;
import com.alibaba.nacos.plugin.auth.impl.remote.AuthLoginRequest;
import com.alibaba.nacos.plugin.auth.impl.remote.AuthLoginResponse;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

/**
 * AuthLoginRequestHandlerTest.
 *
 * @author Nacos
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthLoginRequestHandlerTest {
    
    @Mock(lenient = true)
    JwtTokenManager jwtTokenManager;
    
    @Mock
    private AuthConfigs authConfigs;
    
    @Mock
    private NacosAuthManager authManager;
    
    private NacosUser user;
    
    private AuthLoginRequestHandler authLoginRequestHandler;
    
    @Before
    public void setUp() throws Exception {
        authLoginRequestHandler = new AuthLoginRequestHandler(jwtTokenManager, authConfigs, authManager, null);
        user = new NacosUser();
        user.setUserName("nacos");
        user.setGlobalAdmin(true);
        user.setToken("1234567890");
        Properties properties = new Properties();
        properties.setProperty(AuthConstants.TOKEN_SECRET_KEY,
                "SecretKey012345678901234567890123456789012345678901234567890123456789");
        properties.setProperty(AuthConstants.TOKEN_EXPIRE_SECONDS, "300");
        when(authConfigs.getAuthPluginProperties(AuthConstants.AUTH_PLUGIN_TYPE)).thenReturn(properties);
        JwtTokenManager jwtTokenManager = new JwtTokenManager(authConfigs);
        jwtTokenManager.initProperties();
    }
    
    @Test
    public void handle() throws AccessException {
        when(authManager.login(Mockito.any())).thenReturn(user);
        when(authConfigs.getNacosAuthSystemType()).thenReturn(AuthSystemTypes.NACOS.name());
        when(jwtTokenManager.getTokenValidityInSeconds()).thenReturn(300L);
        
        final AuthLoginRequest authLoginRequest = new AuthLoginRequest("nacos", "nacos");
        final AuthLoginResponse authLoginResponse = authLoginRequestHandler.handle(authLoginRequest, new RequestMeta());
        
        assertEquals("1234567890", authLoginResponse.getAccessToken());
        assertEquals(300L, authLoginResponse.getTokenTtl().longValue());
    }
    
}
