package com.alibaba.nacos.plugin.auth.impl.controller;

import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
@SpringBootTest(properties = {"nacos.core.auth.oidc-idp=github", "nacos.core.auth.oidc-idp.github.name=Github"})
public class OidcAuthControllerTest {
    
    private OidcAuthController oidcAuthController;
    
    private MockEnvironment environment;
    
    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        oidcAuthController = new OidcAuthController();
    }
    
    @Test
    public void listIdpTest() {
        environment.setProperty("nacos.core.auth.oidc-idp", "github");
        environment.setProperty("nacos.core.auth.oidc-idp.github.name", "Github");
        List<Map<String, String>> idpList = oidcAuthController.list();
        Assert.assertEquals(1, idpList.size());
    }
    
}
