/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.controller.v3;

import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.configuration.ConfigCommonConfig;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.paramcheck.ConfigDefaultHttpParamExtractor;
import com.alibaba.nacos.config.server.service.dump.DumpService;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.persistence.configuration.DatasourceConfiguration;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.datasource.LocalDataSourceServiceImpl;
import com.alibaba.nacos.persistence.model.event.DerbyImportEvent;
import com.alibaba.nacos.persistence.repository.embedded.operate.DatabaseOperate;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration ops management.
 *
 * @author Nacos
 */
@NacosApi
@RestController
@RequestMapping(Constants.OPS_CONTROLLER_V3_ADMIN_PATH)
@ExtractorManager.Extractor(httpExtractor = ConfigDefaultHttpParamExtractor.class)
public class ConfigOpsControllerV3 {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigOpsControllerV3.class);
    
    private final DumpService dumpService;
    
    public ConfigOpsControllerV3(DumpService dumpService) {
        this.dumpService = dumpService;
    }
    
    /**
     * Manually trigger dump of a local configuration file.
     */
    @PostMapping(value = "/localCache")
    @Secured(resource = Constants.OPS_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.WRITE,
            signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public Result<String> updateLocalCacheFromStore() {
        LOGGER.info("start to dump all data from store.");
        try {
            dumpService.dumpAll();
            return Result.success("Local cache updated from store successfully!");
        } catch (Exception e) {
            LOGGER.error("[updateLocalCacheFromStore] ", e);
            return Result.failure(ErrorCode.SERVER_ERROR.getCode(), "Local cache updated from store failed!", e.getMessage());
        }
    }
    
    @PutMapping(value = "/log")
    @Secured(resource = Constants.OPS_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.WRITE,
            signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public Result<String> setLogLevel(@RequestParam String logName, @RequestParam String logLevel) {
        try {
            LogUtil.setLogLevel(logName, logLevel);
            return Result.success(String.format("Log level updated successfully! Module: %s, Log Level: %s", logName, logLevel));
        } catch (Exception e) {
            LOGGER.error("Failed to set log level for module {} to {}", logName, logLevel, e);
            return Result.failure(ErrorCode.SERVER_ERROR.getCode(), String.format("Failed to set log level for module %s to %s: %s",
                    logName, logLevel, e.getMessage()), null);
        }
    }
    
    /**
     * Can only run select statements and is a direct query to the native Derby database without any additional logic.
     *
     * <p>
     *     This API is used for maintainer of Nacos to do datasource management when using derby datasource.
     *     So This API required ADMIN permission and need open switch `nacos.config.derby.ops.enabled=true`.
     * </p>
     *
     * @param sql The query
     * @return {@link RestResult}
     */
    @GetMapping(value = "/derby")
    @Secured(resource = Constants.OPS_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.WRITE,
            signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public Result<Object> derbyOps(@RequestParam(value = "sql") String sql) {
        String selectSign = "SELECT";
        String limitSign = "ROWS FETCH NEXT";
        String limit = " OFFSET 0 ROWS FETCH NEXT 1000 ROWS ONLY";
        try {
            if (!DatasourceConfiguration.isEmbeddedStorage()) {
                return Result.failure(ErrorCode.SERVER_ERROR.getCode(), "The current storage mode is not Derby", null);
            }
            if (!ConfigCommonConfig.getInstance().isDerbyOpsEnabled()) {
                return Result.failure(ErrorCode.SERVER_ERROR.getCode(),
                        "Derby ops is disabled, please set `nacos.config.derby.ops.enabled=true` to enabled this feature.", null);
            }
            
            LocalDataSourceServiceImpl dataSourceService = (LocalDataSourceServiceImpl) DynamicDataSource.getInstance()
                    .getDataSource();
            if (StringUtils.startsWithIgnoreCase(sql, selectSign)) {
                if (!StringUtils.containsIgnoreCase(sql, limitSign)) {
                    sql += limit;
                }
                JdbcTemplate template = dataSourceService.getJdbcTemplate();
                List<Map<String, Object>> result = template.queryForList(sql);
                return Result.success(result);
            }
            return Result.failure(ErrorCode.SERVER_ERROR.getCode(), "Only query statements are allowed to be executed", null);
        } catch (Exception e) {
            LOGGER.error("Derby failed to execute sql: " + sql);
            return Result.failure(ErrorCode.SERVER_ERROR.getCode(), "Failed to execute sql: " + sql, null);
        }
    }
    
    /**
     * Import Derby data from other Derby database.
     *
     * <p>mysqldump --defaults-file="XXX" --host=0.0.0.0 --protocol=tcp --user=XXX --extended-insert=FALSE \
     * --complete-insert=TRUE \ --skip-triggers --no-create-info --skip-column-statistics "{SCHEMA}" "{TABLE_NAME}"
     *
     * <p>
     *     This API is used for maintainer of Nacos to do datasource management when using derby datasource.
     *     So This API required ADMIN permission and need open switch `nacos.config.derby.ops.enabled=true`.
     * </p>
     *
     * @param multipartFile {@link MultipartFile}
     * @return {@link DeferredResult}
     */
    @PostMapping(value = "/derby/import")
    @Secured(resource = Constants.OPS_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.WRITE,
            signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public DeferredResult<Result<String>> importDerby(@RequestParam(value = "file") MultipartFile multipartFile) {
        DeferredResult<RestResult<String>> response = new DeferredResult<>();
        if (!DatasourceConfiguration.isEmbeddedStorage()) {
            response.setResult(RestResultUtils.failed("Limited to embedded storage mode"));
            return convertToResult(response);
        }
        if (!ConfigCommonConfig.getInstance().isDerbyOpsEnabled()) {
            response.setResult(RestResultUtils.failed(
                    "Derby ops is disabled, please set `nacos.config.derby.ops.enabled=true` to enabled this feature."));
            return convertToResult(response);
        }
        DatabaseOperate databaseOperate = ApplicationUtils.getBean(DatabaseOperate.class);
        WebUtils.onFileUpload(multipartFile, file -> {
            NotifyCenter.publishEvent(new DerbyImportEvent(false));
            databaseOperate.dataImport(file).whenComplete((result, ex) -> {
                NotifyCenter.publishEvent(new DerbyImportEvent(true));
                if (Objects.nonNull(ex)) {
                    response.setResult(RestResultUtils.failed(ex.getMessage()));
                    return;
                }
                response.setResult(result);
            });
        }, response);

        return convertToResult(response);
    }
    
    /**
     * Ensure backward compatibility.
     */
    private DeferredResult<Result<String>> convertToResult(DeferredResult<RestResult<String>> restResult) {
        DeferredResult<Result<String>> wrappedResponse = new DeferredResult<>();
        restResult.onCompletion(() -> {
            if (restResult.getResult() != null) {
                RestResult<String> originalResult = (RestResult<String>) restResult.getResult();
                Result<String> newResult = new Result<>(originalResult.getCode(), originalResult.getMessage(),
                        originalResult.getData());
                
                wrappedResponse.setResult(newResult);
            }
        });
        
        return wrappedResponse;
    }
}