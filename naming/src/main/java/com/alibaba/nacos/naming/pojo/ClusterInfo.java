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

package com.alibaba.nacos.naming.pojo;

import java.io.Serializable;
import java.util.List;

/**
 * Cluster info.
 *
 * @author caogu.wyp
 * @version $Id: ClusterInfo.java, v 0.1 2018-09-17 上午11:36 caogu.wyp Exp $$
 */
public class ClusterInfo implements Serializable {
    
    private List<IpAddressInfo> hosts;
    
    /**
     * Getter method for property <tt>hosts</tt>.
     *
     * @return property value of hosts
     */
    public List<IpAddressInfo> getHosts() {
        return hosts;
    }
    
    /**
     * Setter method for property <tt>hosts </tt>.
     *
     * @param hosts value to be assigned to property hosts
     */
    public void setHosts(List<IpAddressInfo> hosts) {
        this.hosts = hosts;
    }
}
