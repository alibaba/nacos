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
package com.alibaba.nacos.client.config.filter.impl;

import java.util.List;

import com.alibaba.nacos.api.config.filter.ConfigFilter;
import com.alibaba.nacos.api.config.filter.ConfigFilterChain;
import com.alibaba.nacos.api.config.filter.ConfigRequest;
import com.alibaba.nacos.api.config.filter.ConfigResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.google.common.collect.Lists;

/**
 * Config Filter Chain Management
 * 
 * @author Nacos
 *
 */
public class ConfigFilterChainManager implements ConfigFilterChain {

	private List<ConfigFilter> filters = Lists.newArrayList();

	public synchronized ConfigFilterChainManager addFilter(ConfigFilter filter) {
		// 根据order大小顺序插入
		int i = 0;
		while (i < this.filters.size()) {
			ConfigFilter currentValue = this.filters.get(i);
			if (currentValue.getFilterName().equals(filter.getFilterName())) {
				break;
			}
			if (filter.getOrder() >= currentValue.getOrder() && i < this.filters.size()) {
				i++;
			} else {
				this.filters.add(i, filter);
				break;
			}
		}

		if (i == this.filters.size()) {
			this.filters.add(i, filter);
		}
		return this;
	}
	

	@Override
	public void doFilter(ConfigRequest request, ConfigResponse response) throws NacosException {
		new VirtualFilterChain(this.filters).doFilter(request, response);
	}

	private static class VirtualFilterChain implements ConfigFilterChain {

		private final List<? extends ConfigFilter> additionalFilters;

		private int currentPosition = 0;

		public VirtualFilterChain(List<? extends ConfigFilter> additionalFilters) {
			this.additionalFilters = additionalFilters;
		}

		@Override
		public void doFilter(final ConfigRequest request, final ConfigResponse response) throws NacosException {
			if (this.currentPosition == this.additionalFilters.size()) {
				return;
			} else {
				this.currentPosition++;
				ConfigFilter nextFilter = this.additionalFilters.get(this.currentPosition - 1);
				nextFilter.doFilter(request, response, this);
			}
		}
	}

}
