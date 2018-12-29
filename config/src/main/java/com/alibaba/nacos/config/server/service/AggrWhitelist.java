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
package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.config.server.utils.RegexParser;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static com.alibaba.nacos.config.server.utils.LogUtil.defaultLog;
import static com.alibaba.nacos.config.server.utils.LogUtil.fatalLog;

/**
 * 聚合数据白名单。
 *
 * @author Nacos
 */
@Service
public class AggrWhitelist {

    /**
     * 判断指定的dataId是否在聚合dataId白名单。
     */
    static public boolean isAggrDataId(String dataId) {
        if (null == dataId) {
            throw new IllegalArgumentException();
        }

        for (Pattern pattern : AGGR_DATAID_WHITELIST.get()) {
            if (pattern.matcher(dataId).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 传入内容，重新加载聚合白名单
     */
    static public void load(String content) {
        if (StringUtils.isBlank(content)) {
            fatalLog.error("aggr dataId whitelist is blank.");
            return;
        }
        defaultLog.warn("[aggr-dataIds] {}", content);

        try {
            List<String> lines = IOUtils.readLines(new StringReader(content));
            compile(lines);
        } catch (Exception ioe) {
            defaultLog.error("failed to load aggr whitelist, " + ioe.toString(), ioe);
        }
    }

    static void compile(List<String> whitelist) {
        List<Pattern> list = new ArrayList<Pattern>(whitelist.size());

        for (String line : whitelist) {
            if (!StringUtils.isBlank(line)) {
                String regex = RegexParser.regexFormat(line.trim());
                list.add(Pattern.compile(regex));
            }
        }
        AGGR_DATAID_WHITELIST.set(list);
    }

    static public List<Pattern> getWhiteList() {
        return AGGR_DATAID_WHITELIST.get();
    }

    // =======================

    static public final String AGGRIDS_METADATA = "com.alibaba.nacos.metadata.aggrIDs";

    static final AtomicReference<List<Pattern>> AGGR_DATAID_WHITELIST = new AtomicReference<List<Pattern>>(
        new ArrayList<Pattern>());
}
