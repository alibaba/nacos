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

import java.nio.file.WatchEvent;
import java.util.concurrent.Executor;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class FileWatcher {

    /**
     * Triggered when a file change occurs
     *
     * @param event {@link FileChangeEvent}
     */
    public abstract void onChange(FileChangeEvent event);

    /**
     * WatchEvent context information
     *
     * @param context {@link WatchEvent#context()}
     * @return is this watcher interest context
     */
    public abstract boolean interest(String context);

    /**
     * If the FileWatcher has its own thread pool, use this thread
     * pool to execute, otherwise use the WatchFileManager thread
     *
     * @return {@link Executor}
     */
    public Executor executor() {
        return null;
    }

}
