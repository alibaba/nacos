/*
 * Copyright (C) 2019 the original author or authors.
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
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * This class holds the IP list of the CAI's address service.
 *
 * @author deshao
 * @date 2016/4/28 20:58
 * @since 1.1.0
 */
@Component
public class AddressServerManager {

    public String getRawProductName(String name) {

        if (StringUtils.isBlank(name) || AddressServerConstants.DEFAULT_PRODUCT.equals(name)) {

            return AddressServerConstants.DEFAULT_PRODUCT;
        }

        return name;
    }

    /**
     * <p>
     * if the name is empty then return the default {@UtilAndCommons#DEFAULT_CLUSTER_NAME},
     * <p>
     * or return the source name by input
     *
     * @param name
     * @return
     */
    public String getDefaultClusterNameIfEmpty(String name) {

        if (StringUtils.isEmpty(name) || AddressServerConstants.DEFAULT_GET_CLUSTER.equals(name)) {
            return AddressServerConstants.DEFAULT_GET_CLUSTER;
        }

        return name;
    }

    public String getRawClusterName(String name) {

        return getDefaultClusterNameIfEmpty(name);
    }

    /**
     * @param ips multi ip will separator by the ','
     * @return
     */
    public String[] splitIps(String ips) {

        if (StringUtils.isBlank(ips)) {

            return new String[0];
        }

        return ips.split(AddressServerConstants.MULTI_IPS_SEPARATOR);
    }
}
