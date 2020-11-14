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

package com.alibaba.nacos.address.constant;

import com.alibaba.nacos.naming.misc.UtilsAndCommons;

/**
 * Uniform constant parameter naming for address servers and default values ​​for related parameters.
 *
 * @author pbting
 * @date 2019-06-17 7:23 PM
 * @since 1.1.0
 */
public interface AddressServerConstants {
    
    
    /**
     * the default server port when create the Instance object.
     */
    int DEFAULT_SERVER_PORT = 8848;
    
    /**
     * when post ips is not given the product,then use the default.
     */
    String DEFAULT_PRODUCT = "nacos";
    
    /**
     * the separator for service name between raw service name and group.
     */
    String GROUP_SERVICE_NAME_SEP = "@@";
    
    /**
     * when post ips is not given the cluster,then use the default.
     */
    String DEFAULT_GET_CLUSTER = "serverlist";
    
    /**
     * post multi ip will use the "," to separator.
     */
    String MULTI_IPS_SEPARATOR = ",";
    
    /**
     * the default product name when deploy nacos with naming and config.
     */
    String ALIWARE_NACOS_DEFAULT_PRODUCT_NAME = "nacos.as.default";
    
    /**
     * when the config and naming will separate deploy,then must specify product name by the client.
     */
    String ALIWARE_NACOS_PRODUCT_DOM_TEMPLATE = "nacos.as.%s";
    
    /**
     * the url for address server prefix.
     */
    String ADDRESS_SERVER_REQUEST_URL =
            UtilsAndCommons.NACOS_SERVER_CONTEXT + UtilsAndCommons.NACOS_SERVER_VERSION + "/as";
    
}
