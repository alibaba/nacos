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

package com.alibaba.nacos.config.server.controller;

import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.service.datasource.DataSourceService;
import com.alibaba.nacos.config.server.service.datasource.DynamicDataSource;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.sys.utils.InetUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.util.Map;

/**
 * Health service.
 *
 * @author Nacos
 */
@RestController
@RequestMapping(Constants.HEALTH_CONTROLLER_PATH)
public class HealthController {
    
    private DataSourceService dataSourceService;
    
    private static final String HEALTH_UP = "UP";
    
    private static final String HEALTH_DOWN = "DOWN";
    
    private static final String HEALTH_WARN = "WARN";
    
    @Autowired
    private ServerMemberManager memberManager;
    
    @PostConstruct
    public void init() {
        dataSourceService = DynamicDataSource.getInstance().getDataSource();
    }
    
    @GetMapping
    public String getHealth() {
        // TODO UP DOWN WARN
        StringBuilder sb = new StringBuilder();
        String dbStatus = dataSourceService.getHealth();
        boolean addressServerHealthy = isAddressServerHealthy();
        if (dbStatus.contains(HEALTH_UP) && addressServerHealthy && ServerMemberManager.isInIpList()) {
            sb.append(HEALTH_UP);
        } else if (dbStatus.contains(HEALTH_WARN) && addressServerHealthy && ServerMemberManager.isInIpList()) {
            sb.append("WARN:");
            sb.append("slave db (").append(dbStatus.split(":")[1]).append(") down. ");
        } else {
            sb.append("DOWN:");
            if (dbStatus.contains(HEALTH_DOWN)) {
                sb.append("master db (").append(dbStatus.split(":")[1]).append(") down. ");
            }
        
            if (!addressServerHealthy) {
                sb.append("address server down. ");
            }
            if (!ServerMemberManager.isInIpList()) {
                sb.append("server ip ").append(InetUtils.getSelfIP())
                        .append(" is not in the serverList of address server. ");
            }
        }
    
        return sb.toString();
    }
    
    private boolean isAddressServerHealthy() {
        Map<String, Object> info = memberManager.getLookup().info();
        return info != null && info.get("addressServerHealth") != null && Boolean
                .parseBoolean(info.get("addressServerHealth").toString());
    }
    
}
