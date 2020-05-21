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
package com.alibaba.nacos.config.server.service.dump;

import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.Observable;
import com.alibaba.nacos.common.utils.Observer;
import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.manager.TaskManager;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoAggr;
import com.alibaba.nacos.config.server.model.ConfigInfoChanged;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.service.ConfigService;
import com.alibaba.nacos.config.server.service.DiskUtil;
import com.alibaba.nacos.config.server.service.DynamicDataSource;
import com.alibaba.nacos.config.server.service.TimerTaskService;
import com.alibaba.nacos.config.server.service.merge.MergeTaskProcessor;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.config.server.utils.ContentUtils;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.core.distributed.raft.exception.NoSuchRaftGroupException;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.core.utils.InetUtils;
import com.alibaba.nacos.core.utils.TimerContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.alibaba.nacos.config.server.utils.LogUtil.fatalLog;

/**
 * Dump data service
 *
 * @author Nacos
 */
@Service
public class DumpService {

	private final PersistService persistService;
	private final ServerMemberManager memberManager;
	private final ProtocolManager protocolManager;

	/**
	 * Here you inject the dependent objects constructively, ensuring that some
	 * of the dependent functionality is initialized ahead of time
	 *
	 * @param persistService  {@link PersistService}
	 * @param memberManager   {@link ServerMemberManager}
	 * @param protocolManager {@link ProtocolManager}
	 */
	public DumpService(PersistService persistService, ServerMemberManager memberManager,
			ProtocolManager protocolManager) {
		this.persistService = persistService;
		this.memberManager = memberManager;
		this.protocolManager = protocolManager;
	}

	public PersistService getPersistService() {
		return persistService;
	}

	public ServerMemberManager getMemberManager() {
		return memberManager;
	}

	@PostConstruct
	protected void init() throws Throwable {
		DynamicDataSource.getInstance().getDataSource();

		DumpProcessor processor = new DumpProcessor(this);
		DumpAllProcessor dumpAllProcessor = new DumpAllProcessor(this);
		DumpAllBetaProcessor dumpAllBetaProcessor = new DumpAllBetaProcessor(this);
		DumpAllTagProcessor dumpAllTagProcessor = new DumpAllTagProcessor(this);

		dumpTaskMgr = new TaskManager("com.alibaba.nacos.server.DumpTaskManager");
		dumpTaskMgr.setDefaultTaskProcessor(processor);

		dumpAllTaskMgr = new TaskManager("com.alibaba.nacos.server.DumpAllTaskManager");
		dumpAllTaskMgr.setDefaultTaskProcessor(dumpAllProcessor);

		// If using embedded distributed storage, you need to wait for the
		// underlying master to complete the selection
		if (PropertyUtil.isEmbeddedStorage() && !ApplicationUtils.getStandaloneMode()) {

			CPProtocol protocol = protocolManager.getCpProtocol();

			LogUtil.dumpLog
					.info("With embedded distributed storage, you need to wait for "
							+ "the underlying master to complete before you can perform the dump operation.");

			AtomicReference<Throwable> errorReference = new AtomicReference<>(null);
			CountDownLatch waitDumpFinish = new CountDownLatch(1);

			// watch path => /nacos_config/leader/ has value ?
			Observer observer = new Observer() {

				@Override
				public void update(Observable o, Object arg) {
					GlobalExecutor.executeByCommon(() -> {
						// must make sure that there is a value here to perform the correct operation that follows
						if (Objects.isNull(arg)) {
							return;
						}
						try {
							dumpOperate(processor, dumpAllProcessor, dumpAllBetaProcessor,
									dumpAllTagProcessor);
						}
						catch (Throwable ex) {
							errorReference.set(ex);
						}
						finally {
							waitDumpFinish.countDown();
						}
						protocol.protocolMetaData()
								.unSubscribe(Constants.CONFIG_MODEL_RAFT_GROUP,
										com.alibaba.nacos.consistency.cp.Constants.LEADER_META_DATA,
										this);
					});
				}
			};

			protocol.protocolMetaData().subscribe(Constants.CONFIG_MODEL_RAFT_GROUP,
					com.alibaba.nacos.consistency.cp.Constants.LEADER_META_DATA,
					observer);

			// We must wait for the dump task to complete the callback operation before
			// continuing with the initialization
			ThreadUtils.latchAwait(waitDumpFinish);

			// If an exception occurs during the execution of the dump task, the exception
			// needs to be thrown, triggering the node to start the failed process
			final Throwable ex = errorReference.get();
			if (Objects.nonNull(ex)) {
				throw ex;
			}

		}
		else {
			dumpOperate(processor, dumpAllProcessor, dumpAllBetaProcessor,
					dumpAllTagProcessor);
		}
	}

