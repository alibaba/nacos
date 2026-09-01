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
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

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
        File baseDir =
            new File(localRuleBaseDir, "data" + File.separator + "connection" + File.separator);
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
        File tpsFile = resolveTpsRuleFile(pointName);
        if (ruleContent == null) {
            DiskUtils.deleteQuietly(tpsFile);
        } else {
            if (!tpsFile.exists()) {
                tpsFile.createNewFile();
            }
            DiskUtils.writeFile(tpsFile, ruleContent.getBytes(Constants.ENCODE), false);
            LOGGER.info("Save tps rule to local,pointName={}, ruleContent ={} ", pointName,
                ruleContent);
            
        }
        
    }
    
    @Override
    public String getTpsRule(String pointName) {
        File tpsFile = resolveTpsRuleFile(pointName);
        if (!tpsFile.exists()) {
            return null;
        }
        return DiskUtils.readFile(tpsFile);
    }
    
    private File resolveTpsRuleFile(String pointName) {
        if (pointName == null || pointName.trim().isEmpty() || ".".equals(pointName)
            || "..".equals(pointName) || pointName.indexOf('/') >= 0
            || pointName.indexOf('\\') >= 0) {
            throw invalidPointNameException();
        }
        try {
            Path pointPath = Paths.get(pointName);
            Path basePath = checkTpsBaseDir().toPath().toAbsolutePath().normalize();
            Path targetPath = basePath.resolve(pointPath).normalize();
            if (pointPath.isAbsolute() || pointPath.getNameCount() != 1
                || !basePath.equals(targetPath.getParent())) {
                throw invalidPointNameException();
            }
            return targetPath.toFile();
        } catch (InvalidPathException e) {
            throw invalidPointNameException();
        }
    }
    
    private static IllegalArgumentException invalidPointNameException() {
        return new IllegalArgumentException("pointName must be a direct child file name");
    }
}
