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
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * A default implementation for service/cluster upgrade/downgrade.
 * @author gengtuo.ygt
 * on 2021/2/25
 */
@Component
@ConditionalOnMissingBean(ServiceMetadataUpgradeHelper.class)
public class DefaultServiceMetadataUpgradeHelper implements ServiceMetadataUpgradeHelper {

    @Override
    public Service toV1Service(Service v1, com.alibaba.nacos.naming.core.v2.pojo.Service v2, ServiceMetadata v2meta) {
        if (null == v1) {
            v1 = new Service(v2.getGroupedServiceName());
            v1.setGroupName(v2.getGroup());
            v1.setNamespaceId(v2.getNamespace());
        }
        v1.setSelector(v2meta.getSelector());
        v1.setProtectThreshold(v2meta.getProtectThreshold());
        v1.setMetadata(v2meta.getExtendData());
        for (Map.Entry<String, ClusterMetadata> entry : v2meta.getClusters().entrySet()) {
            if (!v1.getClusterMap().containsKey(entry.getKey())) {
                v1.addCluster(toV1Cluster(new Cluster(entry.getKey(), v1), entry.getValue()));
            } else {
                toV1Cluster(v1.getClusterMap().get(entry.getKey()), entry.getValue());
            }
        }
        return v1;
    }

    @Override
    public Cluster toV1Cluster(Cluster v1, ClusterMetadata v2meta) {
        v1.setDefCkport(v2meta.getHealthyCheckPort());
        v1.setUseIPPort4Check(v2meta.isUseInstancePortForCheck());
        v1.setHealthChecker(v2meta.getHealthChecker());
        v1.setMetadata(v2meta.getExtendData());
        return v1;
    }

    @Override
    public ServiceMetadata toV2ServiceMetadata(Service service, boolean ephemeral) {
        ServiceMetadata result = new ServiceMetadata();
        result.setEphemeral(ephemeral);
        result.setProtectThreshold(service.getProtectThreshold());
        result.setSelector(service.getSelector());
        result.setExtendData(service.getMetadata());
        for (Map.Entry<String, Cluster> entry : service.getClusterMap().entrySet()) {
            result.getClusters().put(entry.getKey(), toV2ClusterMetadata(entry.getValue()));
        }
        return result;
    }

    @Override
    public ClusterMetadata toV2ClusterMetadata(Cluster v1) {
        ClusterMetadata result = new ClusterMetadata();
        result.setHealthyCheckPort(v1.getDefCkport());
        result.setUseInstancePortForCheck(v1.isUseIPPort4Check());
        result.setExtendData(v1.getMetadata());
        result.setHealthChecker(v1.getHealthChecker());
        result.setHealthyCheckType(v1.getHealthChecker().getType());
        return result;
    }
}
