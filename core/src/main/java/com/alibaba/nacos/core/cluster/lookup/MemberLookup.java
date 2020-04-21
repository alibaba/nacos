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
import com.alibaba.nacos.common.utils.Observable;
import com.alibaba.nacos.core.cluster.Member;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Member node addressing mode
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class MemberLookup extends Observable {

	/**
	 * start
	 *
	 * @throws NacosException
	 */
	public abstract void start() throws NacosException;

	/**
	 * Gets the list of addresses stored in the addressing mode
	 *
	 * @return {@link Collection<Member>}
	 */
	public abstract Collection<Member> getMembers();

	/**
	 * The addressing pattern finds cluster nodes
	 *
	 * @param members {@link Collection<Member>}
	 */
	abstract void afterLookup(Collection<Member> members);

	/**
	 * Addressing mode closed
	 *
	 * @throws NacosException
	 */
	abstract void destroy() throws NacosException;

	/**
	 * Some data information about the addressing pattern
	 *
	 * @return {@link Map<String, Object>}
	 */
	public Map<String, Object> info() {
		return Collections.emptyMap();
	}

}
