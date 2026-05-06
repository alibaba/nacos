/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.remote;

import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.core.control.TpsControl;
import com.alibaba.nacos.core.control.TpsControlConfig;
import com.alibaba.nacos.core.remote.grpc.InvokeSource;
import com.alibaba.nacos.plugin.control.ControlManagerCenter;
import com.google.common.collect.Sets;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * RequestHandlerRegistry.
 *
 * @author liuzunfei
 * @version $Id: RequestHandlerRegistry.java, v 0.1 2020年07月13日 8:24 PM liuzunfei Exp $
 */

@Service
public class RequestHandlerRegistry implements ApplicationListener<ContextRefreshedEvent> {
    
    Map<String, RequestHandler> registryHandlers = new HashMap<>();
    
    Map<String, Set<String>> sourceRegistry = new HashMap<>();
    
    /**
     * Get Request Handler By request Type.
     *
     * @param requestType see definitions  of sub constants classes of RequestTypeConstants
     * @return request handler.
     */
    public RequestHandler getByRequestType(String requestType) {
        return registryHandlers.get(requestType);
    }
    
    /**
     * check source invoke allowed.
     *
     * @param type   type.
     * @param source source.
     * @return
     */
    public boolean checkSourceInvokeAllowed(String type, String source) {
        if (sourceRegistry.containsKey(type) && !sourceRegistry.get(type).contains(source)) {
            return false;
        }
        return true;
    }
    
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        Map<String, RequestHandler> beansOfType = event.getApplicationContext().getBeansOfType(RequestHandler.class);
        Collection<RequestHandler> values = beansOfType.values();
        for (RequestHandler requestHandler : values) {
            
            Class<?> clazz = requestHandler.getClass();
            boolean skip = false;
            while (!clazz.getSuperclass().equals(RequestHandler.class)) {
                if (clazz.getSuperclass().equals(Object.class)) {
                    skip = true;
                    break;
                }
                clazz = clazz.getSuperclass();
            }
            if (skip) {
                continue;
            }
            //register tps control.
            try {
                Method method = clazz.getMethod("handle", Request.class, RequestMeta.class);
                if (method.isAnnotationPresent(TpsControl.class) && TpsControlConfig.isTpsControlEnabled()) {
                    TpsControl tpsControl = method.getAnnotation(TpsControl.class);
                    String pointName = tpsControl.pointName();
                    ControlManagerCenter.getInstance().getTpsControlManager().registerTpsPoint(pointName);
                }
            } catch (Exception e) {
                //ignore.
            }
            
            Class tClass = (Class) ((ParameterizedType) clazz.getGenericSuperclass()).getActualTypeArguments()[0];
            
            //register invoke source.
            try {
                if (clazz.isAnnotationPresent(InvokeSource.class)) {
                    InvokeSource tpsControl = clazz.getAnnotation(InvokeSource.class);
                    String[] sources = tpsControl.source();
                    if (sources != null && sources.length > 0) {
                        sourceRegistry.put(tClass.getSimpleName(), Sets.newHashSet(sources));
                    }
                }
            } catch (Exception e) {
                //ignore.
            }
            
            registryHandlers.putIfAbsent(tClass.getSimpleName(), requestHandler);
        }
    }
}
