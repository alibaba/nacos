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

import com.alibaba.nacos.client.auth.ram.RamConstants;
import com.alibaba.nacos.client.auth.ram.RamContext;
import com.alibaba.nacos.client.auth.ram.identify.IdentifyConstants;
import com.alibaba.nacos.client.auth.ram.identify.StsConfig;
import com.alibaba.nacos.client.auth.ram.identify.StsCredential;
import com.alibaba.nacos.client.auth.ram.identify.StsCredentialHolder;
import com.alibaba.nacos.client.auth.ram.utils.CalculateV4SigningKeyUtil;
import com.alibaba.nacos.client.auth.ram.utils.SpasAdapter;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.api.LoginIdentityContext;
import com.alibaba.nacos.plugin.auth.api.RequestResource;

import java.util.Map;

/**
 * Resource Injector for naming module.
 *
 * @author xiweng.yy
 */
public class ConfigResourceInjector extends AbstractResourceInjector {
    
    private static final String ACCESS_KEY_HEADER = "Spas-AccessKey";
    
    private static final String DEFAULT_RESOURCE = "";
    
    @Override
    public void doInject(RequestResource resource, RamContext context, LoginIdentityContext result) {
        String accessKey = context.getAccessKey();
        String secretKey = context.getSecretKey();
        // STS 临时凭证鉴权的优先级高于 AK/SK 鉴权
        if (StsConfig.getInstance().isStsOn()) {
            StsCredential stsCredential = StsCredentialHolder.getInstance().getStsCredential();
            accessKey = stsCredential.getAccessKeyId();
            secretKey = stsCredential.getAccessKeySecret();
            result.setParameter(IdentifyConstants.SECURITY_TOKEN_HEADER, stsCredential.getSecurityToken());
        }
        
        if (StringUtils.isNotEmpty(accessKey) && StringUtils.isNotBlank(secretKey)) {
            result.setParameter(ACCESS_KEY_HEADER, accessKey);
        }
        String signatureKey = secretKey;
        if (StringUtils.isNotEmpty(context.getRegionId())) {
            signatureKey = CalculateV4SigningKeyUtil
                    .finalSigningKeyStringWithDefaultInfo(secretKey, context.getRegionId());
            result.setParameter(RamConstants.SIGNATURE_VERSION, RamConstants.V4);
        }
        Map<String, String> signHeaders = SpasAdapter
                .getSignHeaders(getResource(resource.getNamespace(), resource.getGroup()), signatureKey);
        result.setParameters(signHeaders);
    }
    
    private String getResource(String tenant, String group) {
        if (StringUtils.isNotBlank(tenant) && StringUtils.isNotBlank(group)) {
            return tenant + "+" + group;
        }
        if (StringUtils.isNotBlank(group)) {
            return group;
        }
        if (StringUtils.isNotBlank(tenant)) {
            return tenant;
        }
        return DEFAULT_RESOURCE;
    }
}
