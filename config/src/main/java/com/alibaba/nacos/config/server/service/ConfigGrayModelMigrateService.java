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
import com.alibaba.nacos.persistence.model.Page;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

import static com.alibaba.nacos.config.server.utils.LogUtil.DEFAULT_LOG;

/**
 * config gray model migrate.migrate beta and tag to gray model.
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
    
    @PostConstruct
    public void migrate() {
        doCheckMigrate();
    }
    
    private void doCheckMigrate() {
        int pageSize = 100;
        int rowCount = configInfoBetaPersistService.configInfoBetaCount();
        int pageCount = (int) Math.ceil(rowCount * 1.0 / pageSize);
        int actualRowCount = 0;
        for (int pageNo = 1; pageNo <= pageCount; pageNo++) {
            Page<ConfigInfoBetaWrapper> page = configInfoBetaPersistService.findAllConfigInfoBetaForDumpAll(pageNo,
                    pageSize);
            if (page != null) {
                for (ConfigInfoBetaWrapper cf : page.getPageItems()) {
                    
                    ConfigInfoGrayWrapper configInfo4Gray = configInfoGrayPersistService.findConfigInfo4Gray(
                            cf.getDataId(), cf.getGroup(), cf.getTenant(), BetaGrayRule.TYPE_BETA);
                    if (configInfo4Gray == null || configInfo4Gray.getLastModified() < cf.getLastModified()) {
                        DEFAULT_LOG.info("[migrate beta to gray] dataId={}, group={}, tenant={},  md5={}",
                                cf.getDataId(), cf.getGroup(), cf.getTenant(), cf.getMd5());
                        ConfigGrayPersistInfo localConfigGrayPersistInfo = new ConfigGrayPersistInfo(
                                BetaGrayRule.TYPE_BETA, BetaGrayRule.VERSION, cf.getBetaIps(), BetaGrayRule.PROPRITY);
                        configInfoGrayPersistService.insertOrUpdateGray(cf, BetaGrayRule.TYPE_BETA,
                                GrayRuleManager.serializeConfigGrayPersistInfo(localConfigGrayPersistInfo),
                                NetUtils.localIP(), "nacos_auto_migrate");
                    }
                    
                }
                actualRowCount += page.getPageItems().size();
            }
        }
        
        rowCount = configInfoBetaPersistService.configInfoBetaCount();
        pageCount = (int) Math.ceil(rowCount * 1.0 / pageSize);
        actualRowCount = 0;
        for (int pageNo = 1; pageNo <= pageCount; pageNo++) {
            Page<ConfigInfoTagWrapper> page = configInfoTagPersistService.findAllConfigInfoTagForDumpAll(pageNo,
                    pageSize);
            if (page != null) {
                for (ConfigInfoTagWrapper cf : page.getPageItems()) {
                    ConfigInfoGrayWrapper configInfo4Gray = configInfoGrayPersistService.findConfigInfo4Gray(
                            cf.getDataId(), cf.getGroup(), cf.getTenant(), TagGrayRule.TYPE_TAG + "_" + cf.getTag());
                    if (configInfo4Gray == null || configInfo4Gray.getLastModified() < cf.getLastModified()) {
                        DEFAULT_LOG.info("[migrate tag to gray] dataId={}, group={}, tenant={},  md5={}",
                                cf.getDataId(), cf.getGroup(), cf.getTenant(), cf.getMd5());
                        ConfigGrayPersistInfo localConfigGrayPersistInfo = new ConfigGrayPersistInfo(
                                TagGrayRule.TYPE_TAG, TagGrayRule.VERSION, cf.getTag(), TagGrayRule.PROPRITY);
                        configInfoGrayPersistService.insertOrUpdateGray(cf, TagGrayRule.TYPE_TAG + "_" + cf.getTag(),
                                GrayRuleManager.serializeConfigGrayPersistInfo(localConfigGrayPersistInfo),
                                NetUtils.localIP(), "nacos_auto_migrate");
                    }
                }
                
                actualRowCount += page.getPageItems().size();
                DEFAULT_LOG.info("[-tag] {} / {}", actualRowCount, rowCount);
            }
        }
    }
    
}
