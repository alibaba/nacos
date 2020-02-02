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

package com.alibaba.nacos.core.file;

import com.alibaba.nacos.core.executor.ExecutorFactory;
import com.alibaba.nacos.core.executor.NameThreadFactory;
import com.alibaba.nacos.core.notify.Event;
import com.alibaba.nacos.core.notify.NotifyManager;
import com.alibaba.nacos.core.notify.listener.Subscribe;
import com.alibaba.nacos.core.utils.Loggers;
import org.slf4j.Logger;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Unified file change monitoring management center, which uses {@link WatchService} internally.
 * One file directory corresponds to one {@link WatchService}. It can only monitor up to 32 file
 * directories. When a file change occurs, a {@link FileChangeEvent} will be issued, which will
 * be released by {@link NotifyManager}.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class WatchFileManager {

    private static final Logger logger = Loggers.WATCH_FILE;

    private static final int MAX_WATCH_FILE_JOB = 32;

    private static int NOW_WATCH_JOB_CNT = 0;

    private static final Map<String, WatchJob> MANAGER = new HashMap<>(MAX_WATCH_FILE_JOB);

    private static final FileSystem FILE_SYSTEM = FileSystems.getDefault();

    private static final Executor WATCH_FILE_EXECUTOR = ExecutorFactory.newFixExecutorService(
            WatchFileManager.class.getCanonicalName(),
            MAX_WATCH_FILE_JOB,
            new NameThreadFactory("com.alibaba.nacos.core.file.watch")
    );

    static {
        NotifyManager.registerPublisher(FileChangeEvent::new, FileChangeEvent.class);
    }

    public synchronized static boolean registerWatcher(final String paths, Consumer<FileChangeEvent> consumer) {
        NOW_WATCH_JOB_CNT ++;
        if (NOW_WATCH_JOB_CNT > MAX_WATCH_FILE_JOB) {
            return false;
        }
        try {
            WatchJob job = MANAGER.get(paths);
            if (Objects.isNull(job)) {
                WatchService service = FILE_SYSTEM.newWatchService();
                FILE_SYSTEM.getPath(paths).register(service,
                        StandardWatchEventKinds.OVERFLOW,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE
                );
                job = new WatchJob(paths, service);
                job.start();
            }
            job.addSubscribe(consumer);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public synchronized static boolean deregisterWatcher(final String path) {
        WatchJob job = MANAGER.get(path);
        if (Objects.nonNull(job)) {
            job.shutdown();
            MANAGER.remove(path);
            return true;
        }
        return false;
    }

    private static class WatchJob implements Runnable {

        private final String paths;

        private final WatchService watchService;

        private volatile boolean watch = true;

        public WatchJob(String paths, WatchService watchService) {
            this.paths = paths;
            this.watchService = watchService;
        }

        void addSubscribe(final Consumer<FileChangeEvent> consumer) {
            NotifyManager.registerSubscribe(new Subscribe<FileChangeEvent>() {
                @Override
                public void onEvent(FileChangeEvent event) {
                    consumer.accept(event);
                }

                @Override
                public Class<? extends Event> subscribeType() {
                    return FileChangeEvent.class;
                }
            });
        }

        void start() {
            WATCH_FILE_EXECUTOR.execute(this);
        }

        void shutdown() {
            watch = false;
        }

        @Override
        public void run() {
            while (watch) {
                try {
                    WatchKey watchKey = watchService.take();

                    for (WatchEvent<?> event : watchKey.pollEvents()) {
                        final FileChangeEvent fileChangeEvent = FileChangeEvent.builder()
                                .paths(paths)
                                .event(event)
                                .build();
                        NotifyManager.publishEvent(FileChangeEvent.class, fileChangeEvent);
                    }

                    if (!watchKey.reset()) {
                        break;
                    }
                } catch (InterruptedException e) {
                    Thread.interrupted();
                } catch (Exception e) {
                    logger.error("【{}】 File listening exception, error : {}", paths, e);
                }
            }
        }
    }
}
