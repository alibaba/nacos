/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.nacos.core.context;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.PropertySource;

/**
 * An {@link ApplicationContextInitializer} implementation is used to append
 * Nacos default {@link org.springframework.core.env.PropertySource} via annotating {@link PropertySource @PropertySource}
 *
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 * @since 0.2.2
 */
@PropertySource(name = "nacos-default", value = "classpath:/META-INF/nacos-default.properties", encoding = "UTF-8")
public class NacosDefaultPropertySourceApplicationContextInitializer implements
        ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static final String BEAN_NAME = "nacosDefaultPropertySource";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {

        ConfigurableBeanFactory beanFactory = applicationContext.getBeanFactory();

        if (!beanFactory.containsSingleton(BEAN_NAME)) { // If current bean is absent, will be registered
            beanFactory.registerSingleton(BEAN_NAME, this);
        }

    }
}
