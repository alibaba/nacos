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
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Nacos bootstrap class.
 *
 * @author xiweng.yy
 */
@SpringBootApplication
public class NacosBootstrap {
    
    public static void main(String[] args) {
        ConfigurableApplicationContext coreContext = new SpringApplicationBuilder(
                NacosServerBasicApplication.class).web(WebApplicationType.NONE).run(args);
        //        startWithoutConsole(args, coreContext);
        startWithConsole(args, coreContext);
    }
    
    private static void startWithoutConsole(String[] args, ConfigurableApplicationContext coreContext) {
        ConfigurableApplicationContext webContext = new SpringApplicationBuilder(
                NacosServerWebApplication.class).parent(coreContext).run(args);
    }
    
    private static void startWithConsole(String[] args, ConfigurableApplicationContext coreContext) {
        ConfigurableApplicationContext serverWebContext = new SpringApplicationBuilder(
                NacosServerWebApplication.class).parent(coreContext).run(args);
        System.out.println(serverWebContext.getEnvironment().getProperty("nacos.k8s.sync.enabled"));
        ConfigurableApplicationContext consoleWebContext = new SpringApplicationBuilder(NacosConsole.class).parent(
                coreContext).run(args);
        System.out.println(consoleWebContext.getEnvironment().getProperty("nacos.k8s.sync.enabled"));
    }
}