package com.alibaba.nacos.config.server.configuration;

import com.alibaba.nacos.common.event.ServerConfigChangeEvent;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Nacos config change configs test.
 *
 * @author liyunfei
 **/
public class ConfigChangeConfigsTest {
    
    private ConfigChangeConfigs configChangeConfigs;
    
    private MockEnvironment environment;
    
    @Before
    public void setUp() throws Exception {
        environment = new MockEnvironment();
        environment.setProperty("nacos.core.config.plugin.mockPlugin.enabled", "true");
        EnvUtil.setEnvironment(environment);
        configChangeConfigs = new ConfigChangeConfigs();
    }
    
    @Test
    public void testEnable() {
        Assert.assertTrue(Boolean.parseBoolean(configChangeConfigs
                .getPluginProperties("mockPlugin").getProperty("enabled")));
    }
    
    @Test
    public void testUpgradeEnable() {
        environment.setProperty("nacos.core.config.plugin.mockPlugin.enabled", "false");
        configChangeConfigs.onEvent(ServerConfigChangeEvent.newEvent());
        Assert.assertFalse(Boolean.parseBoolean(configChangeConfigs
                .getPluginProperties("mockPlugin").getProperty("enabled")));
    }
    
}
