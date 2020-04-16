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

package com.alibaba.nacos.common.file;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.executor.NameThreadFactory;

import java.io.File;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Unified file change monitoring management center, which uses {@link WatchService} internally.
 * One file directory corresponds to one {@link WatchService}. It can only monitor up to 32 file
 * directories. When a file change occurs, a {@link FileChangeEvent} will be issued
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class WatchFileCenter {

	private static final int MAX_WATCH_FILE_JOB = Integer
			.getInteger("nacos.watch-file.max-dirs", 16);
	private static final Map<String, WatchJob> MANAGER = new HashMap<>(
			MAX_WATCH_FILE_JOB);
	private static final FileSystem FILE_SYSTEM = FileSystems.getDefault();
	private static final ExecutorService WATCH_FILE_EXECUTOR = ExecutorFactory
			.newFixExecutorService(WatchFileCenter.class.getCanonicalName(),
					MAX_WATCH_FILE_JOB << 1,
					new NameThreadFactory("com.alibaba.nacos.common.file.watch"));
	private static int NOW_WATCH_JOB_CNT = 0;

	public synchronized static boolean registerWatcher(final String paths,
			FileWatcher watcher) throws NacosException {
		NOW_WATCH_JOB_CNT++;
		if (NOW_WATCH_JOB_CNT > MAX_WATCH_FILE_JOB) {
			return false;
		}
		WatchJob job = MANAGER.get(paths);
		if (job == null) {
			job = new WatchJob(paths);
			WATCH_FILE_EXECUTOR.execute(job);
		}
		job.addSubscribe(watcher);
		return true;
	}

	public synchronized static boolean deregisterWatcher(final String path) {
		WatchJob job = MANAGER.get(path);
		if (job != null) {
			job.shutdown();
			MANAGER.remove(path);
			return true;
		}
		return false;
	}

	private static class WatchJob implements Runnable {

		private final String paths;

		private WatchService watchService;

		private volatile boolean watch = true;

		private Set<FileWatcher> watchers = new CopyOnWriteArraySet<>();

		public WatchJob(String paths) throws NacosException {
			this.paths = paths;

			if (!Paths.get(paths).toFile().isDirectory()) {
				throw new IllegalArgumentException("Must be a file directory : " + paths);
			}

			try {
				WatchService service = FILE_SYSTEM.newWatchService();
				Paths.get(paths).register(service, StandardWatchEventKinds.OVERFLOW,
						StandardWatchEventKinds.ENTRY_MODIFY,
						StandardWatchEventKinds.ENTRY_CREATE,
						StandardWatchEventKinds.ENTRY_DELETE);
				this.watchService = service;
			}
			catch (Throwable ex) {
				throw new NacosException(NacosException.SERVER_ERROR, ex);
			}
		}

		void addSubscribe(final FileWatcher watcher) {
			watchers.add(watcher);
		}

		void shutdown() {
			watch = false;
		}

		@Override
		public void run() {
			while (watch) {
				try {
					WatchKey watchKey = watchService.take();
					final List<WatchEvent<?>> events = watchKey.pollEvents();
					watchKey.reset();
					if (WATCH_FILE_EXECUTOR.isShutdown()) {
						return;
					}
					WATCH_FILE_EXECUTOR.execute(new Runnable() {
						@Override
						public void run() {
							for (WatchEvent<?> event : events) {
								WatchEvent.Kind<?> kind = event.kind();

								// Since the OS's event cache may be overflow, a backstop is needed
								if (StandardWatchEventKinds.OVERFLOW.equals(kind)) {
									eventOverflow();
								}
								else {
									eventProcess(event.context());
								}
							}
						}
					});
				}
				catch (InterruptedException ignore) {
					Thread.interrupted();
				}
			}
		}

		private void eventProcess(Object context) {
			final FileChangeEvent fileChangeEvent = FileChangeEvent.builder().paths(paths)
					.context(context).build();
			String str = String.valueOf(context);
			for (final FileWatcher watcher : watchers) {
				if (watcher.interest(str)) {
					Runnable job = new Runnable() {
						@Override
						public void run() {
							watcher.onChange(fileChangeEvent);
						}
					};
					Executor executor = watcher.executor();
					if (executor == null) {
						job.run();
					}
					else {
						executor.execute(job);
					}
				}
			}
		}

		private void eventOverflow() {
			File dir = Paths.get(paths).toFile();
			for (File file : Objects.requireNonNull(dir.listFiles())) {
				if (file.isDirectory()) {
					continue;
				}
				eventProcess(file.getName());
			}
		}

	}
}
