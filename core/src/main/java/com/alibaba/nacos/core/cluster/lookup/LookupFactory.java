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

package com.alibaba.nacos.core.cluster.lookup;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.core.cluster.MemberLookup;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.core.utils.Loggers;

import java.io.File;
import java.util.Objects;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class LookupFactory {

	static final String GOSSIP_SWITCH_NAME = "nacos.gossip";

	static MemberLookup LOOK_UP = null;

	static int currentLookupType = -1;

	interface LookupType {

		int FILE_CONFIG = 1;

		int ADDRESS_SERVER = 2;

		int GOSSIP = 3;

	}

	public static void initLookUp(ServerMemberManager memberManager)
			throws NacosException {
		if (!ApplicationUtils.getStandaloneMode()) {
			int type = chooseLookup();
			switch (type) {
			case LookupType.FILE_CONFIG:
				LOOK_UP = new FileConfigMemberLookup();
				break;
			case LookupType.ADDRESS_SERVER:
				LOOK_UP = new AddressServerMemberLookup();
				break;
			case LookupType.GOSSIP:
				LOOK_UP = new GossipMemberLookup();
				break;
			default:
				throw new IllegalArgumentException();
			}

			currentLookupType = type;

		}
		else {
			LOOK_UP = new StandaloneMemberLookup();
		}

		Loggers.CLUSTER.info("Current addressing mode selection : {}", LOOK_UP.getClass().getSimpleName());
		LOOK_UP.init(memberManager);
		LOOK_UP.run();
	}

	public static void switchLookup(int type, ServerMemberManager memberManager) throws NacosException {

		if (currentLookupType == type) {
			return;
		}

		MemberLookup newLookup = null;
		switch (type) {
		case LookupType.FILE_CONFIG:
			newLookup = new FileConfigMemberLookup();
			break;
		case LookupType.ADDRESS_SERVER:
			newLookup = new AddressServerMemberLookup();
			break;
		case LookupType.GOSSIP:
			newLookup = new GossipMemberLookup();
			break;
		default:
			throw new IllegalArgumentException();
		}

		currentLookupType = type;
		LOOK_UP.destroy();
		LOOK_UP = newLookup;
		LOOK_UP.init(memberManager);
		LOOK_UP.destroy();
	}

	private static int chooseLookup() {
		File file = new File(ApplicationUtils.getClusterConfFilePath());
		if (Boolean.parseBoolean(ApplicationUtils.getProperty(GOSSIP_SWITCH_NAME, Boolean.toString(false)))) {
			return LookupType.GOSSIP;
		}
		if (file.exists()) {
			return LookupType.FILE_CONFIG;
		}
		return LookupType.ADDRESS_SERVER;
	}

	public static MemberLookup getLookUp() {
		return LOOK_UP;
	}

	public static void destroy() throws NacosException {
		Objects.requireNonNull(LOOK_UP).destroy();
	}

}
