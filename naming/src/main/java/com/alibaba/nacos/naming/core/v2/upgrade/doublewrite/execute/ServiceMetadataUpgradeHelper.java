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

package com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.execute;

import com.alibaba.nacos.naming.core.Cluster;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;

/**
 * Help converting service/cluster and its metadata when upgrading/downgrading.
 *
 * @author gengtuo.ygt
 * on 2021/2/25
 */
public interface ServiceMetadataUpgradeHelper {

    /**
     * Convert to v1 service.
     * 
     * @param v1     service v1, null if not exists, will create a new one
     * @param v2     service v2 without metadata
     * @param v2meta service v2 metadata
     * @return service v1
     */
    Service toV1Service(Service v1, com.alibaba.nacos.naming.core.v2.pojo.Service v2, ServiceMetadata v2meta);

    /**
     * Convert to v1 cluster.
     * 
     * @param v1     cluster v1, null if not exists, will create a new one
     * @param v2meta cluster v2 metadata
     * @return cluster v1
     */
    Cluster toV1Cluster(Cluster v1, ClusterMetadata v2meta);

    /**
     * Convert to v2 service metadata.
     * 
     * @param service   service v1
     * @param ephemeral is ephemeral
     * @return service metadata v2
     */
    ServiceMetadata toV2ServiceMetadata(Service service, boolean ephemeral);

    /**
     * Convert to v2 cluster metadata.
     * 
     * @param v1 cluster v1
     * @return cluster metadata v2
     */
    ClusterMetadata toV2ClusterMetadata(Cluster v1);
}
