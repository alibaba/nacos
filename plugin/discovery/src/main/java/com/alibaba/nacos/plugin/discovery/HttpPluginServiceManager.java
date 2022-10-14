/*
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
 */

package com.alibaba.nacos.plugin.discovery;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.plugin.discovery.spi.HttpPluginService;
import com.alibaba.nacos.plugin.discovery.wapper.HttpServletWapper;
import org.springframework.beans.BeansException;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Http Service Manager.
 *
 * @author karsonto
 */
public class HttpPluginServiceManager implements ServletContextInitializer, ApplicationContextAware {
    
    private Map<String, HttpPluginService> httpServiceContainer = new ConcurrentHashMap<>(4);
    
    private ServletContext servletContext;
    
    @Override
    public void onStartup(ServletContext servletContext) throws ServletException {
        this.servletContext = servletContext;
        httpServiceContainer.forEach((uri, httpService) -> {
            ServletRegistration.Dynamic dynamic = servletContext.addServlet(httpService.getClass().getSimpleName(),
                    new HttpServletWapper(httpService));
            dynamic.addMapping(httpService.getRequestUri());
        });
        
    }
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        Collection<HttpPluginService> httpServiceCollection = NacosServiceLoader.load(HttpPluginService.class);
        for (HttpPluginService httpService : httpServiceCollection) {
            if (httpService.enable()) {
                httpService.init(applicationContext);
                httpServiceContainer.put(httpService.getRequestUri(), httpService);
            }
            
        }
        
    }
}
