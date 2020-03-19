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

package com.alibaba.nacos.core.util;

import com.alibaba.nacos.core.utils.DiskUtils;
import com.alipay.sofa.jraft.util.CRC64;
import org.junit.Test;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class DiskUtilsTest {

	@Test
	public void test_compress() throws Exception {
		String rootPath = "/Volumes/resources/LogDir/test_zip/";
		String sourcePath = "derby_data";
		String outFile = "/Volumes/resources/LogDir/test_zip/derby_data.zip";
		DiskUtils.compress(rootPath, sourcePath, outFile, new CRC64());
	}

}
