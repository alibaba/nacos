/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.console;

import com.alibaba.nacos.core.web.NacosWebBean;
import com.alibaba.nacos.sys.env.Constants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * nacos console path filter config.
 * @author cxhello
 * @date 2025/7/24
 */
@Configuration
@NacosWebBean
public class ConsolePathTipConfig {
    
    @Bean
    @ConditionalOnProperty(name = Constants.NACOS_DEPLOYMENT_TYPE, havingValue = Constants.NACOS_DEPLOYMENT_TYPE_MERGED, matchIfMissing = true)
    public FilterRegistrationBean<NacosConsolePathTipFilter> nacosConsolePathTipFilterRegistration() {
        FilterRegistrationBean<NacosConsolePathTipFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new NacosConsolePathTipFilter());
        registration.addUrlPatterns("/*");
        registration.setName("nacosConsolePathTipFilter");
        registration.setOrder(7);
        return registration;
    }
    
}
