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

package com.alibaba.nacos.client.auth.ram.injector;

import com.alibaba.nacos.client.auth.LoginIdentityContext;
import com.alibaba.nacos.client.auth.ram.RamContext;
import com.alibaba.nacos.client.auth.spi.RequestResource;
import com.alibaba.nacos.client.naming.utils.SignUtil;
import com.alibaba.nacos.common.utils.StringUtils;

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;

/**
 * Resource Injector for naming module.
 *
 * @author xiweng.yy
 */
public class NamingResourceInjector extends AbstractResourceInjector {
    
    private static final String SEPARATOR = "@@";
    
    private static final String SIGNATURE_FILED = "signature";
    
    private static final String DATA_FILED = "data";
    
    private static final String AK_FILED = "ak";
    
    @Override
    public void doInject(RequestResource resource, RamContext context, LoginIdentityContext result) {
        if (context.validate()) {
            try {
                String signData = getSignData(resource.getResource());
                String signature = SignUtil.sign(signData, context.getSecretKey());
                result.setParameter(SIGNATURE_FILED, signature);
                result.setParameter(DATA_FILED, signData);
                result.setParameter(AK_FILED, context.getAccessKey());
            } catch (Exception e) {
                NAMING_LOGGER.error("inject ak/sk failed.", e);
            }
        }
    }
    
    private String getSignData(String serviceName) {
        return StringUtils.isNotEmpty(serviceName) ? System.currentTimeMillis() + SEPARATOR + serviceName
                : String.valueOf(System.currentTimeMillis());
    }
}
