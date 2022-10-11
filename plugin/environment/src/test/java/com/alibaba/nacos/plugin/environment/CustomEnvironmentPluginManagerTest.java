package com.alibaba.nacos.plugin.environment;

import com.alibaba.nacos.plugin.environment.spi.CustomEnvironmentPluginService;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * CustomEnvironment Plugin Test.
 *
 * @author : huangtianhui
 */
public class CustomEnvironmentPluginManagerTest {
    @Test
    public void testInstance() {
        CustomEnvironmentPluginManager instance = CustomEnvironmentPluginManager.getInstance();
        Assert.assertNotNull(instance);
    }

    @Test
    public void testJoin() {
        CustomEnvironmentPluginManager.join(new CustomEnvironmentPluginService() {
            @Override
            public Map<String, Object> customValue(Map<String, Object> property) {
                String pwd = (String) property.get("db.password.0");
                property.put("db.password.0", "test" + pwd);
                return property;
            }

            @Override
            public Set<String> propertyKey() {
                Set<String> propertyKey = new HashSet<>();
                propertyKey.add("db.password.0");
                return propertyKey;
            }

            @Override
            public Integer order() {
                return 0;
            }

            @Override
            public String pluginName() {
                return "test";
            }
        });
    }
}
