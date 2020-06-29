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
import com.alibaba.nacos.core.utils.InetUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;

/**
 * health service
 *
 * @author Nacos
 */
@RestController
@RequestMapping(Constants.HEALTH_CONTROLLER_PATH)
public class HealthController {

	private DataSourceService dataSourceService;
	private String heathUpStr = "UP";
	private String heathDownStr = "DOWN";
	private String heathWarnStr = "WARN";

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
		if (dbStatus.contains(heathUpStr) && memberManager.isInIpList()) {
			sb.append(heathUpStr);
		}
		else if (dbStatus.contains(heathWarnStr) && memberManager.isInIpList()) {
			sb.append("WARN:");
			sb.append("slave db (").append(dbStatus.split(":")[1]).append(") down. ");
		}
		else {
			sb.append("DOWN:");
			if (dbStatus.contains(heathDownStr)) {
				sb.append("master db (").append(dbStatus.split(":")[1])
						.append(") down. ");
			}
			if (!memberManager.isInIpList()) {
				sb.append("server ip ").append(InetUtils.getSelfIp())
						.append(" is not in the serverList of address server. ");
			}
		}

		return sb.toString();
	}

}
