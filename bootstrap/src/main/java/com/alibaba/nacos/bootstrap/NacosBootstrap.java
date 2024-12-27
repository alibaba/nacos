/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.bootstrap;

import com.alibaba.nacos.NacosServerBasicApplication;
import com.alibaba.nacos.NacosServerWebApplication;
import com.alibaba.nacos.console.NacosConsole;
import com.alibaba.nacos.core.listener.startup.NacosStartUp;
import com.alibaba.nacos.core.listener.startup.NacosStartUpManager;
import com.alibaba.nacos.sys.env.Constants;
import org.springframework.boot.Banner;
import org.springframework.boot.ResourceBanner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jmx.support.MBeanRegistrationSupport;
import org.springframework.jmx.support.RegistrationPolicy;

/**
 * Nacos bootstrap class.
 *
 * @author xiweng.yy
 */
@SpringBootApplication
public class NacosBootstrap {
    
    public static void main(String[] args) {
        ConfigurableApplicationContext coreContext = startCoreContext(args);
        coreContext.getBean(MBeanRegistrationSupport.class).setRegistrationPolicy(RegistrationPolicy.IGNORE_EXISTING);
        String type = coreContext.getEnvironment()
                .getProperty(Constants.NACOS_DEPLOYMENT_TYPE, Constants.NACOS_DEPLOYMENT_TYPE_MERGED);
        if (Constants.NACOS_DEPLOYMENT_TYPE_MERGED.equals(type)) {
            startWithConsole(args, coreContext);
        } else if (Constants.NACOS_DEPLOYMENT_TYPE_SERVER.equals(type)) {
            startWithoutConsole(args, coreContext);
        } else {
            throw new IllegalArgumentException("Unsupported type " + type);
        }
    }
    
    private static void startWithoutConsole(String[] args, ConfigurableApplicationContext coreContext) {
        ConfigurableApplicationContext webContext = startServerWebContext(args, coreContext);
    }
    
    private static void startWithConsole(String[] args, ConfigurableApplicationContext coreContext) {
        ConfigurableApplicationContext serverWebContext = startServerWebContext(args, coreContext);
        ConfigurableApplicationContext consoleContext = startConsoleContext(args, coreContext);
    }
    
    private static ConfigurableApplicationContext startServerWebContext(String[] args,
            ConfigurableApplicationContext coreContext) {
        NacosStartUpManager.start(NacosStartUp.WEB_START_UP_PHASE);
        return new SpringApplicationBuilder(NacosServerWebApplication.class).parent(coreContext)
                .banner(getBanner("nacos-server-web-banner.txt")).run(args);
    }
    
    private static ConfigurableApplicationContext startConsoleContext(String[] args,
            ConfigurableApplicationContext coreContext) {
        NacosStartUpManager.start(NacosStartUp.CONSOLE_START_UP_PHASE);
        return new SpringApplicationBuilder(NacosConsole.class).parent(coreContext)
                .banner(getBanner("nacos-console-banner.txt")).run(args);
    }
    
    private static ConfigurableApplicationContext startCoreContext(String[] args) {
        NacosStartUpManager.start(NacosStartUp.CORE_START_UP_PHASE);
        return new SpringApplicationBuilder(NacosServerBasicApplication.class).web(WebApplicationType.NONE)
                .banner(getBanner("core-banner.txt")).run(args);
    }
    
    private static Banner getBanner(String bannerFileName) {
        return new ResourceBanner(new ClassPathResource(bannerFileName));
    }
}