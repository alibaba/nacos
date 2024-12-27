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

package com.alibaba.nacos.client.auth.ram;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.auth.ram.identify.StsConfig;
import com.alibaba.nacos.client.auth.ram.injector.AbstractResourceInjector;
import com.alibaba.nacos.client.auth.ram.injector.ConfigResourceInjector;
import com.alibaba.nacos.client.auth.ram.injector.NamingResourceInjector;
import com.alibaba.nacos.client.auth.ram.utils.RamUtil;
import com.alibaba.nacos.client.auth.ram.utils.SpasAdapter;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.api.LoginIdentityContext;
import com.alibaba.nacos.plugin.auth.api.RequestResource;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.auth.spi.client.AbstractClientAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Client Auth service implementation for aliyun RAM.
 *
 * @author xiweng.yy
 */
public class RamClientAuthServiceImpl extends AbstractClientAuthService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(RamClientAuthServiceImpl.class);
    
    private final RamContext ramContext;
    
    private final Map<String, AbstractResourceInjector> resourceInjectors;
    
    public RamClientAuthServiceImpl() {
        ramContext = new RamContext();
        resourceInjectors = new HashMap<>();
        resourceInjectors.put(SignType.NAMING, new NamingResourceInjector());
        resourceInjectors.put(SignType.CONFIG, new ConfigResourceInjector());
    }
    
    @Override
    public Boolean login(Properties properties) {
        if (ramContext.validate()) {
            return true;
        }
        loadRoleName(properties);
        loadAccessKey(properties);
        loadSecretKey(properties);
        loadRegionId(properties);
        return true;
    }
    
    private void loadRoleName(Properties properties) {
        String ramRoleName = properties.getProperty(PropertyKeyConst.RAM_ROLE_NAME);
        if (!StringUtils.isBlank(ramRoleName)) {
            StsConfig.getInstance().setRamRoleName(ramRoleName);
            ramContext.setRamRoleName(ramRoleName);
        }
    }
    
    private void loadAccessKey(Properties properties) {
        ramContext.setAccessKey(RamUtil.getAccessKey(properties));
    }
    
    private void loadSecretKey(Properties properties) {
        ramContext.setSecretKey(RamUtil.getSecretKey(properties));
    }
    
    private void loadRegionId(Properties properties) {
        String regionId = properties.getProperty(PropertyKeyConst.SIGNATURE_REGION_ID);
        ramContext.setRegionId(regionId);
    }
    
    @Override
    public LoginIdentityContext getLoginIdentityContext(RequestResource resource) {
        LoginIdentityContext result = new LoginIdentityContext();
        if (!ramContext.validate() || notFountInjector(resource.getType())) {
            return result;
        }
        resourceInjectors.get(resource.getType()).doInject(resource, ramContext, result);
        return result;
    }
    
    private boolean notFountInjector(String type) {
        if (!resourceInjectors.containsKey(type)) {
            LOGGER.warn("Injector for type {} not found, will use default ram identity context.", type);
            return true;
        }
        return false;
    }
    
    @Override
    public void shutdown() throws NacosException {
        SpasAdapter.freeCredentialInstance();
    }
}
