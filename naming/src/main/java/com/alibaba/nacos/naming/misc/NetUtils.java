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

package com.alibaba.nacos.naming.misc;

import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.InetUtils;

/**
 * Net Utils.
 *
 * @author nacos
 */
public class NetUtils {
    
    /**
     * Get local server address.
     *
     * @return local server address
     */
    public static String localServer() {
        return InetUtils.getSelfIP() + InternetAddressUtil.IP_PORT_SPLITER + EnvUtil.getPort();
    }
    
}
