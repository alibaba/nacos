/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.config;

import com.alibaba.nacos.console.NacosConsole;
import com.alibaba.nacos.core.listener.startup.NacosStartUp;
import com.alibaba.nacos.core.listener.startup.NacosStartUpManager;
import com.alibaba.nacos.plugin.auth.impl.NacosAuthPluginService;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManagerDelegate;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginManager;
import com.alibaba.nacos.sys.env.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConsoleAuthPluginLifecycleContextTest {
    
    private static final String TOKEN_SECRET = Base64.getEncoder().encodeToString(
        "IndependentConsoleAuthPluginLifecycleSecretKey0123456789"
            .getBytes(StandardCharsets.UTF_8));
    
    private final ConfigurableEnvironment cachedEnvironment = EnvUtil.getEnvironment();
    
    @TempDir
    private Path tempDir;
    
    private ConfigurableApplicationContext context;
    
    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
        EnvUtil.setNacosHomePath(null);
        System.clearProperty(EnvUtil.NACOS_HOME_KEY);
        System.clearProperty(Constants.NACOS_DEPLOYMENT_TYPE);
        EnvUtil.setEnvironment(cachedEnvironment);
    }
    
    @Test
    void initializeBuiltInAuthPluginInConsoleOnlyContext() throws Exception {
        Files.createDirectories(tempDir.resolve("logs"));
        Files.createDirectories(tempDir.resolve("conf"));
        Files.createFile(tempDir.resolve("conf/application.properties"));
        EnvUtil.setNacosHomePath(tempDir.toString());
        System.setProperty(EnvUtil.NACOS_HOME_KEY, tempDir.toString());
        System.setProperty(Constants.NACOS_DEPLOYMENT_TYPE,
            Constants.NACOS_DEPLOYMENT_TYPE_CONSOLE);
        NacosStartUpManager.start(NacosStartUp.CONSOLE_START_UP_PHASE);
        context = new SpringApplicationBuilder(NacosConsole.class)
            .web(WebApplicationType.SERVLET)
            .properties("nacos.deployment.type=console", "nacos.core.auth.console.enabled=true",
                "server.port=0",
                "nacos.member.list=127.0.0.1:8848",
                "nacos.core.auth.server.identity.key=test-key",
                "nacos.core.auth.server.identity.value=test-value",
                "nacos.plugin.auth.type=nacos",
                "nacos.plugin.auth.nacos.token.secret.key=" + TOKEN_SECRET,
                "nacos.logs.path=" + tempDir.resolve("logs"),
                "spring.config.additional-location=file:" + tempDir.resolve("conf") + '/',
                "spring.main.lazy-initialization=false")
            .run();
        
        assertNotNull(context.getBean(ConsoleAuthPluginInitializer.class));
        NacosAuthPluginService plugin = (NacosAuthPluginService) AuthPluginManager.getInstance()
            .getAllPlugins().get("nacos");
        TokenManagerDelegate tokenManager = plugin.getTokenManagerDelegate();
        String token = tokenManager.createToken("console-user");
        NacosUser user = tokenManager.parseToken(token);
        
        assertEquals("console-user", user.getUserName());
    }
}
