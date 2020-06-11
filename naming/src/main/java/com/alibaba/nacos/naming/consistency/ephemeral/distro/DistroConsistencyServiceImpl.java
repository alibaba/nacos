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
package com.alibaba.nacos.naming.consistency.ephemeral.distro;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.Objects;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.naming.cluster.ServerStatus;
import com.alibaba.nacos.naming.cluster.transport.Serializer;
import com.alibaba.nacos.naming.consistency.ApplyAction;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.RecordListener;
import com.alibaba.nacos.naming.consistency.ephemeral.EphemeralConsistencyService;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.Instances;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.misc.GlobalConfig;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.NamingProxy;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.pojo.Record;
import org.apache.commons.lang3.StringUtils;
import org.javatuples.Pair;
import org.springframework.context.annotation.DependsOn;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A consistency protocol algorithm called <b>Distro</b>
 * <p>
 * Use a distro algorithm to divide data into many blocks. Each Nacos server node takes
 * responsibility for exactly one block of data. Each block of data is generated, removed
 * and synchronized by its responsible server. So every Nacos server only handles writings
 * for a subset of the total service data.
 * <p>
 * At mean time every Nacos server receives data sync of other Nacos server, so every Nacos
 * server will eventually have a complete set of data.
 *
 * @author nkorange
 * @since 1.0.0
 */
@DependsOn("ProtocolManager")
@org.springframework.stereotype.Service("distroConsistencyService")
public class DistroConsistencyServiceImpl implements EphemeralConsistencyService {

	private final DistroMapper distroMapper;

	private final DataStore dataStore;

	private final TaskDispatcher taskDispatcher;

	private final Serializer serializer;

	private final ServerMemberManager memberManager;

	private final SwitchDomain switchDomain;

	private final GlobalConfig globalConfig;

	private boolean initialized = false;

	private volatile Notifier notifier = new Notifier();

	private LoadDataTask loadDataTask = new LoadDataTask();

	private Map<String, ConcurrentLinkedQueue<RecordListener>> listeners = new ConcurrentHashMap<>();

	private Map<String, String> syncChecksumTasks = new ConcurrentHashMap<>(16);

	public DistroConsistencyServiceImpl(DistroMapper distroMapper, DataStore dataStore,
			TaskDispatcher taskDispatcher, Serializer serializer,
			ServerMemberManager memberManager, SwitchDomain switchDomain,
			GlobalConfig globalConfig) {
		this.distroMapper = distroMapper;
		this.dataStore = dataStore;
		this.taskDispatcher = taskDispatcher;
		this.serializer = serializer;
		this.memberManager = memberManager;
		this.switchDomain = switchDomain;
		this.globalConfig = globalConfig;
	}

	@PostConstruct
	public void init() {
		GlobalExecutor.submit(loadDataTask);
		GlobalExecutor.submitDistroNotifyTask(notifier);
	}

	private class LoadDataTask implements Runnable {

		@Override
		public void run() {
			try {
				load();
				if (!initialized) {
					GlobalExecutor
							.submit(this, globalConfig.getLoadDataRetryDelayMillis());
				} else {
					Loggers.DISTRO.info("load data success");
				}
			}
			catch (Exception e) {
				Loggers.DISTRO.error("load data failed.", e);
			}
		}
	}

	public void load() throws Exception {
		if (ApplicationUtils.getStandaloneMode()) {
			initialized = true;
			return;
		}
		// size = 1 means only myself in the list, we need at least one another server alive:
		while (memberManager.getServerList().size() <= 1) {
			Thread.sleep(1000L);
			Loggers.DISTRO.info("waiting server list init...");
		}

		for (Map.Entry<String, Member> entry : memberManager.getServerList().entrySet()) {
			final String address = entry.getValue().getAddress();
			if (ApplicationUtils.getLocalAddress().equals(address)) {
				continue;
			}
			if (Loggers.DISTRO.isDebugEnabled()) {
				Loggers.DISTRO.debug("sync from " + address);
			}
			// try sync data from remote server:
			if (syncAllDataFromRemote(address)) {
				initialized = true;
				return;
			}
		}
	}

	@Override
	public void put(String key, Record value) throws NacosException {
		onPut(key, value);
		taskDispatcher.addTask(key);
	}

