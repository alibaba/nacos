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

package com.alibaba.nacos.core.distributed.distro.core;

import com.alibaba.nacos.core.cluster.Node;
import com.alibaba.nacos.core.cluster.NodeChangeEvent;
import com.alibaba.nacos.core.cluster.NodeChangeListener;
import com.alibaba.nacos.core.cluster.NodeManager;
import com.alibaba.nacos.core.distributed.distro.DistroConfig;
import com.alibaba.nacos.core.distributed.distro.DistroSysConstants;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.SpringUtils;
import com.alibaba.nacos.core.utils.SystemUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Supplier;

/**
 * // TODO 是否需要上升到 NACOS AP 一致性协议的自定义实现的强制规范
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class DistroMapper implements NodeChangeListener {

    private volatile List<String> healthyList = new ArrayList<>();

    private final NodeManager nodeManager;

    private final DistroConfig distroConfig;

    private volatile boolean isDistroEnabled = false;

    private final List<KeyAnalysis> keyAnalyses;

    public DistroMapper(NodeManager nodeManager, DistroConfig config) {
        this.nodeManager = nodeManager;
        this.distroConfig = config;
        this.isDistroEnabled = Boolean.parseBoolean(
                config.getValOfDefault(DistroSysConstants.DISTRO_ENABLED, "true")
        );

        List<KeyAnalysis> tmp = new ArrayList<>();

        // discovery by java spi

        ServiceLoader<KeyAnalysis> loader = ServiceLoader.load(KeyAnalysis.class);

        for (KeyAnalysis keyAnalysis : loader) {
            tmp.add(keyAnalysis);
        }

        // discovery by spring ioc

        try {
            tmp.addAll(SpringUtils.getBeansOfType(KeyAnalysis.class).values());
        } catch (Exception ignore) {

        }

        // end

        keyAnalyses = Collections.unmodifiableList(tmp);
    }

    /**
     * Distro calling function provided to the business party, passing
     * in custom rule Supplier and key information
     *
     * @param key origin key
     * @param suppliers customer distro rules
     * @return can distro
     */
    public boolean responsibleByCustomerRule(String key, Supplier<Boolean>... suppliers) {

        boolean customerResult = true;

        if (suppliers != null) {
            boolean[] booleans = new boolean[suppliers.length];
            int index = 0;
            for (Supplier<Boolean> supplier : suppliers) {
                booleans[index ++] = supplier.get();
            }
            customerResult = BooleanUtils.and(booleans);
        }

        return customerResult && responsible(key);
    }

    public boolean responsible(String key) {

        for (KeyAnalysis keyAnalysis : keyAnalyses) {
            if (keyAnalysis.interest(key)) {
                key = keyAnalysis.analyze(key);
                break;
            }
        }

        final String selfAddress = nodeManager.self().address();

        if (!isDistroEnabled || SystemUtils.STANDALONE_MODE) {
            return true;
        }

        if (CollectionUtils.isEmpty(healthyList)) {
            // means distro config is not ready yet
            return false;
        }

        int index = healthyList.indexOf(selfAddress);
        int lastIndex = healthyList.lastIndexOf(selfAddress);
        if (lastIndex < 0 || index < 0) {
            return true;
        }

        int target = distroHash(key) % healthyList.size();
        return target >= index && target <= lastIndex;
    }

    public String mapSrv(String serviceName) {

        final String self = nodeManager.self().address();

        if (CollectionUtils.isEmpty(healthyList) || !isDistroEnabled) {
            return self;
        }

        try {
            return healthyList.get(distroHash(serviceName) % healthyList.size());
        } catch (Exception e) {
            Loggers.DISTRO.warn("distro mapper failed, return localhost: " + self, e);

            return self;
        }
    }

    public int distroHash(String serviceName) {
        return Math.abs(serviceName.hashCode() % Integer.MAX_VALUE);
    }

    @Override
    public void onEvent(NodeChangeEvent event) {
        List<String> newHealthyList = new ArrayList<>();
        for (Node node : event.getAllNodes()) {
            newHealthyList.add(node.address());
        }
        this.healthyList = newHealthyList;
    }
}
