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

package com.alibaba.nacos.console.config;

import org.apache.catalina.Valve;
import org.apache.catalina.valves.AccessLogValve;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Set max days of tomcat access log
 * as TomcatWebServerFactoryCustomizer of springboot 2.1.17.RELEASE which Nacos is currently using does not set it.
 *
 * @author zrlw
 */
@Component
public class TomcatWebServerAccessLogCustomizer implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {
    @Value("${server.tomcat.accesslog.max-days:-1}")
    int accessLogMaxDays;
    
    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        Collection<Valve> engineValves = factory.getEngineValves();
        for (Valve tempObject : engineValves) {
            if (tempObject instanceof AccessLogValve) {
                AccessLogValve accessLogValve = (AccessLogValve) tempObject;
                accessLogValve.setMaxDays(accessLogMaxDays);
            }
        }
    }
}
