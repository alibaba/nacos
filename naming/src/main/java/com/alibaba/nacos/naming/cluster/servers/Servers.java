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
package com.alibaba.nacos.naming.cluster.servers;

import com.alibaba.nacos.common.util.Md5Utils;
import com.alibaba.nacos.naming.pojo.Record;

import java.util.List;

/**
 * @author XCXCXCXCX
 * @since 1.0
 */
public class Servers implements Record {

    private List<String> clusterHosts;

    public Servers(List<String> clusterHosts) {
        this.clusterHosts = clusterHosts;
    }

    public List<String> getClusterHosts() {
        return clusterHosts;
    }

    public void setClusterHosts(List<String> clusterHosts) {
        this.clusterHosts = clusterHosts;
    }

    /**
     * get the checksum of this record, usually for record comparison
     *
     * @return checksum of record
     */
    @Override
    public String getChecksum() {
        return Md5Utils.getMD5(clusterHosts.toString(), "UTF-8");
    }

    @Override
    public String toString() {
        return "Servers{" +
            "clusterHosts=" + clusterHosts +
            '}';
    }
}
