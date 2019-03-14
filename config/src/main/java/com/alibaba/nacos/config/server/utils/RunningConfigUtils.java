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
package com.alibaba.nacos.config.server.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import javax.servlet.ServletContext;

/**
 * Running config
 * @author nkorange
 */
@Component
public class RunningConfigUtils implements ApplicationListener<WebServerInitializedEvent> {

    private static int serverPort;

    private static String contextPath;

    private static String clusterName = "serverlist";

	@Autowired
    private ServletContext servletContext;

    @Override
	public void onApplicationEvent(WebServerInitializedEvent event) {

		setServerPort(event.getWebServer().getPort());
		setContextPath(servletContext.getContextPath());
	}

    public static int getServerPort() {
        return serverPort;
    }

    public static String getContextPath() {
        return contextPath;
    }

    public static String getClusterName() {
		return clusterName;
	}

	public static void setServerPort(int serverPort) {
		RunningConfigUtils.serverPort = serverPort;
	}

	public static void setContextPath(String contextPath) {
		RunningConfigUtils.contextPath = contextPath;
	}

}
