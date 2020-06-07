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
import com.alibaba.nacos.core.file.FileChangeEvent;
import com.alibaba.nacos.core.file.FileWatcher;
import com.alibaba.nacos.core.file.WatchFileCenter;
import com.alibaba.nacos.core.cluster.AbstractMemberLookup;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberUtils;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.core.utils.Loggers;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Cluster.conf file managed cluster member node addressing pattern
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class FileConfigMemberLookup extends AbstractMemberLookup {

	private FileWatcher watcher = new FileWatcher() {
		@Override
		public void onChange(FileChangeEvent event) {
			readClusterConfFromDisk();
		}

		@Override
		public boolean interest(String context) {
			return StringUtils.contains(context, "cluster.conf");
		}
	};

	@Override
	public void start() throws NacosException {
		if (start.compareAndSet(false, true)) {
			readClusterConfFromDisk();

			// Use the inotify mechanism to monitor file changes and automatically
			// trigger the reading of cluster.conf
			try {
				WatchFileCenter.registerWatcher(ApplicationUtils.getConfFilePath(), watcher);
			}
			catch (Throwable e) {
				Loggers.CLUSTER
						.error("An exception occurred in the launch file monitor : {}", e.getMessage());
			}
		}
	}

	@Override
	public void destroy() throws NacosException {
		WatchFileCenter.deregisterWatcher(ApplicationUtils.getConfFilePath(), watcher);
	}

	private void readClusterConfFromDisk() {
		Collection<Member> tmpMembers = new ArrayList<>();
		try {
			List<String> tmp = ApplicationUtils.readClusterConf();
			tmpMembers = MemberUtils.readServerConf(tmp);
		}
		catch (Throwable e) {
			Loggers.CLUSTER
					.error("nacos-XXXX [serverlist] failed to get serverlist from disk!, error : {}",
							e.getMessage());
		}

		afterLookup(tmpMembers);
	}
}
