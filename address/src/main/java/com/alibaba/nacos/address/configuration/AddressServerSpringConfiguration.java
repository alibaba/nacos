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

package com.alibaba.nacos.address.configuration;

import com.alibaba.nacos.address.auth.AddressServerAuthManager;
import com.alibaba.nacos.auth.AuthManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Address server spring configuration.
 *
 * @author xiweng.yy
 */
@Configuration
public class AddressServerSpringConfiguration {
    
    @Bean
    @ConditionalOnMissingBean(value = AuthManager.class)
    public AuthManager getAuthManager() {
        return new AddressServerAuthManager();
    }
}
