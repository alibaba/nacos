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

package com.alibaba.nacos.core.web;

import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import jakarta.servlet.ServletContext;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Nacos web server listener which listen web container ready and listen the context path changed.
 *
 * @author xiweng.yy
 */
@Component
@NacosWebBean
public class NacosWebServerListener implements ApplicationListener<WebServerInitializedEvent> {
    
    private static final String SPRING_MANAGEMENT_CONTEXT_NAMESPACE = "management";
    
    private final ServerMemberManager serverMemberManager;
    
    public NacosWebServerListener(ServerMemberManager serverMemberManager, ServletContext servletContext) {
        this.serverMemberManager = serverMemberManager;
        EnvUtil.setContextPath(servletContext.getContextPath());
    }
    
    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        String serverNamespace = event.getApplicationContext().getServerNamespace();
        if (SPRING_MANAGEMENT_CONTEXT_NAMESPACE.equals(serverNamespace)) {
            // ignore
            // fix#issue https://github.com/alibaba/nacos/issues/7230
            return;
        }
        serverMemberManager.setSelfReady(event.getWebServer().getPort());
    }
}