	private void dumpOperate(DumpProcessor processor, DumpAllProcessor dumpAllProcessor,
			DumpAllBetaProcessor dumpAllBetaProcessor,
			DumpAllTagProcessor dumpAllTagProcessor) {
		TimerContext.start("config dump job");
		try {
			LogUtil.defaultLog.warn("DumpService start");

			Runnable dumpAll = () -> dumpAllTaskMgr
					.addTask(DumpAllTask.TASK_ID, new DumpAllTask());

			Runnable dumpAllBeta = () -> dumpAllTaskMgr
					.addTask(DumpAllBetaTask.TASK_ID, new DumpAllBetaTask());

			Runnable clearConfigHistory = () -> {
				log.warn("clearConfigHistory start");
				if (canExecute()) {
					try {
						Timestamp startTime = getBeforeStamp(TimeUtils.getCurrentTime(),
								24 * getRetentionDays());
						int totalCount = persistService
								.findConfigHistoryCountByTime(startTime);
						if (totalCount > 0) {
							int pageSize = 1000;
							int removeTime = (totalCount + pageSize - 1) / pageSize;
							log.warn(
									"clearConfigHistory, getBeforeStamp:{}, totalCount:{}, pageSize:{}, removeTime:{}",
									startTime, totalCount, pageSize, removeTime);
							while (removeTime > 0) {
								// 分页删除，以免批量太大报错
								persistService.removeConfigHistory(startTime, pageSize);
								removeTime--;
							}
						}
					}
					catch (Throwable e) {
						log.error("clearConfigHistory error", e);
					}
				}
			};

			try {
				dumpConfigInfo(dumpAllProcessor);

				// 更新beta缓存
				LogUtil.defaultLog.info("start clear all config-info-beta.");
				DiskUtil.clearAllBeta();
				if (persistService.isExistTable(BETA_TABLE_NAME)) {
					dumpAllBetaProcessor
							.process(DumpAllBetaTask.TASK_ID, new DumpAllBetaTask());
				}
				// 更新Tag缓存
				LogUtil.defaultLog.info("start clear all config-info-tag.");
				DiskUtil.clearAllTag();
				if (persistService.isExistTable(TAG_TABLE_NAME)) {
					dumpAllTagProcessor
							.process(DumpAllTagTask.TASK_ID, new DumpAllTagTask());
				}

				// add to dump aggr
				List<ConfigInfoChanged> configList = persistService.findAllAggrGroup();
				if (configList != null && !configList.isEmpty()) {
					total = configList.size();
					List<List<ConfigInfoChanged>> splitList = splitList(configList,
							INIT_THREAD_COUNT);
					for (List<ConfigInfoChanged> list : splitList) {
						MergeAllDataWorker work = new MergeAllDataWorker(list);
						work.start();
					}
					log.info("server start, schedule merge end.");
				}
			}
			catch (Exception e) {
				LogUtil.fatalLog
						.error("Nacos Server did not start because dumpservice bean construction failure :\n"
								+ e.getMessage(), e.getCause());
				throw new RuntimeException(
						"Nacos Server did not start because dumpservice bean construction failure :\n"
								+ e.getMessage());
			}
			if (!ApplicationUtils.getStandaloneMode()) {
				Runnable heartbeat = () -> {
					String heartBeatTime = TimeUtils.getCurrentTime().toString();
					// write disk
					try {
						DiskUtil.saveHeartBeatToDisk(heartBeatTime);
					}
					catch (IOException e) {
						LogUtil.fatalLog.error("save heartbeat fail" + e.getMessage());
					}
				};

				TimerTaskService
						.scheduleWithFixedDelay(heartbeat, 0, 10, TimeUnit.SECONDS);

				long initialDelay = new Random().nextInt(INITIAL_DELAY_IN_MINUTE) + 10;
				LogUtil.defaultLog.warn("initialDelay:{}", initialDelay);

				TimerTaskService.scheduleWithFixedDelay(dumpAll, initialDelay,
						DUMP_ALL_INTERVAL_IN_MINUTE, TimeUnit.MINUTES);

				TimerTaskService.scheduleWithFixedDelay(dumpAllBeta, initialDelay,
						DUMP_ALL_INTERVAL_IN_MINUTE, TimeUnit.MINUTES);
			}

			TimerTaskService
					.scheduleWithFixedDelay(clearConfigHistory, 10, 10, TimeUnit.MINUTES);
		}
		finally {
			TimerContext.end(LogUtil.dumpLog);
		}

	}

