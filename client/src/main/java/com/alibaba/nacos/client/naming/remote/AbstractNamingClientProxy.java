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

package com.alibaba.nacos.client.naming.remote;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.client.config.impl.SpasAdapter;
import com.alibaba.nacos.client.naming.event.ServerListChangedEvent;
import com.alibaba.nacos.client.naming.utils.SignUtil;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.client.utils.AppNameUtils;
import com.alibaba.nacos.client.utils.TemplateUtils;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;

/**
 * Abstract Naming client proxy.
 *
 * @author xiweng.yy
 */
public abstract class AbstractNamingClientProxy extends Subscriber<ServerListChangedEvent>
        implements NamingClientProxy {
    
    private static final String APP_FILED = "app";
    
    private static final String SIGNATURE_FILED = "signature";
    
    private static final String DATA_FILED = "data";
    
    private static final String AK_FILED = "ak";
    
    private static final String SEPARATOR = "@@";
    
    private final SecurityProxy securityProxy;
    
    private final Properties properties;
    
    protected AbstractNamingClientProxy(SecurityProxy securityProxy, Properties properties) {
        this.securityProxy = securityProxy;
        this.properties = properties;
    }
    
    /**
     * Get nacos security headers.
     *
     * @return nacos security access token
     */
    protected Map<String, String> getSecurityHeaders() {
        Map<String, String> result = new HashMap<>(1);
        if (StringUtils.isNotBlank(securityProxy.getAccessToken())) {
            result.put(Constants.ACCESS_TOKEN, securityProxy.getAccessToken());
        }
        return result;
    }
    
    /**
     * Get ak/sk if exist.
     *
     * @param serviceName service Name.
     * @return Ak Sk headers.
     */
    protected Map<String, String> getSpasHeaders(String serviceName) {
        Map<String, String> result = new HashMap<>(2);
        String ak = getAccessKey();
        String sk = getSecretKey();
        result.put(APP_FILED, AppNameUtils.getAppName());
        if (StringUtils.isNotBlank(ak) && StringUtils.isNotBlank(sk)) {
            try {
                String signData = getSignData(serviceName);
                String signature = SignUtil.sign(signData, sk);
                result.put(SIGNATURE_FILED, signature);
                result.put(DATA_FILED, signData);
                result.put(AK_FILED, ak);
            } catch (Exception e) {
                NAMING_LOGGER.error("inject ak/sk failed.", e);
            }
        }
        return result;
    }
    
    private String getAccessKey() {
        if (properties == null) {
            return SpasAdapter.getAk();
        }
        return TemplateUtils
                .stringEmptyAndThenExecute(properties.getProperty(PropertyKeyConst.ACCESS_KEY), SpasAdapter::getAk);
    }
    
    private String getSecretKey() {
        if (properties == null) {
            return SpasAdapter.getSk();
        }
        return TemplateUtils
                .stringEmptyAndThenExecute(properties.getProperty(PropertyKeyConst.SECRET_KEY), SpasAdapter::getSk);
    }
    
    private String getSignData(String serviceName) {
        return StringUtils.isNotEmpty(serviceName) ? System.currentTimeMillis() + SEPARATOR + serviceName
                : String.valueOf(System.currentTimeMillis());
    }
}
