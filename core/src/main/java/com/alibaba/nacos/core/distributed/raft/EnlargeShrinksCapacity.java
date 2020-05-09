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
package com.alibaba.nacos.core.distributed.raft;

import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.utils.LoggerUtils;
import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.core.utils.Loggers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Due to the cluster expansion of Raft protocol, the target node needs to be started first,
 * and then the list of clusters needs to be modified to properly expand the Raft cluster.
 * Therefore, a detection mechanism with a layer of port connectivity is needed, and only
 * the port can be connected can the node be allowed to join in
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class EnlargeShrinksCapacity {

	/**
	 * Use a thread pool with only one core thread
	 */
	private final ExecutorService executor = ExecutorFactory.newFixExecutorService(getClass().getCanonicalName(), 1);
	private final JRaftServer server;
	private FutureTask<Boolean> task;
	private Selector selector;

	private AtomicBoolean isFirst = new AtomicBoolean(true);

	public EnlargeShrinksCapacity(JRaftServer server) throws IOException {
		this.server = server;
		this.selector = Selector.open();
	}

	private SocketChannel createSocketChannel() throws IOException {
		SocketChannel channel = SocketChannel.open();
		channel.configureBlocking(false);
		// only by setting this can we make the socket close event asynchronous
		channel.socket().setSoLinger(false, -1);
		channel.socket().setReuseAddress(true);
		channel.socket().setKeepAlive(true);
		channel.socket().setTcpNoDelay(true);
		return channel;
	}

	public void execute(Set<String> newPeers) {
		// The first startup does not involve node expansion
		if (isFirst.compareAndSet(true, false)) {
			return;
		}

		if (!Objects.isNull(task)) {
			task.cancel(true);
		}
		task = build(newPeers);
		executor.execute(task);
	}

	private FutureTask<Boolean> build(Set<String> newPeers) {
		return new FutureTask<>(() -> {
			Set<String> alreadyConnect = new HashSet<>();
			// This mission must succeed
			while (!newPeers.isEmpty()) {

				for (String peer : newPeers) {
					portProbe(peer);
				}

				// To maximize the number of successful node port connections
				ThreadUtils.sleep(500L);

				alreadyConnect.addAll(onConnect());
				newPeers.removeAll(alreadyConnect);

				if (Thread.interrupted()) {
					break;
				}

				LoggerUtils.printIfDebugEnabled(Loggers.RAFT, "The probe port is accessible to the node : {}", alreadyConnect);

				if (!alreadyConnect.isEmpty()) {
					server.peersChange(alreadyConnect);
				}

				ThreadUtils.sleep(1000L);
			}

			return true;

		});
	}

	private Set<String> onConnect() {
		Set<String> connectPeer = new HashSet<>();
		try {
			selector.select();
			for (SelectionKey key : this.selector.selectedKeys()) {
				SocketChannel channel = (SocketChannel) key.channel();
				String remoteAddress = (String) key.attachment();
				try {
					if (key.isValid() && key.isConnectable()) {
						// connected
						channel.finishConnect();
					}

					if (key.isValid() && key.isReadable()) {
						// disconnected
						ByteBuffer buffer = ByteBuffer.allocate(128);
						if (channel.read(buffer) == -1) {
							key.cancel();
							key.channel().close();
						}
					}
					connectPeer.add(remoteAddress);

					LoggerUtils.printIfDebugEnabled(Loggers.RAFT, "Node-port probe probe success {}", remoteAddress);
				}
				catch (Throwable ex) {
					Loggers.RAFT.warn("Node-port probe probe failed on method [onConnect] {} : {}", remoteAddress, ex.toString());
				} finally {
					try {
						key.cancel();
						key.channel().close();
					} catch (Exception ignore) { }
				}
			}
		} catch (Throwable ignore) { }

		return connectPeer;
	}

	private void portProbe(String address) {
		String[] info = address.split(":");
		String ip = info[0];
		int port = Integer.parseInt(info[1]);
		try {
			SocketChannel channel = createSocketChannel();
			channel.connect(new InetSocketAddress(ip, port));
			SelectionKey key = channel
					.register(this.selector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ);
			key.attach(address);
		}
		catch (Throwable ex) {
			Loggers.RAFT.warn("Node-port probe probe failed on method [portProbe] {} : {}", address, ex.toString());
		}

	}

}