	@Override
	public void remove(String key) throws NacosException {
		onRemove(key);
		listeners.remove(key);
	}

	@Override
	public Datum get(String key) throws NacosException {
		return dataStore.get(key);
	}

	public void onPut(String key, Record value) {

		if (KeyBuilder.matchEphemeralInstanceListKey(key)) {
			Datum<Instances> datum = new Datum<>();
			datum.value = (Instances) value;
			datum.key = key;
			datum.timestamp.incrementAndGet();
			dataStore.put(key, datum);
		}

		if (!listeners.containsKey(key)) {
			return;
		}

		notifier.addTask(key, ApplyAction.CHANGE);
	}

	public void onRemove(String key) {

		dataStore.remove(key);

		if (!listeners.containsKey(key)) {
			return;
		}

		notifier.addTask(key, ApplyAction.DELETE);
	}

	public void onReceiveChecksums(Map<String, String> checksumMap, String server) {

		if (syncChecksumTasks.containsKey(server)) {
			// Already in process of this server:
			Loggers.DISTRO.warn("sync checksum task already in process with {}", server);
			return;
		}

		syncChecksumTasks.put(server, "1");

		try {

			List<String> toUpdateKeys = new ArrayList<>();
			List<String> toRemoveKeys = new ArrayList<>();
			for (Map.Entry<String, String> entry : checksumMap.entrySet()) {
				if (distroMapper.responsible(KeyBuilder.getServiceName(entry.getKey()))) {
					// this key should not be sent from remote server:
					Loggers.DISTRO.error("receive responsible key timestamp of " + entry
							.getKey() + " from " + server);
					// abort the procedure:
					return;
				}

				if (!dataStore.contains(entry.getKey())
						|| dataStore.get(entry.getKey()).value == null || !dataStore
						.get(entry.getKey()).value.getChecksum()
						.equals(entry.getValue())) {
					toUpdateKeys.add(entry.getKey());
				}
			}

			for (String key : dataStore.keys()) {

				if (!server.equals(distroMapper.mapSrv(KeyBuilder.getServiceName(key)))) {
					continue;
				}

				if (!checksumMap.containsKey(key)) {
					toRemoveKeys.add(key);
				}
			}

			Loggers.DISTRO.info("to remove keys: {}, to update keys: {}, source: {}",
						toRemoveKeys, toUpdateKeys, server);

			for (String key : toRemoveKeys) {
				onRemove(key);
			}

			if (toUpdateKeys.isEmpty()) {
				return;
			}

			try {
				byte[] result = NamingProxy.getData(toUpdateKeys, server);
				processData(result);
			}
			catch (Exception e) {
				Loggers.DISTRO.error("get data from " + server + " failed!", e);
			}
		}
		finally {
			// Remove this 'in process' flag:
			syncChecksumTasks.remove(server);
		}

	}

	public boolean syncAllDataFromRemote(String server) {

		try {
			byte[] data = NamingProxy.getAllData(server);
			return processData(data);
		}
		catch (Exception e) {
			Loggers.DISTRO.error("sync full data from " + server + " failed!", e);
			return false;
		}
	}

	public boolean processData(byte[] data) throws Exception {
		if (data.length > 0) {
			Map<String, Datum<Instances>> datumMap = serializer
					.deserializeMap(data, Instances.class);

			for (Map.Entry<String, Datum<Instances>> entry : datumMap.entrySet()) {
				dataStore.put(entry.getKey(), entry.getValue());

				if (!listeners.containsKey(entry.getKey())) {
					// pretty sure the service not exist:
					if (switchDomain.isDefaultInstanceEphemeral()) {
						// create empty service
						Loggers.DISTRO.info("creating service {}", entry.getKey());
						Service service = new Service();
						String serviceName = KeyBuilder.getServiceName(entry.getKey());
						String namespaceId = KeyBuilder.getNamespace(entry.getKey());
						service.setName(serviceName);
						service.setNamespaceId(namespaceId);
						service.setGroupName(Constants.DEFAULT_GROUP);
						// now validate the service. if failed, exception will be thrown
						service.setLastModifiedMillis(System.currentTimeMillis());
						service.recalculateChecksum();

						// The Listener corresponding to the key value must not be empty
						RecordListener listener = listeners.get(KeyBuilder.SERVICE_META_KEY_PREFIX).peek();
						if (Objects.isNull(listener)) {
							return false;
						}
						listener.onChange(
								KeyBuilder.buildServiceMetaKey(namespaceId, serviceName),
								service);
					}
				}
			}

			for (Map.Entry<String, Datum<Instances>> entry : datumMap.entrySet()) {

				if (!listeners.containsKey(entry.getKey())) {
					// Should not happen:
					Loggers.DISTRO.warn("listener of {} not found.", entry.getKey());
					continue;
				}

				try {
					for (RecordListener listener : listeners.get(entry.getKey())) {
						listener.onChange(entry.getKey(), entry.getValue().value);
					}
				}
				catch (Exception e) {
					Loggers.DISTRO
							.error("[NACOS-DISTRO] error while execute listener of key: {}",
									entry.getKey(), e);
					continue;
				}

				// Update data store if listener executed successfully:
				dataStore.put(entry.getKey(), entry.getValue());
			}
		}
		return true;
	}

