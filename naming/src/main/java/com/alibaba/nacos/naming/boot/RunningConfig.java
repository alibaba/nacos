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

import com.alibaba.nacos.core.cluster.ServerInitializedEvent;
import com.alibaba.nacos.core.notify.Event;
import com.alibaba.nacos.core.notify.NotifyCenter;
import com.alibaba.nacos.core.notify.listener.Subscribe;
import com.alibaba.nacos.core.utils.ApplicationUtils;
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
public class RunningConfig implements Subscribe<ServerInitializedEvent> {

    private static int serverPort;

    private static String contextPath;

    private static volatile boolean isServerInitialized = false;

    private static final RunningConfig RUNNING_CONFIG = new RunningConfig();

    static {
        NotifyCenter.registerSubscribe(RUNNING_CONFIG);
    }

    @Override
    public void onEvent(ServerInitializedEvent event) {
        serverPort = event.getEvent().getWebServer().getPort();
        contextPath = event.getServletContext().getContextPath();

        Loggers.SRV_LOG.info("[SERVER-INIT] got port: {}", serverPort);
        Loggers.SRV_LOG.info("[SERVER-INIT] got path: {}", contextPath);

        isServerInitialized = true;

        NotifyCenter.deregisterPublisher(ServerInitializedEvent.class);
    }

    public static int getServerPort() {
        return serverPort;
    }

    public static String getContextPath() {

        if (!isServerInitialized) {
            String contextPath = ApplicationUtils.getProperty(Constants.WEB_CONTEXT_PATH);
            if (Constants.ROOT_WEB_CONTEXT_PATH.equals(contextPath)) {
                return StringUtils.EMPTY;
            } else {
                return contextPath;
            }
        }
        return contextPath;
    }

    @Override
    public Class<? extends Event> subscribeType() {
        return ServerInitializedEvent.class;
    }
}
