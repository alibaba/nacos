/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.aot;

import com.alibaba.nacos.api.model.NacosForm;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Controller;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Type;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Register binding metadata for implicit Nacos HTTP form parameters during AOT processing.
 *
 * <p>Optional properties and inherited accessors must also work when a remote Console supplies
 * parameters that were absent from build-time tracing requests. No controller is instantiated.
 */
public class NacosFormBeanRegistrationAotProcessor implements BeanRegistrationAotProcessor {
    
    @Override
    public BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
        Class<?> beanClass = registeredBean.getBeanClass();
        if (!AnnotatedElementUtils.hasAnnotation(beanClass, Controller.class)) {
            return null;
        }
        Set<Type> forms = new LinkedHashSet<>();
        ReflectionUtils.doWithMethods(beanClass, method -> {
            if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
                return;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            Type[] genericTypes = method.getGenericParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                if (NacosForm.class.isAssignableFrom(parameterTypes[i])) {
                    forms.add(genericTypes[i]);
                }
            }
        });
        if (forms.isEmpty()) {
            return null;
        }
        return (generationContext, beanRegistrationCode) -> new BindingReflectionHintsRegistrar()
            .registerReflectionHints(
                generationContext.getRuntimeHints().reflection(), forms.toArray(Type[]::new));
    }
}