	private void dumpConfigInfo(DumpAllProcessor dumpAllProcessor) throws IOException {
		int timeStep = 6;
		Boolean isAllDump = true;
		// initial dump all
		FileInputStream fis = null;
		Timestamp heartheatLastStamp = null;
		try {
			if (isQuickStart()) {
				File heartbeatFile = DiskUtil.heartBeatFile();
				if (heartbeatFile.exists()) {
					fis = new FileInputStream(heartbeatFile);
					String heartheatTempLast = IoUtils.toString(fis, Constants.ENCODE);
					heartheatLastStamp = Timestamp.valueOf(heartheatTempLast);
					if (TimeUtils.getCurrentTime().getTime() - heartheatLastStamp
							.getTime() < timeStep * 60 * 60 * 1000) {
						isAllDump = false;
					}
				}
			}
			if (isAllDump) {
				LogUtil.defaultLog.info("start clear all config-info.");
				DiskUtil.clearAll();
				dumpAllProcessor.process(DumpAllTask.TASK_ID, new DumpAllTask());
			}
			else {
				Timestamp beforeTimeStamp = getBeforeStamp(heartheatLastStamp, timeStep);
				DumpChangeProcessor dumpChangeProcessor = new DumpChangeProcessor(this,
						beforeTimeStamp, TimeUtils.getCurrentTime());
				dumpChangeProcessor.process(DumpChangeTask.TASK_ID, new DumpChangeTask());
				Runnable checkMd5Task = () -> {
					LogUtil.defaultLog.error("start checkMd5Task");
					List<String> diffList = ConfigService.checkMd5();
					for (String groupKey : diffList) {
						String[] dg = GroupKey.parseKey(groupKey);
						String dataId = dg[0];
						String group = dg[1];
						String tenant = dg[2];
						ConfigInfoWrapper configInfo = persistService
								.queryConfigInfo(dataId, group, tenant);
						ConfigService.dumpChange(dataId, group, tenant,
								configInfo.getContent(), configInfo.getLastModified());
					}
					LogUtil.defaultLog.error("end checkMd5Task");
				};
				TimerTaskService
						.scheduleWithFixedDelay(checkMd5Task, 0, 12, TimeUnit.HOURS);
			}
		}
		catch (IOException e) {
			LogUtil.fatalLog.error("dump config fail" + e.getMessage());
			throw e;
		}
		finally {
			if (null != fis) {
				try {
					fis.close();
				}
				catch (IOException e) {
					LogUtil.defaultLog.warn("close file failed");
				}
			}
		}
	}

	private Timestamp getBeforeStamp(Timestamp date, int step) {
		Calendar cal = Calendar.getInstance();
		/**
		 *  date 换成已经已知的Date对象
		 */
		cal.setTime(date);
		/**
		 *  before 6 hour
		 */
		cal.add(Calendar.HOUR_OF_DAY, -step);
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		return Timestamp.valueOf(format.format(cal.getTime()));
	}

	private Boolean isQuickStart() {
		try {
			String val = null;
			val = ApplicationUtils.getProperty("isQuickStart");
			if (val != null && TRUE_STR.equals(val)) {
				isQuickStart = true;
			}
			fatalLog.warn("isQuickStart:{}", isQuickStart);
		}
		catch (Exception e) {
			fatalLog.error("read application.properties wrong", e);
		}
		return isQuickStart;
	}

	private int getRetentionDays() {
		String val = ApplicationUtils.getProperty("nacos.config.retention.days");
		if (null == val) {
			return retentionDays;
		}

		int tmp = 0;
		try {
			tmp = Integer.parseInt(val);
			if (tmp > 0) {
				retentionDays = tmp;
			}
		}
		catch (NumberFormatException nfe) {
			fatalLog.error("read nacos.config.retention.days wrong", nfe);
		}

		return retentionDays;
	}

	public void dump(String dataId, String group, String tenant, String tag,
			long lastModified, String handleIp) {
		dump(dataId, group, tenant, tag, lastModified, handleIp, false);
	}

	public void dump(String dataId, String group, String tenant, long lastModified,
			String handleIp) {
		dump(dataId, group, tenant, lastModified, handleIp, false);
	}

	public void dump(String dataId, String group, String tenant, long lastModified,
			String handleIp, boolean isBeta) {
		String groupKey = GroupKey2.getKey(dataId, group, tenant);
		dumpTaskMgr.addTask(groupKey,
				new DumpTask(groupKey, lastModified, handleIp, isBeta));
	}

	public void dump(String dataId, String group, String tenant, String tag,
			long lastModified, String handleIp, boolean isBeta) {
		String groupKey = GroupKey2.getKey(dataId, group, tenant);
		dumpTaskMgr.addTask(groupKey,
				new DumpTask(groupKey, tag, lastModified, handleIp, isBeta));
	}

	public void dumpAll() {
		dumpAllTaskMgr.addTask(DumpAllTask.TASK_ID, new DumpAllTask());
	}

