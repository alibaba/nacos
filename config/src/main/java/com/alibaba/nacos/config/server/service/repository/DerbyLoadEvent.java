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

package com.alibaba.nacos.config.server.service.repository;

import com.alibaba.nacos.core.notify.SlowEvent;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class DerbyLoadEvent implements SlowEvent {

	public static final DerbyLoadEvent INSTANCE = new DerbyLoadEvent();

	private static final long serialVersionUID = 875401667921565121L;

}
