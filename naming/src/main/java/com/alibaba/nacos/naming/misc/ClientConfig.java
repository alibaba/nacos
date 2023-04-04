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

package com.alibaba.nacos.naming.misc;

import com.alibaba.nacos.core.config.AbstractDynamicConfig;
import com.alibaba.nacos.naming.constants.ClientConstants;
import com.alibaba.nacos.sys.env.EnvUtil;

/**
 * Naming client config.
 *
 * @author xiweng.yy
 */
public class ClientConfig extends AbstractDynamicConfig {
    
    private static final String NAMING_CLIENT = "NamingClient";
    
    private static final ClientConfig INSTANCE = new ClientConfig();
    
    private long clientExpiredTime = ClientConstants.DEFAULT_CLIENT_EXPIRED_TIME;
    
    private ClientConfig() {
        super(NAMING_CLIENT);
        resetConfig();
    }
    
    public static ClientConfig getInstance() {
        return INSTANCE;
    }
    
    public long getClientExpiredTime() {
        return clientExpiredTime;
    }
    
    public void setClientExpiredTime(long clientExpiredTime) {
        this.clientExpiredTime = clientExpiredTime;
    }
    
    @Override
    protected void getConfigFromEnv() {
        clientExpiredTime = EnvUtil.getProperty(ClientConstants.CLIENT_EXPIRED_TIME_CONFIG_KEY, Long.class,
                ClientConstants.DEFAULT_CLIENT_EXPIRED_TIME);
    }
    
    @Override
    protected String printConfig() {
        return "ClientConfig{" + "clientExpiredTime=" + clientExpiredTime + '}';
    }
}
