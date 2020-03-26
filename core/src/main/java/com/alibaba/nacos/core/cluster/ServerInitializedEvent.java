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

package com.alibaba.nacos.core.cluster;

import com.alibaba.nacos.core.notify.Event;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.boot.web.context.WebServerInitializedEvent;

import javax.servlet.ServletContext;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ServerInitializedEvent implements Event {

	private WebServerInitializedEvent event;
	private ServletContext servletContext;

	public ServerInitializedEvent() {
	}

	protected ServerInitializedEvent(final WebServerInitializedEvent event, final ServletContext context) {
		this.event = event;
		this.servletContext = context;
	}

	@Override
	public Class<? extends Event> eventType() {
		return ServerInitializedEvent.class;
	}

	public WebServerApplicationContext getApplicationContext() {
		return event.getApplicationContext();
	}

	public WebServerInitializedEvent getEvent() {
		return event;
	}

	public ServletContext getServletContext() {
		return servletContext;
	}
}
