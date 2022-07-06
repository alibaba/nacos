/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.env;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Properties;

class NacosEnvironmentFactory {
    
    /**
     * create nacos environment.
     * @return NacosEnvironment's proxy object, it contains a SearchableEnvironment object.
     * @see SearchableEnvironment
     */
    static NacosEnvironment createEnvironment() {
        
        return (NacosEnvironment) Proxy.newProxyInstance(NacosEnvironmentFactory.class.getClassLoader(), new Class[] {NacosEnvironment.class},
                new NacosEnvironmentDelegate() {
                    volatile NacosEnvironment environment;
                
                    @Override
                    public void init(Properties properties) {
                        if (environment == null) {
                            synchronized (NacosEnvironmentFactory.class) {
                                if (environment == null) {
                                    environment = new SearchableEnvironment(properties);
                                }
                            }
                        }
                    }
                
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (environment == null) {
                            throw new IllegalStateException(
                                    "Nacos environment doesn't init, please call NacosEnvs#init method then try it again.");
                        }
                        return method.invoke(environment, args);
                    }
                });
    }
    
    interface NacosEnvironmentDelegate extends InvocationHandler {
    
        /**
         * init environment.
         * @param properties user customize properties
         */
        void init(Properties properties);
    }
    
}
