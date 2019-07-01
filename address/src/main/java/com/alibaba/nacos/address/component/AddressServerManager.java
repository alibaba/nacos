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
import com.alibaba.nacos.naming.consistency.persistent.raft.LeaderElectFinishedEvent;
import com.alibaba.nacos.naming.consistency.persistent.raft.MakeLeaderEvent;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.misc.SwitchEntry;
import com.alibaba.nacos.naming.misc.SwitchManager;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * This class holds the IP list of the CAI's address service.
 *
 * @author deshao
 * @date 2016/4/28 20:58
 */
@Component
public class AddressServerManager implements ApplicationListener<ApplicationEvent> {

    private static final Logger logger = LoggerFactory.getLogger(AddressServerManager.class);

    private ServiceManager serviceManager;

    private AddressServerGeneratorManager addressServerBuilderManager;

    private SwitchManager switchManager;

    /**
     * @param serviceManager
     */
    public AddressServerManager(ServiceManager serviceManager,
                                AddressServerGeneratorManager addressServerBuilderManager,
                                SwitchManager switchManager) {
        this.serviceManager = serviceManager;
        this.addressServerBuilderManager = addressServerBuilderManager;
        this.switchManager = switchManager;
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {

        if (event instanceof LeaderElectFinishedEvent ||
            event instanceof MakeLeaderEvent) {
            // will handler the leader elect finished and follower make leader event
            try {
                switchManager.update(SwitchEntry.CHECK, "false", false);
            } catch (Exception e) {
                logger.error("update the health check cause an exception.", e);
            }
        }
    }

    /**
     * @param name
     * @return
     */
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

    /**
     * @param name
     * @return
     */
    public String getRawClusterName(String name) {

        return getDefaultClusterNameIfEmpty(name);
    }

    /**
     * @param ips multi ip will separator by the ';'
     * @return
     */
    public String[] splitIps(String ips) {

        if (StringUtils.isBlank(ips)) {

            return new String[0];
        }

        return ips.split(AddressServerConstants.MULTI_IPS_SEPARATOR);
    }

    /**
     * @param rawServiceName
     * @return
     */
    public Service createServiceIfEmpty(String rawServiceName) throws Exception {

        String serviceName = addressServerBuilderManager.generateNacosServiceName(rawServiceName);
        Service service = serviceManager.getService(AddressServerConstants.DEFAULT_NAMESPACE, serviceName);
        if (service != null) {
            return service;
        }

        service = serviceManager.getService(AddressServerConstants.DEFAULT_NAMESPACE, serviceName);
        if (service == null) {
            synchronized (this) {
                service = new Service(serviceName);
                service.setNamespaceId(AddressServerConstants.DEFAULT_NAMESPACE);
                service.setGroupName(AddressServerConstants.DEFAULT_GROUP);
                serviceManager.addOrReplaceService(service);
            }
        }

        return service;
    }

}
