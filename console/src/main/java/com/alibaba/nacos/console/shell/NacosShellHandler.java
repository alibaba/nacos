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
package com.alibaba.nacos.console.shell;

import com.alibaba.nacos.shell.OperationalCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="mailto:liaochunyhm@live.com">liaochuntao</a>
 */
public class NacosShellHandler implements WebSocketHandler {

	private static final Map<String, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();

	@Lazy
	@Autowired
	private List<OperationalCommand> commands;

	@Override
	public void afterConnectionEstablished(WebSocketSession webSocketSession)
			throws Exception {

	}

	@Override
	public void handleMessage(WebSocketSession webSocketSession,
			WebSocketMessage<?> webSocketMessage) throws Exception {

	}

	@Override
	public void handleTransportError(WebSocketSession webSocketSession,
			Throwable throwable) throws Exception {

	}

	@Override
	public void afterConnectionClosed(WebSocketSession webSocketSession,
			CloseStatus closeStatus) throws Exception {

	}

	@Override
	public boolean supportsPartialMessages() {
		return false;
	}
}
