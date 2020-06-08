/*
 *
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
 *
 */

package com.alibaba.nacos.config.server.service.dump;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.event.ConfigDumpEvent;
import com.alibaba.nacos.config.server.service.AggrWhitelist;
import com.alibaba.nacos.config.server.service.ClientIpWhiteList;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.SwitchService;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.core.notify.Event;
import com.alibaba.nacos.core.notify.listener.Subscribe;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class DumpConfigHandler implements Subscribe<ConfigDumpEvent> {

	public static boolean configDump(ConfigDumpEvent event) {
		final String dataId = event.getDataId();
		final String group = event.getGroup();
		final String namespaceId = event.getNamespaceId();
		final String content = event.getContent();
		final String type = event.getType();
		final long lastModified = event.getLastModifiedTs();
		if (event.isBeta()) {
			boolean result = false;
			if (event.isRemove()) {
				result = ConfigCacheService.removeBeta(dataId, group, namespaceId);
				if (result) {
					ConfigTraceService
							.logDumpEvent(dataId, group, namespaceId, null, lastModified,
									event.getHandleIp(),
									ConfigTraceService.DUMP_EVENT_REMOVE_OK,
									System.currentTimeMillis() - lastModified, 0);
				}
				return result;
			}
			else {
				result = ConfigCacheService
						.dumpBeta(dataId, group, namespaceId, content, lastModified,
								event.getBetaIps());
				if (result) {
					ConfigTraceService
							.logDumpEvent(dataId, group, namespaceId, null, lastModified,
									event.getHandleIp(), ConfigTraceService.DUMP_EVENT_OK,
									System.currentTimeMillis() - lastModified,
									content.length());
				}
			}

			return result;
		}
		if (StringUtils.isBlank(event.getTag())) {
			if (dataId.equals(AggrWhitelist.AGGRIDS_METADATA)) {
				AggrWhitelist.load(content);
			}

			if (dataId.equals(ClientIpWhiteList.CLIENT_IP_WHITELIST_METADATA)) {
				ClientIpWhiteList.load(content);
			}

			if (dataId.equals(SwitchService.SWITCH_META_DATAID)) {
				SwitchService.load(content);
			}

			boolean result;
			if (!event.isRemove()) {
				result = ConfigCacheService
						.dump(dataId, group, namespaceId, content, lastModified, type);

				if (result) {
					ConfigTraceService
							.logDumpEvent(dataId, group, namespaceId, null, lastModified,
									event.getHandleIp(), ConfigTraceService.DUMP_EVENT_OK,
									System.currentTimeMillis() - lastModified,
									content.length());
				}
			}
			else {
				result = ConfigCacheService.remove(dataId, group, namespaceId);

				if (result) {
					ConfigTraceService
							.logDumpEvent(dataId, group, namespaceId, null, lastModified,
									event.getHandleIp(),
									ConfigTraceService.DUMP_EVENT_REMOVE_OK,
									System.currentTimeMillis() - lastModified, 0);
				}
			}
			return result;
		}
		else {
			//
			boolean result;
			if (!event.isRemove()) {
				result = ConfigCacheService
						.dumpTag(dataId, group, namespaceId, event.getTag(), content,
								lastModified);
				if (result) {
					ConfigTraceService
							.logDumpEvent(dataId, group, namespaceId, null, lastModified,
									event.getHandleIp(), ConfigTraceService.DUMP_EVENT_OK,
									System.currentTimeMillis() - lastModified,
									content.length());
				}
			}
			else {
				result = ConfigCacheService
						.removeTag(dataId, group, namespaceId, event.getTag());
				if (result) {
					ConfigTraceService
							.logDumpEvent(dataId, group, namespaceId, null, lastModified,
									event.getHandleIp(),
									ConfigTraceService.DUMP_EVENT_REMOVE_OK,
									System.currentTimeMillis() - lastModified, 0);
				}
			}
			return result;
		}

	}

	@Override
	public void onEvent(ConfigDumpEvent event) {
		configDump(event);
	}

	@Override
	public Class<? extends Event> subscribeType() {
		return ConfigDumpEvent.class;
	}
}
