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
package com.alibaba.nacos.config.server.controller;

import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.service.datasource.DynamicDataSource;
import com.alibaba.nacos.config.server.service.datasource.LocalDataSourceServiceImpl;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.config.server.service.dump.DumpService;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

/**
 * 管理控制器。
 *
 * @author Nacos
 */
@RestController
@RequestMapping(Constants.OPS_CONTROLLER_PATH)
public class ConfigOpsController {

    private static final Logger log = LoggerFactory.getLogger(ConfigOpsController.class);

    protected final PersistService persistService;

    private final DumpService dumpService;

    @Autowired
    public ConfigOpsController(PersistService persistService, DumpService dumpService) {
        this.persistService = persistService;
        this.dumpService = dumpService;
    }

    /**
     * ops call
     */
    @PostMapping(value = "/localCache")
    public String updateLocalCacheFromStore() {
        log.info("start to dump all data from store.");
        dumpService.dumpAll();
        log.info("finish to dump all data from store.");
        return HttpServletResponse.SC_OK + "";
    }

    @PutMapping(value = "/log")
    public String setLogLevel(@RequestParam String logName, @RequestParam String logLevel) {
        LogUtil.setLogLevel(logName, logLevel);
        return HttpServletResponse.SC_OK + "";
    }

    // The interface to the Derby operations query can only run select statements
    // and is a direct query to the native Derby database without any additional logic

    // TODO In a future release, the front page should appear operable

    @GetMapping(value = "/derby")
    public RestResult<Object> derbyOps(@RequestParam(value = "sql") String sql) {
        String selectSign = "select";
        String limitSign = "ROWS FETCH NEXT";
        String limit = " OFFSET 0 ROWS FETCH NEXT 1000 ROWS ONLY";
        try {
            if (PropertyUtil.isEmbeddedStorage()) {
                LocalDataSourceServiceImpl dataSourceService = (LocalDataSourceServiceImpl) DynamicDataSource
                        .getInstance().getDataSource();
                if (StringUtils.startsWithIgnoreCase(sql, selectSign)) {
                    if (!StringUtils.containsIgnoreCase(sql, limitSign)) {
                        sql += limit;
                    }
                    JdbcTemplate template = dataSourceService.getJdbcTemplate();
                    List<Map<String, Object>> result = template.queryForList(sql);
                    return RestResultUtils.success(result);
                }
                return RestResultUtils.failed("Only query statements are allowed to be executed");
            }
            return RestResultUtils.failed("The current storage mode is not Derby");
        } catch (Exception e) {
            return RestResultUtils.failed(e.getMessage());
        }
    }

}
