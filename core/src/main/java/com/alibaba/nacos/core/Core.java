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

package com.alibaba.nacos.core;

import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * <p>
 *     Initialization of all insert, update, and delete statements under
 *     a transaction in the order in which they occur
 * </p>
 *
 * <pre>
 *                                                                      ┌────────────────┐
 *                                                                      │                │
 *                                                                      │                ▼
 *                                                                      │    ┌───────────────────────┐
 *                                                                      │    │  AddressServerInit()  │
 *                                                                      │    └───────────────────────┘
 *                                                                      │                │
 *                                                                      │                │
 *                                          ┌───────────────────────┐   │                ▼
 *                          ┌──────────────▶│  ServerMemberManager  │───┘       ┌─────────────────┐
 *                          │               └───────────────────────┘           │  GossipInit()   │
 *                          │                           │                       └─────────────────┘
 *                          │                           │
 *     ┌────┐           ┌──────┐                        │
 *     │Core│──────────▶│init()│                        │
 *     └────┘           └──────┘                        │
 *                                                      │                       ┌───────────────────┐
 *                                                      ▼                 ┌─────│    APProtocol     │
 *                                          ┌───────────────────────┐     │     └───────────────────┘
 *                                          │    ProtocolManager    │◀────┤
 *                                          └───────────────────────┘     │     ┌───────────────────┐
 *                                                                        └─────│    CPProtocol     │
 *                                                                              └───────────────────┘
 * </pre>
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Component(value = "core")
public class Core implements InitializingBean, DisposableBean {

	@Autowired
	private ServerMemberManager memberManager;

	@Autowired
	private ProtocolManager protocolManager;

	@Override
	public void afterPropertiesSet() throws Exception {
		memberManager.init();
		protocolManager.init(memberManager);
	}

	@Override
	public void destroy() throws Exception {
		protocolManager.destroy();
		memberManager.destroy();
	}
}
