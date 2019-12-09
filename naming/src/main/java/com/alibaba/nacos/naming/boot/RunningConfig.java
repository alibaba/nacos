/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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
package com.alibaba.nacos.naming.boot;

import com.alibaba.nacos.core.utils.Constants;
import com.alibaba.nacos.core.utils.PropertyUtil;
import com.alibaba.nacos.naming.misc.Loggers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import javax.servlet.ServletContext;
import java.util.TreeMap;

/**
 * @author nkorange
 */
@Component("runningConfig")
public class RunningConfig implements ApplicationListener<WebServerInitializedEvent> {

    private static int serverPort;

    private static String contextPath;

    @Autowired
    private ServletContext servletContext;

    private static volatile boolean isServerInitialized = false;

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {

        Loggers.SRV_LOG.info("[SERVER-INIT] got port: {}", event.getWebServer().getPort());
        Loggers.SRV_LOG.info("[SERVER-INIT] got path: {}", servletContext.getContextPath());

        serverPort = event.getWebServer().getPort();
        contextPath = servletContext.getContextPath();
        isServerInitialized = true;
    }

    public static int getServerPort() {
        return serverPort;
    }

    public static String getContextPath() {

        if (!isServerInitialized) {
            String contextPath = PropertyUtil.getProperty(Constants.WEB_CONTEXT_PATH);
            if (Constants.ROOT_WEB_CONTEXT_PATH.equals(contextPath)) {
                return StringUtils.EMPTY;
            } else {
                return contextPath;
            }
        }
        return contextPath;
    }
}