	static List<List<ConfigInfoChanged>> splitList(List<ConfigInfoChanged> list,
			int count) {
		List<List<ConfigInfoChanged>> result = new ArrayList<List<ConfigInfoChanged>>(
				count);
		for (int i = 0; i < count; i++) {
			result.add(new ArrayList<ConfigInfoChanged>());
		}
		for (int i = 0; i < list.size(); i++) {
			ConfigInfoChanged config = list.get(i);
			result.get(i % count).add(config);
		}
		return result;
	}

	class MergeAllDataWorker extends Thread {
		static final int PAGE_SIZE = 10000;

		private List<ConfigInfoChanged> configInfoList;

		public MergeAllDataWorker(List<ConfigInfoChanged> configInfoList) {
			super("MergeAllDataWorker");
			this.configInfoList = configInfoList;
		}

		@Override
		public void run() {
			if (!canExecute()) {
				return;
			}
			for (ConfigInfoChanged configInfo : configInfoList) {
				String dataId = configInfo.getDataId();
				String group = configInfo.getGroup();
				String tenant = configInfo.getTenant();
				try {
					List<ConfigInfoAggr> datumList = new ArrayList<ConfigInfoAggr>();
					int rowCount = persistService
							.aggrConfigInfoCount(dataId, group, tenant);
					int pageCount = (int) Math.ceil(rowCount * 1.0 / PAGE_SIZE);
					for (int pageNo = 1; pageNo <= pageCount; pageNo++) {
						Page<ConfigInfoAggr> page = persistService
								.findConfigInfoAggrByPage(dataId, group, tenant, pageNo,
										PAGE_SIZE);
						if (page != null) {
							datumList.addAll(page.getPageItems());
							log.info("[merge-query] {}, {}, size/total={}/{}", dataId,
									group, datumList.size(), rowCount);
						}
					}

					final Timestamp time = TimeUtils.getCurrentTime();
					// 聚合
					if (datumList.size() > 0) {
						ConfigInfo cf = MergeTaskProcessor
								.merge(dataId, group, tenant, datumList);
						String aggrContent = cf.getContent();
						String localContentMD5 = ConfigService
								.getContentMd5(GroupKey.getKey(dataId, group));
						String aggrConetentMD5 = MD5Utils
								.md5Hex(aggrContent, Constants.ENCODE);

						if (!StringUtils.equals(localContentMD5, aggrConetentMD5)) {
							persistService
									.insertOrUpdate(null, null, cf, time, null, false);
							log.info(
									"[merge-ok] {}, {}, size={}, length={}, md5={}, content={}",
									dataId, group, datumList.size(),
									cf.getContent().length(), cf.getMd5(),
									ContentUtils.truncateContent(cf.getContent()));
						}
					}
					// 删除
					else {
						persistService.removeConfigInfo(dataId, group, tenant,
								InetUtils.getSelfIp(), null);
						log.warn(
								"[merge-delete] delete config info because no datum. dataId="
										+ dataId + ", groupId=" + group);
					}

				}
				catch (Throwable e) {
					log.info("[merge-error] " + dataId + ", " + group + ", " + e
							.toString(), e);
				}
				FINISHED.incrementAndGet();
				if (FINISHED.get() % 100 == 0) {
					log.info("[all-merge-dump] {} / {}", FINISHED.get(), total);
				}
			}
			log.info("[all-merge-dump] {} / {}", FINISHED.get(), total);
		}
	}

	private boolean canExecute() {
		try {
			CPProtocol protocol = protocolManager.getCpProtocol();
			// if is derby + raft mode, only leader can execute
			if (PropertyUtil.isEmbeddedStorage() && !ApplicationUtils
					.getStandaloneMode()) {
				return protocol.isLeader(Constants.CONFIG_MODEL_RAFT_GROUP);
			}
			// If it is external storage, it determines whether it is the first node of the cluster
			return memberManager.isFirstIp();
		}
		catch (NoSuchRaftGroupException e) {
			return true;
		}
		catch (Throwable e) {
			// It's impossible to get to this point
			throw new RuntimeException(e);
		}
	}

	/**
	 * 全量dump间隔
	 */
	static final int DUMP_ALL_INTERVAL_IN_MINUTE = 6 * 60;
	/**
	 * 全量dump间隔
	 */
	static final int INITIAL_DELAY_IN_MINUTE = 6 * 60;

	private TaskManager dumpTaskMgr;
	private TaskManager dumpAllTaskMgr;

	private static final Logger log = LoggerFactory.getLogger(DumpService.class);

	static final AtomicInteger FINISHED = new AtomicInteger();

	static final int INIT_THREAD_COUNT = 10;
	int total = 0;
	private final static String TRUE_STR = "true";
	private final static String BETA_TABLE_NAME = "config_info_beta";
	private final static String TAG_TABLE_NAME = "config_info_tag";

	Boolean isQuickStart = false;

	private int retentionDays = 30;
}
