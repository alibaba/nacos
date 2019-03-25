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
package com.alibaba.nacos.naming.web;

import com.alibaba.nacos.naming.controllers.*;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Basic methods for filter to use
 *
 * @author nkorange
 * @since 1.0.0
 */
@Component
public class FilterBase {

    private ConcurrentMap<String, Method> methodCache = new
        ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        initClassMethod(InstanceController.class);
        initClassMethod(ServiceController.class);
        initClassMethod(ClusterController.class);
        initClassMethod(CatalogController.class);
        initClassMethod(HealthController.class);
        initClassMethod(RaftController.class);
        initClassMethod(DistroController.class);
        initClassMethod(OperatorController.class);
        initClassMethod(ApiController.class);
    }

    public Method getMethod(String httpMethod, String path) {
        String key = httpMethod + "-->" + path.replace("/nacos", "");
        return methodCache.get(key);
    }

    private void initClassMethod(Class<?> clazz) {
        RequestMapping requestMapping = clazz.getAnnotation(RequestMapping.class);
        String classPath = requestMapping.value()[0];
        for (Method method : clazz.getMethods()) {
            if (!method.isAnnotationPresent(RequestMapping.class)) {
                continue;
            }
            requestMapping = method.getAnnotation(RequestMapping.class);
            RequestMethod[] requestMethods = requestMapping.method();
            if (requestMethods.length == 0) {
                requestMethods = new RequestMethod[1];
                requestMethods[0] = RequestMethod.GET;
            }
            for (String methodPath : requestMapping.value()) {
                methodCache.put(requestMethods[0].name() + "-->" + classPath + methodPath, method);
            }
        }
    }
}
