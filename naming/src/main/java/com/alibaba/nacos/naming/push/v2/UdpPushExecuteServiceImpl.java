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

package com.alibaba.nacos.naming.push.v2;

import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.push.PushService;
import org.springframework.stereotype.Component;

/**
 * Push execute service for udp.
 *
 * @author xiweng.yy
 */
@Component
public class UdpPushExecuteServiceImpl implements PushExecuteService {
    
    private final PushService pushService;
    
    public UdpPushExecuteServiceImpl(PushService pushService) {
        this.pushService = pushService;
    }
    
    @Override
    public void doPush(String clientId, Subscriber subscriber, ServiceInfo data) {
        data.setName(NamingUtils.getGroupedName(data.getName(), data.getGroupName()));
        pushService.pushData(subscriber, data);
    }
}
