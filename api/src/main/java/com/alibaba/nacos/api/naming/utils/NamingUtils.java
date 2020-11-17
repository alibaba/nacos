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

package com.alibaba.nacos.api.naming.utils;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.utils.StringUtils;

/**
 * NamingUtils.
 *
 * @author nkorange
 * @since 1.0.0
 */
public class NamingUtils {
    
    public static String getGroupedName(final String serviceName, final String groupName) {
        if (StringUtils.isBlank(serviceName)) {
            throw new IllegalArgumentException("Param 'serviceName' is illegal, serviceName is blank");
        }
        final String resultGroupedName = groupName + Constants.SERVICE_INFO_SPLITER + serviceName;
        return resultGroupedName.intern();
    }
    
    public static String getServiceName(final String serviceNameWithGroup) {
        if (StringUtils.isBlank(serviceNameWithGroup)) {
            return StringUtils.EMPTY;
        }
        if (!serviceNameWithGroup.contains(Constants.SERVICE_INFO_SPLITER)) {
            return serviceNameWithGroup;
        }
        return serviceNameWithGroup.split(Constants.SERVICE_INFO_SPLITER)[1];
    }
    
    public static String getGroupName(final String serviceNameWithGroup) {
        if (StringUtils.isBlank(serviceNameWithGroup)) {
            return StringUtils.EMPTY;
        }
        if (!serviceNameWithGroup.contains(Constants.SERVICE_INFO_SPLITER)) {
            return Constants.DEFAULT_GROUP;
        }
        return serviceNameWithGroup.split(Constants.SERVICE_INFO_SPLITER)[0];
    }
    
    /**
     * check combineServiceName format. the serviceName can't be blank.
     * <pre>
     * serviceName = "@@"; the length = 0; illegal
     * serviceName = "group@@"; the length = 1; illegal
     * serviceName = "@@serviceName"; the length = 2; legal
     * serviceName = "group@@serviceName"; the length = 2; legal
     * </pre>
     *
     * @param combineServiceName such as: groupName@@serviceName
     */
    public static void checkServiceNameFormat(String combineServiceName) {
        String[] split = combineServiceName.split(Constants.SERVICE_INFO_SPLITER);
        if (split.length <= 1) {
            throw new IllegalArgumentException(
                    "Param 'serviceName' is illegal, it should be format as 'groupName@@serviceName'");
        }
    }
}
