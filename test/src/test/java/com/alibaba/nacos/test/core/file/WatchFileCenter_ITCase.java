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

package com.alibaba.nacos.test.core.file;

import com.alibaba.nacos.core.file.FileChangeEvent;
import com.alibaba.nacos.core.file.FileWatcher;
import com.alibaba.nacos.core.file.WatchFileCenter;
import com.alibaba.nacos.common.utils.DiskUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class WatchFileCenter_ITCase {

	@Test
	public void test_watch_file_change() throws Exception {
		final String path = Paths.get(System.getProperty("user.home"), "/watch_file_change_test").toString();
		DiskUtils.forceMkdir(path);
		final String fileName = "test_file_change";
		final File file = Paths.get(path, fileName).toFile();

		CountDownLatch latch = new CountDownLatch(1);

		AtomicReference<String> reference = new AtomicReference<>(null);

		WatchFileCenter.registerWatcher(path, new FileWatcher() {
			@Override
			public void onChange(FileChangeEvent event) {
				final String content = DiskUtils.readFile(file);
				reference.set(content);
				System.out.println(content);
				latch.countDown();
			}

			@Override
			public boolean interest(String context) {
				return StringUtils.contains(context, fileName);
			}
		});


		DiskUtils.touch(file);
		DiskUtils.writeFile(file, fileName.getBytes(StandardCharsets.UTF_8), false);

		latch.await();

		Assert.assertEquals(reference.get(), fileName);
	}

}
