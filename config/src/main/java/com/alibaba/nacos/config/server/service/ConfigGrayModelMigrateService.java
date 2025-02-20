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

import com.alibaba.nacos.api.utils.NetUtils;
import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.config.server.model.ConfigInfoBetaWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoGrayWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoTagWrapper;
import com.alibaba.nacos.config.server.model.gray.BetaGrayRule;
import com.alibaba.nacos.config.server.model.gray.ConfigGrayPersistInfo;
import com.alibaba.nacos.config.server.model.gray.GrayRuleManager;
import com.alibaba.nacos.config.server.model.gray.TagGrayRule;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoBetaPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoGrayPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoTagPersistService;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.config.server.utils.LogUtil.DEFAULT_LOG;
import static com.alibaba.nacos.config.server.utils.PropertyUtil.GRAY_MIGRATE_FLAG;

/**
 * migrate beta and tag to gray model. should only invoked from config sync notify.
 *
 * @author shiyiyue
 */
@Service
public class ConfigGrayModelMigrateService {
    
    ConfigInfoBetaPersistService configInfoBetaPersistService;
    
    ConfigInfoTagPersistService configInfoTagPersistService;
    
    ConfigInfoGrayPersistService configInfoGrayPersistService;
    
    public ConfigGrayModelMigrateService(ConfigInfoBetaPersistService configInfoBetaPersistService,
            ConfigInfoTagPersistService configInfoTagPersistService,
            ConfigInfoGrayPersistService configInfoGrayPersistService) {
        this.configInfoBetaPersistService = configInfoBetaPersistService;
        this.configInfoGrayPersistService = configInfoGrayPersistService;
        this.configInfoTagPersistService = configInfoTagPersistService;
    }
    
    /**
     * migrate beta&tag to gray .
     */
    @PostConstruct
    public void migrate() throws Exception {
        if (PropertyUtil.isGrayCompatibleModel()) {
            doCheckMigrate();
        }
    }
    
    /**
     * migrate single config beta.
     *
     * @param dataId dataId.
     * @param group  group.
     * @param tenant tenant.
     */
    public void checkMigrateBeta(String dataId, String group, String tenant) {
        ConfigInfoBetaWrapper configInfo4Beta = configInfoBetaPersistService.findConfigInfo4Beta(dataId, group, tenant);
        if (configInfo4Beta == null) {
            ConfigInfoGrayWrapper configInfoGrayWrapper = configInfoGrayPersistService.findConfigInfo4Gray(dataId,
                    group, tenant, BetaGrayRule.TYPE_BETA);
            if (configInfoGrayWrapper == null) {
                return;
            }
            configInfoGrayPersistService.removeConfigInfoGray(dataId, group, tenant, BetaGrayRule.TYPE_BETA,
                    NetUtils.localIP(), "nacos_auto_migrate");
            return;
        }
        ConfigInfoGrayWrapper configInfo4Gray = configInfoGrayPersistService.findConfigInfo4Gray(dataId, group, tenant,
                BetaGrayRule.TYPE_BETA);
        if (configInfo4Gray == null || configInfo4Gray.getLastModified() < configInfo4Beta.getLastModified()) {
            DEFAULT_LOG.info("[migrate beta to gray] dataId={}, group={}, tenant={},  md5={}",
                    configInfo4Beta.getDataId(), configInfo4Beta.getGroup(), configInfo4Beta.getTenant(),
                    configInfo4Beta.getMd5());
            ConfigGrayPersistInfo localConfigGrayPersistInfo = new ConfigGrayPersistInfo(BetaGrayRule.TYPE_BETA,
                    BetaGrayRule.VERSION, configInfo4Beta.getBetaIps(), BetaGrayRule.PRIORITY);
            configInfoGrayPersistService.insertOrUpdateGray(configInfo4Beta, BetaGrayRule.TYPE_BETA,
                    GrayRuleManager.serializeConfigGrayPersistInfo(localConfigGrayPersistInfo), NetUtils.localIP(),
                    "nacos_auto_migrate");
        }
        
    }
    
