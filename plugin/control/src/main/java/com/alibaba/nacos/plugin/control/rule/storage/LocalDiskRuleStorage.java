/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.control.rule.storage;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.utils.DiskUtils;
import com.alibaba.nacos.plugin.control.utils.EnvUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;

/**
 * local disk storage.
 *
 * @author shiyiyue
 */
public class LocalDiskRuleStorage implements RuleStorage {
    
    LocalDiskRuleStorage() {
    
    }
    
    private static final Logger LOGGER = Loggers.CONTROL;
    
    private String localRuleBaseDir = defaultBaseDir();
    
    private File checkTpsBaseDir() {
        File baseDir = new File(localRuleBaseDir, "data" + File.separator + "tps" + File.separator);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        return baseDir;
    }
    
    public void setLocalRuleBaseDir(String localRruleBaseDir) {
        this.localRuleBaseDir = localRruleBaseDir;
    }
    
    private static String defaultBaseDir() {
        return EnvUtils.getNacosHome();
    }
    
    private File getConnectionRuleFile() {
        File baseDir = new File(localRuleBaseDir, "data" + File.separator + "connection" + File.separator);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        return new File(baseDir, "limitRule");
    }
    
    @Override
    public String getName() {
        return "localdisk";
    }
    
    @Override
    public void saveConnectionRule(String ruleContent) throws IOException {
        File pointFile = getConnectionRuleFile();
        if (!pointFile.exists()) {
            pointFile.createNewFile();
        }
        DiskUtils.writeFile(pointFile, ruleContent.getBytes(Constants.ENCODE), false);
        LOGGER.info("Save connection rule to local, ruleContent ={} ", ruleContent);
    }
    
    @Override
    public String getConnectionRule() {
        File connectionRuleFile = getConnectionRuleFile();
        if (!connectionRuleFile.exists()) {
            return null;
        }
        return DiskUtils.readFile(connectionRuleFile);
    }
    
    @Override
    public void saveTpsRule(String pointName, String ruleContent) throws IOException {
        File file = checkTpsBaseDir();
        File tpsFile = new File(file, pointName);
        if (!tpsFile.exists()) {
            tpsFile.createNewFile();
        }
        if (ruleContent == null) {
            DiskUtils.deleteQuietly(tpsFile);
        } else {
            DiskUtils.writeFile(tpsFile, ruleContent.getBytes(Constants.ENCODE), false);
            LOGGER.info("Save tps rule to local,pointName={}, ruleContent ={} ", pointName, ruleContent);
            
        }
        
    }
    
    @Override
    public String getTpsRule(String pointName) {
        File file = checkTpsBaseDir();
        File tpsFile = new File(file, pointName);
        if (!tpsFile.exists()) {
            return null;
        }
        return DiskUtils.readFile(tpsFile);
    }
}
