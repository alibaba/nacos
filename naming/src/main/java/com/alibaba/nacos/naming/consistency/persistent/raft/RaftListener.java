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

package com.alibaba.nacos.naming.consistency.persistent.raft;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Inject the raft information from the naming module into the outlier information of the node
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Component
public class RaftListener implements SmartApplicationListener {

	private static final String GROUP = "naming";

	@Autowired
	private ServerMemberManager memberManager;

	@Override
	public boolean supportsEventType(
			Class<? extends ApplicationEvent> eventType) {
		boolean a = BaseRaftEvent.class.isAssignableFrom(eventType);
		return a;
	}

	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		if (event instanceof BaseRaftEvent) {
			BaseRaftEvent raftEvent = (BaseRaftEvent) event;
			RaftPeer local = raftEvent.getLocal();
			String json = JacksonUtils.toJson(local);
			Map map = JacksonUtils.toObj(json, HashMap.class);
			Member self = memberManager.getSelf();
			self.setExtendVal(GROUP, map);
			memberManager.update(self);
		}
	}
}
