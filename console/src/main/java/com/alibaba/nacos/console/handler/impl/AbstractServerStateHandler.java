/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.handler.impl;

import com.alibaba.nacos.console.handler.ServerStateHandler;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.DiskUtils;

import java.io.File;

import static com.alibaba.nacos.common.utils.StringUtils.FOLDER_SEPARATOR;
import static com.alibaba.nacos.common.utils.StringUtils.TOP_PATH;
import static com.alibaba.nacos.common.utils.StringUtils.WINDOWS_FOLDER_SEPARATOR;

/**
 * Abstract Server state handler.
 *
 * @author xiweng.yy
 */
public abstract class AbstractServerStateHandler implements ServerStateHandler {
    
    private static final String ANNOUNCEMENT_FILE = "announcement.conf";
    
    private static final String GUIDE_FILE = "console-guide.conf";
    
    @Override
    public String getAnnouncement(String language) {
        String file = ANNOUNCEMENT_FILE.substring(0, ANNOUNCEMENT_FILE.length() - 5) + "_" + language + ".conf";
        if (file.contains(TOP_PATH) || file.contains(FOLDER_SEPARATOR) || file.contains(WINDOWS_FOLDER_SEPARATOR)) {
            throw new IllegalArgumentException("Invalid filename");
        }
        File announcementFile = new File(EnvUtil.getConfPath(), file);
        String announcement = null;
        if (announcementFile.exists() && announcementFile.isFile()) {
            announcement = DiskUtils.readFile(announcementFile);
        }
        return announcement;
    }
    
    @Override
    public String getConsoleUiGuide() {
        File guideFile = new File(EnvUtil.getConfPath(), GUIDE_FILE);
        String guideInformation = null;
        if (guideFile.exists() && guideFile.isFile()) {
            guideInformation = DiskUtils.readFile(guideFile);
        }
        return guideInformation;
    }
}
