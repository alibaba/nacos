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
package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.core.utils.SystemUtils;
import com.alibaba.nacos.naming.cluster.ServerListManager;
import com.alibaba.nacos.naming.cluster.servers.Server;
import com.alibaba.nacos.naming.cluster.servers.ServerChangeListener;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.NetUtils;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nkorange
 */
@Component("distroMapper")
public class DistroMapper implements ServerChangeListener {

    private List<String> healthyList = new ArrayList<>();

    public List<String> getHealthyList() {
        return healthyList;
    }

    @Autowired
    private SwitchDomain switchDomain;

    @Autowired
    private ServerListManager serverListManager;

    /**
     * init server list
     */
    @PostConstruct
    public void init() {
        serverListManager.listen(this);
    }

    public boolean responsible(Cluster cluster, Instance instance) {
        return switchDomain.isHealthCheckEnabled(cluster.getServiceName())
            && !cluster.getHealthCheckTask().isCancelled()
            && responsible(cluster.getServiceName())
            && cluster.contains(instance);
    }

    /**
     * 查看本地服务是否可用
     * @param serviceName
     * @return
     */
    public boolean responsible(String serviceName) {
        if (!switchDomain.isDistroEnabled() || SystemUtils.STANDALONE_MODE) {
            return true;
        }

        if (CollectionUtils.isEmpty(healthyList)) {
            // means distro config is not ready yet
            return false;
        }

        /**
         * 第一次出现的位置
         */
        int index = healthyList.indexOf(NetUtils.localServer());
        /**
         * 最后一次出现的位置
         */
        int lastIndex = healthyList.lastIndexOf(NetUtils.localServer());
        if (lastIndex < 0 || index < 0) {
            return true;
        }

        /**
         * 使用healthyList中的target位置的nacos处理当前请求
         */
        int target = distroHash(serviceName) % healthyList.size();

        /**
         * 通常在healthyList   当前节点只会出现一次
         * 所以只有 target == index == lastIndex时  才会返回true   即用当前节点处理请求
         * 否则使用其他节点处理请求
         */
        return target >= index && target <= lastIndex;
    }

    /**
     * 根据serviceName对应的hash  选择转发的节点
     * @param serviceName
     * @return
     */
    public String mapSrv(String serviceName) {
        if (CollectionUtils.isEmpty(healthyList) || !switchDomain.isDistroEnabled()) {
            return NetUtils.localServer();
        }

        try {
            /**
             * 根据serviceName对应的hash  选择转发的节点
             */
            return healthyList.get(distroHash(serviceName) % healthyList.size());
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("distro mapper failed, return localhost: " + NetUtils.localServer(), e);

            return NetUtils.localServer();
        }
    }

    /**
     * 获取serviceName对应的hash
     * @param serviceName
     * @return
     */
    public int distroHash(String serviceName) {
        return Math.abs(serviceName.hashCode() % Integer.MAX_VALUE);
    }

    @Override
    public void onChangeServerList(List<Server> latestMembers) {

    }

    /**
     * 更新nacos集群内   健康节点列表
     * @param latestReachableMembers
     */
    @Override
    public void onChangeHealthyServerList(List<Server> latestReachableMembers) {

        List<String> newHealthyList = new ArrayList<>();
        for (Server server : latestReachableMembers) {
            newHealthyList.add(server.getKey());
        }
        healthyList = newHealthyList;
    }
}
