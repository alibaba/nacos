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
import com.alibaba.nacos.core.remote.control.TpsControl;
import com.alibaba.nacos.core.remote.control.TpsControlConfig;
import com.alibaba.nacos.core.remote.control.TpsMonitorManager;
import com.alibaba.nacos.core.remote.control.TpsMonitorPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * RequestHandlerRegistry.
 *
 * @author liuzunfei
 * @version $Id: RequestHandlerRegistry.java, v 0.1 2020年07月13日 8:24 PM liuzunfei Exp $
 */

@Service
public class RequestHandlerRegistry implements ApplicationListener<ContextRefreshedEvent> {
    
    Map<String, RequestHandler> registryHandlers = new HashMap<String, RequestHandler>();
    
    @Autowired
    private TpsMonitorManager tpsMonitorManager;
    
    /**
     * Get Request Handler By request Type.
     *
     * @param requestType see definitions  of sub constants classes of RequestTypeConstants
     * @return request handler.
     */
    public RequestHandler getByRequestType(String requestType) {
        return registryHandlers.get(requestType);
    }
    
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // 获取RequestHandler抽象类的全部实现类
        Map<String, RequestHandler> beansOfType = event.getApplicationContext().getBeansOfType(RequestHandler.class);
        Collection<RequestHandler> values = beansOfType.values();
        for (RequestHandler requestHandler : values) {
            Class<?> clazz = requestHandler.getClass();
            boolean skip = false;
            // 筛选出RequestHandler抽象类被动态代理,导致clazz不是原本的Class对象,
            // 从而在后面使用反射出现未知错误
            while (!clazz.getSuperclass().equals(RequestHandler.class)) {
                if (clazz.getSuperclass().equals(Object.class)) {
                    skip = true;
                    break;
                }
                // 获取被动态代理类的原Class对象
                clazz = clazz.getSuperclass();
            }
            if (skip) {
                continue;
            }
            
            try {
                // 获取handle()方法的反射信息
                Method method = clazz.getMethod("handle", Request.class, RequestMeta.class);
                // 处理@TpsControl注解,将解析得到的注解信息缓存到TpsMonitorManager.points属性中
                if (method.isAnnotationPresent(TpsControl.class) && TpsControlConfig.isTpsControlEnabled()) {
                    TpsControl tpsControl = method.getAnnotation(TpsControl.class);
                    String pointName = tpsControl.pointName();
                    TpsMonitorPoint tpsMonitorPoint = new TpsMonitorPoint(pointName);
                    tpsMonitorManager.registerTpsControlPoint(tpsMonitorPoint);
                }
            } catch (Exception e) {
                //ignore.
            }
            // 获取RequestHandler的第一个泛型的Class类型
            Class tClass = (Class) ((ParameterizedType) clazz.getGenericSuperclass()).getActualTypeArguments()[0];
            // 将请求处理器的类名和请求处理器的映射关系缓存到registryHandlers中,通过request对象中的type类型查询得到不同的请求处理器,执行对应的业务逻辑
            registryHandlers.putIfAbsent(tClass.getSimpleName(), requestHandler);
        }
    }
}