    /**
     * migrate single config tag.
     *
     * @param dataId dataId.
     * @param group  group.
     * @param tenant tenant.
     * @param tag    tag.
     */
    public void checkMigrateTag(String dataId, String group, String tenant, String tag) {
        ConfigInfoTagWrapper configInfo4Tag = configInfoTagPersistService.findConfigInfo4Tag(dataId, group, tenant,
                tag);
        if (configInfo4Tag == null) {
            ConfigInfoGrayWrapper configInfo4Gray = configInfoGrayPersistService.findConfigInfo4Gray(dataId, group,
                    tenant, TagGrayRule.TYPE_TAG + "_" + tag);
            if (configInfo4Gray == null) {
                return;
            }
            configInfoGrayPersistService.removeConfigInfoGray(dataId, group, tenant, TagGrayRule.TYPE_TAG + "_" + tag,
                    NetUtils.localIP(), "nacos_auto_migrate");
            return;
        }
        ConfigInfoGrayWrapper configInfo4Gray = configInfoGrayPersistService.findConfigInfo4Gray(dataId, group, tenant,
                TagGrayRule.TYPE_TAG + "_" + tag);
        if (configInfo4Gray == null || configInfo4Gray.getLastModified() < configInfo4Tag.getLastModified()) {
            DEFAULT_LOG.info("[migrate tag to gray] dataId={}, group={}, tenant={},  md5={}",
                    configInfo4Tag.getDataId(), configInfo4Tag.getGroup(), configInfo4Tag.getTenant(),
                    configInfo4Tag.getMd5());
            ConfigGrayPersistInfo localConfigGrayPersistInfo = new ConfigGrayPersistInfo(TagGrayRule.TYPE_TAG,
                    TagGrayRule.VERSION, configInfo4Tag.getTag(), TagGrayRule.PRIORITY);
            configInfoGrayPersistService.insertOrUpdateGray(configInfo4Tag, TagGrayRule.TYPE_TAG,
                    GrayRuleManager.serializeConfigGrayPersistInfo(localConfigGrayPersistInfo), NetUtils.localIP(),
                    "nacos_auto_migrate");
        }
    }
    
