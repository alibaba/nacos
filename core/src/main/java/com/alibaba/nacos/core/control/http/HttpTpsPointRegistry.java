/*
 *
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.core.control.http;

import com.alibaba.nacos.core.control.TpsControl;
import com.alibaba.nacos.core.control.TpsControlConfig;
import com.alibaba.nacos.plugin.control.ControlManagerCenter;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * RequestHandlerRegistry.
 *
 * @author liuzunfei
 * @version $Id: RequestHandlerRegistry.java, v 0.1 2020年07月13日 8:24 PM liuzunfei Exp $
 */
@Service
public class HttpTpsPointRegistry implements ApplicationListener<ContextRefreshedEvent> {
    
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        RequestMappingHandlerMapping requestMapping = event.getApplicationContext()
                .getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = requestMapping.getHandlerMethods();
        for (HandlerMethod handlerMethod : handlerMethods.values()) {
            Method method = handlerMethod.getMethod();
            if (method.isAnnotationPresent(TpsControl.class) && TpsControlConfig.isTpsControlEnabled()) {
                TpsControl tpsControl = method.getAnnotation(TpsControl.class);
                String pointName = tpsControl.pointName();
                ControlManagerCenter.getInstance().getTpsControlManager().registerTpsPoint(pointName);
            }
        }
    }
}
