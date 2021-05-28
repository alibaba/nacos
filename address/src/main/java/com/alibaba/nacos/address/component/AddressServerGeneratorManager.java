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

package com.alibaba.nacos.address.component;

import com.alibaba.nacos.address.constant.AddressServerConstants;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.naming.core.Instance;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * will generator some result by the input parameter.
 *
 * @author pbting
 * @date 2019-07-01 8:53 PM
 * @since 1.1.0
 */
@Component
public class AddressServerGeneratorManager {
    
    /**
     * Generate product name.
     *
     * @param name name
     * @return product
     */
    public String generateProductName(String name) {
        
        if (StringUtils.isBlank(name) || AddressServerConstants.DEFAULT_PRODUCT.equals(name)) {
            
            return AddressServerConstants.ALIWARE_NACOS_DEFAULT_PRODUCT_NAME;
        }
        
        return String.format(AddressServerConstants.ALIWARE_NACOS_PRODUCT_DOM_TEMPLATE, name);
    }
    
    /**
     * Note: if the parameter inputted is empty then will return the empty list.
     *
     * @param serviceName service name
     * @param clusterName cluster name
     * @param ipArray array of ips
     * @return instance list
     */
    public List<Instance> generateInstancesByIps(String serviceName, String rawProductName, String clusterName,
            String[] ipArray) {
        if (StringUtils.isEmpty(serviceName) || StringUtils.isEmpty(clusterName) || ipArray == null
                || ipArray.length == 0) {
            return Collections.emptyList();
        }
        
        List<Instance> instanceList = new ArrayList<>(ipArray.length);
        for (String ip : ipArray) {
            String[] ipAndPort = generateIpAndPort(ip);
            Instance instance = new Instance();
            instance.setIp(ipAndPort[0]);
            instance.setPort(Integer.parseInt(ipAndPort[1]));
            instance.setClusterName(clusterName);
            instance.setServiceName(serviceName);
            instance.setTenant(Constants.DEFAULT_NAMESPACE_ID);
            instance.setApp(rawProductName);
            instance.setEphemeral(false);
            instanceList.add(instance);
        }
        
        return instanceList;
    }
    
    private String[] generateIpAndPort(String ip) {
        String[] result = InternetAddressUtil.splitIPPortStr(ip);
        if (result.length != InternetAddressUtil.SPLIT_IP_PORT_RESULT_LENGTH) {
            return new String[] {result[0], String.valueOf(AddressServerConstants.DEFAULT_SERVER_PORT)};
        }
        return result;
    }
    
    /**
     * Generate response ips.
     *
     * @param instanceList a instance set will generate string response to client.
     * @return the result of response to client
     */
    public String generateResponseIps(List<Instance> instanceList) {
        
        StringBuilder ips = new StringBuilder();
        instanceList.forEach(instance -> {
            ips.append(instance.getIp()).append(":").append(instance.getPort());
            ips.append("\n");
        });
        
        return ips.toString();
    }
    
    /**
     * Generate nacos service name.
     *
     * @param rawServiceName the raw service name will not contains the {@link Constants#DEFAULT_GROUP}.
     * @return the nacos service name
     */
    public String generateNacosServiceName(String rawServiceName) {
        
        if (rawServiceName.contains(Constants.DEFAULT_GROUP)) {
            return rawServiceName;
        }
        
        return Constants.DEFAULT_GROUP + AddressServerConstants.GROUP_SERVICE_NAME_SEP + rawServiceName;
    }
}