    private void doCheckMigrate() throws Exception {
        
        int migrateMulti = EnvUtil.getProperty("nacos.gray.migrate.executor.multi", Integer.class, Integer.valueOf(4));
        ThreadPoolExecutor executorService = new ThreadPoolExecutor(ThreadUtils.getSuitableThreadCount(migrateMulti),
                ThreadUtils.getSuitableThreadCount(migrateMulti), 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(PropertyUtil.getAllDumpPageSize() * migrateMulti),
                r -> new Thread(r, "gray-migrate-worker"), new ThreadPoolExecutor.CallerRunsPolicy());
        int pageSize = 100;
        int rowCount = configInfoBetaPersistService.configInfoBetaCount();
        int pageCount = (int) Math.ceil(rowCount * 1.0 / pageSize);
        int actualRowCount = 0;
        for (int pageNo = 1; pageNo <= pageCount; pageNo++) {
            Page<ConfigInfoBetaWrapper> page = configInfoBetaPersistService.findAllConfigInfoBetaForDumpAll(pageNo,
                    pageSize);
            if (page != null) {
                for (ConfigInfoBetaWrapper cf : page.getPageItems()) {
                    
                    executorService.execute(() -> {
                        GRAY_MIGRATE_FLAG.set(true);
                        ConfigInfoGrayWrapper configInfo4Gray = configInfoGrayPersistService.findConfigInfo4Gray(
                                cf.getDataId(), cf.getGroup(), cf.getTenant(), BetaGrayRule.TYPE_BETA);
                        if (configInfo4Gray == null || configInfo4Gray.getLastModified() < cf.getLastModified()) {
                            DEFAULT_LOG.info("[migrate beta to gray] dataId={}, group={}, tenant={},  md5={}",
                                    cf.getDataId(), cf.getGroup(), cf.getTenant(), cf.getMd5());
                            ConfigGrayPersistInfo localConfigGrayPersistInfo = new ConfigGrayPersistInfo(
                                    BetaGrayRule.TYPE_BETA, BetaGrayRule.VERSION, cf.getBetaIps(),
                                    BetaGrayRule.PRIORITY);
                            configInfoGrayPersistService.insertOrUpdateGray(cf, BetaGrayRule.TYPE_BETA,
                                    GrayRuleManager.serializeConfigGrayPersistInfo(localConfigGrayPersistInfo),
                                    NetUtils.localIP(), "nacos_auto_migrate");
                            GRAY_MIGRATE_FLAG.set(false);
                        }
                    });
                    
                }
                actualRowCount += page.getPageItems().size();
                DEFAULT_LOG.info("[gray-migrate-beta] submit gray task {} / {}", actualRowCount, rowCount);
                
            }
        }
        
        try {
            int unfinishedTaskCount = 0;
            while ((unfinishedTaskCount = executorService.getQueue().size() + executorService.getActiveCount()) > 0) {
                DEFAULT_LOG.info("[gray-migrate-beta] wait {} migrate tasks to be finished", unfinishedTaskCount);
                Thread.sleep(1000L);
            }
            
        } catch (Exception e) {
            DEFAULT_LOG.error("[gray-migrate-beta] wait  dump tasks to be finished error", e);
            throw e;
        }
        
        rowCount = configInfoTagPersistService.configInfoTagCount();
        pageCount = (int) Math.ceil(rowCount * 1.0 / pageSize);
        actualRowCount = 0;
        for (int pageNo = 1; pageNo <= pageCount; pageNo++) {
            Page<ConfigInfoTagWrapper> page = configInfoTagPersistService.findAllConfigInfoTagForDumpAll(pageNo,
                    pageSize);
            if (page != null) {
                for (ConfigInfoTagWrapper cf : page.getPageItems()) {
                    
                    executorService.execute(() -> {
                        GRAY_MIGRATE_FLAG.set(true);
                        ConfigInfoGrayWrapper configInfo4Gray = configInfoGrayPersistService.findConfigInfo4Gray(
                                cf.getDataId(), cf.getGroup(), cf.getTenant(),
                                TagGrayRule.TYPE_TAG + "_" + cf.getTag());
                        if (configInfo4Gray == null || configInfo4Gray.getLastModified() < cf.getLastModified()) {
                            DEFAULT_LOG.info("[migrate tag to gray] dataId={}, group={}, tenant={},  md5={}",
                                    cf.getDataId(), cf.getGroup(), cf.getTenant(), cf.getMd5());
                            ConfigGrayPersistInfo localConfigGrayPersistInfo = new ConfigGrayPersistInfo(
                                    TagGrayRule.TYPE_TAG, TagGrayRule.VERSION, cf.getTag(), TagGrayRule.PRIORITY);
                            configInfoGrayPersistService.insertOrUpdateGray(cf,
                                    TagGrayRule.TYPE_TAG + "_" + cf.getTag(),
                                    GrayRuleManager.serializeConfigGrayPersistInfo(localConfigGrayPersistInfo),
                                    NetUtils.localIP(), "nacos_auto_migrate");
                            GRAY_MIGRATE_FLAG.set(false);
                        }
                    });
                    
                }
                
                actualRowCount += page.getPageItems().size();
                DEFAULT_LOG.info("[gray-migrate-tag]  submit gray task  {} / {}", actualRowCount, rowCount);
            }
        }
        
        try {
            int unfinishedTaskCount = 0;
            while ((unfinishedTaskCount = executorService.getQueue().size() + executorService.getActiveCount()) > 0) {
                DEFAULT_LOG.info("[gray-migrate-tag] wait {} migrate tasks to be finished", unfinishedTaskCount);
                Thread.sleep(1000L);
            }
            
        } catch (Exception e) {
            DEFAULT_LOG.error("[gray-migrate-tag] wait migrate tasks to be finished error", e);
            throw e;
        }
        //shut down migrate executor
        executorService.shutdown();
        
    }
    
}