	@Override
	public void listen(String key, RecordListener listener) throws NacosException {
		if (!listeners.containsKey(key)) {
			listeners.put(key, new ConcurrentLinkedQueue<>());
		}

		if (listeners.get(key).contains(listener)) {
			return;
		}

		listeners.get(key).add(listener);
	}

	@Override
	public void unlisten(String key, RecordListener listener) throws NacosException {
		if (!listeners.containsKey(key)) {
			return;
		}
		for (RecordListener recordListener : listeners.get(key)) {
			if (recordListener.equals(listener)) {
				listeners.get(key).remove(listener);
				break;
			}
		}
	}

	@Override
	public boolean isAvailable() {
		return isInitialized() || ServerStatus.UP.name()
				.equals(switchDomain.getOverriddenServerStatus());
	}

	public boolean isInitialized() {
		return initialized || !globalConfig.isDataWarmup();
	}

	public class Notifier implements Runnable {

		private ConcurrentHashMap<String, String> services = new ConcurrentHashMap<>(
				10 * 1024);

		private BlockingQueue<Pair<String, ApplyAction>> tasks = new ArrayBlockingQueue<>(
				1024 * 1024);

		public void addTask(String datumKey, ApplyAction action) {

			if (services.containsKey(datumKey) && action == ApplyAction.CHANGE) {
				return;
			}
			if (action == ApplyAction.CHANGE) {
				services.put(datumKey, StringUtils.EMPTY);
			}
			tasks.offer(Pair.with(datumKey, action));
		}

		public int getTaskSize() {
			return tasks.size();
		}

		@Override
		public void run() {
			Loggers.DISTRO.info("distro notifier started");

			for ( ; ; ) {
				try {
					Pair<String, ApplyAction> pair = tasks.take();
                    handle(pair);
				}
				catch (Throwable e) {
					Loggers.DISTRO
							.error("[NACOS-DISTRO] Error while handling notifying task",
									e);
				}
			}
		}

		private void handle(Pair<String, ApplyAction> pair) {
			try {
				String datumKey = pair.getValue0();
				ApplyAction action = pair.getValue1();

				services.remove(datumKey);

				int count = 0;

				if (!listeners.containsKey(datumKey)) {
					return;
				}

				for (RecordListener listener : listeners.get(datumKey)) {

					count++;

					try {
						if (action == ApplyAction.CHANGE) {
							listener.onChange(datumKey, dataStore.get(datumKey).value);
							continue;
						}

						if (action == ApplyAction.DELETE) {
							listener.onDelete(datumKey);
							continue;
						}
					}
					catch (Throwable e) {
						Loggers.DISTRO
								.error("[NACOS-DISTRO] error while notifying listener of key: {}",
										datumKey, e);
					}
				}

				if (Loggers.DISTRO.isDebugEnabled()) {
					Loggers.DISTRO
							.debug("[NACOS-DISTRO] datum change notified, key: {}, listener count: {}, action: {}",
									datumKey, count, action.name());
				}
			}
			catch (Throwable e) {
				Loggers.DISTRO
						.error("[NACOS-DISTRO] Error while handling notifying task", e);
			}
		}
	}
}
