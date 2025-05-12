/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.handler.impl.remote;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.console.cluster.RemoteServerMemberManager;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberChangeListener;
import com.alibaba.nacos.core.cluster.MembersChangeEvent;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerFactory;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import com.alibaba.nacos.maintainer.client.config.ConfigMaintainerFactory;
import com.alibaba.nacos.maintainer.client.config.ConfigMaintainerService;
import com.alibaba.nacos.maintainer.client.naming.NamingMaintainerFactory;
import com.alibaba.nacos.maintainer.client.naming.NamingMaintainerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Properties;

/**
 * Nacos maintainer client holder.
 *
 * @author xiweng.yy
 */
@Component
@EnabledRemoteHandler
public class NacosMaintainerClientHolder extends MemberChangeListener {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosMaintainerClientHolder.class);
    
    private final RemoteServerMemberManager memberManager;
    
    private volatile NamingMaintainerService namingMaintainerService;
    
    private volatile ConfigMaintainerService configMaintainerService;
    
    private volatile AiMaintainerService aiMaintainerService;
    
    public NacosMaintainerClientHolder(RemoteServerMemberManager memberManager) throws NacosException {
        this.memberManager = memberManager;
        buildMaintainerService();
        NotifyCenter.registerSubscriber(this);
    }
    
    private void buildMaintainerService() throws NacosException {
        List<String> memberAddress = memberManager.allMembers().stream().map(Member::getAddress).toList();
        String memberAddressString = StringUtils.join(memberAddress, ",");
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, memberAddressString);
        namingMaintainerService = NamingMaintainerFactory.createNamingMaintainerService(properties);
        configMaintainerService = ConfigMaintainerFactory.createConfigMaintainerService(properties);
        aiMaintainerService = AiMaintainerFactory.createAiMaintainerService(properties);
    }
    
    public NamingMaintainerService getNamingMaintainerService() {
        return namingMaintainerService;
    }
    
    public ConfigMaintainerService getConfigMaintainerService() {
        return configMaintainerService;
    }
    
    public AiMaintainerService getAiMaintainerService() {
        return aiMaintainerService;
    }
    
    @Override
    public void onEvent(MembersChangeEvent event) {
        try {
            buildMaintainerService();
        } catch (NacosException e) {
            LOGGER.warn("Nacos Server members changed, but build new maintain client failed with: ", e);
        }
    }
}
