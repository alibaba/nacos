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
package com.alibaba.nacos.client.config.impl;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.client.config.utils.*;
import com.alibaba.nacos.client.logger.Logger;
import com.alibaba.nacos.client.utils.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;


/**
 * Local Disaster Recovery Directory Tool
 * 
 * @author Nacos
 */
public class LocalConfigInfoProcessor {
	
	final static public Logger log = LogUtils.logger(LocalConfigInfoProcessor.class);

    static public String getFailover(String serverName, String dataId, String group, String tenant) {
    	File localPath = getFailoverFile(serverName, dataId, group, tenant);
    	if (!localPath.exists() || !localPath.isFile()) {
    		return null;
    	}
    	
    	try {
    		return readFile(localPath);
    	} catch (IOException ioe) {
    		log.error(serverName, "NACOS-XXXX","get failover error, " + localPath + ioe.toString());
    		return null;
    	}
    }
    
    /**
     * 获取本地缓存文件内容。NULL表示没有本地文件或抛出异常。
     */
    static public String getSnapshot(String name, String dataId, String group, String tenant) {
    	if (!SnapShotSwitch.getIsSnapShot()) {
    		return null;
    	}
    	File file = getSnapshotFile(name, dataId, group, tenant);
    	if (!file.exists() || !file.isFile()) {
    		return null;
    	}
    	
    	try {
    		return readFile(file);
    	} catch (IOException ioe) {
    		log.error(name, "NACOS-XXXX","get snapshot error, " + file + ", " + ioe.toString());
    		return null;
    	}
    }
    
	static private String readFile(File file) throws IOException {
		if (!file.exists() || !file.isFile()) {
			return null;
		}

		if (JVMUtil.isMultiInstance()) {
			return ConcurrentDiskUtil.getFileContent(file, Constants.ENCODE);
		} else {
			InputStream is = null;
			try {
				is = new FileInputStream(file);
				return IOUtils.toString(is, Constants.ENCODE);
			} finally {
				try {
					if (null != is) {
						is.close();
					}
				} catch (IOException ioe) {
				}
			}
		}
	}

    
    static public void saveSnapshot(String envName, String dataId, String group, String tenant, String config) {
		if (!SnapShotSwitch.getIsSnapShot()) {
			return;
		}
        File file = getSnapshotFile(envName, dataId, group, tenant);
        if (null == config) {
            try {
                IOUtils.delete(file);
            } catch (IOException ioe) {
                log.error(envName, "NACOS-XXXX","delete snapshot error, " + file + ", " + ioe.toString());
            }
        } else {
            try {
				boolean isMdOk = file.getParentFile().mkdirs();
				if (!isMdOk) {
					log.error(envName, "NACOS-XXXX", "save snapshot error");
				}
				if (JVMUtil.isMultiInstance()) {
					ConcurrentDiskUtil.writeFileContent(file, config,
							Constants.ENCODE);
				} else {
					IOUtils.writeStringToFile(file, config, Constants.ENCODE);
				}
            } catch (IOException ioe) {
                log.error(envName, "NACOS-XXXX","save snapshot error, " + file + ", " + ioe.toString());
            }
        }
    }
    
    /**
     * 清除snapshot目录下所有缓存文件。
     */
    static public void cleanAllSnapshot() {
        try {
        	File rootFile = new File(LOCAL_SNAPSHOT_PATH);
        	File[] files = rootFile.listFiles();
			if (files == null || files.length == 0) {
				return;
			}
        	for(File file : files){
        		if(file.getName().endsWith("_nacos")){
        			IOUtils.cleanDirectory(file);
        		}
        	}
        } catch (IOException ioe) {
            log.error("NACOS-XXXX","clean all snapshot error, " + ioe.toString(), ioe);
        }
    }
    
    static public void cleanEnvSnapshot(String envName){
    	File tmp = new File(LOCAL_SNAPSHOT_PATH, envName + "_nacos");
    	tmp = new File(tmp, "snapshot");
    	try {
			IOUtils.cleanDirectory(tmp);
			log.info("success dlelet " + envName + "-snapshot");
		} catch (IOException e) {
			log.info("fail dlelet " + envName + "-snapshot, " + e.toString());
			e.printStackTrace();
		}
    }
    

    static File getFailoverFile(String serverName, String dataId, String group, String tenant) {
    	File tmp = new File(LOCAL_SNAPSHOT_PATH, serverName + "_nacos");
    	tmp = new File(tmp, "data");
    	if (StringUtils.isBlank(tenant)) {
    		tmp = new File(tmp, "config-data");
    	} else
    	{
    		tmp = new File(tmp, "config-data-tenant");
    		tmp = new File(tmp, tenant);
    	}
    	return new File(new File(tmp, group), dataId);
    }
    
    static File getSnapshotFile(String envName, String dataId, String group, String tenant) {
		File tmp = new File(LOCAL_SNAPSHOT_PATH, envName + "_nacos");
		if (StringUtils.isBlank(tenant)) {
			tmp = new File(tmp, "snapshot");
		} else {
			tmp = new File(tmp, "snapshot-tenant");
			tmp = new File(tmp, tenant);
		}
    	
        return new File(new File(tmp, group), dataId);
    }
    
    public static final String LOCAL_FILEROOT_PATH;
    public static final String LOCAL_SNAPSHOT_PATH;
	static {
		LOCAL_FILEROOT_PATH = System.getProperty("JM.LOG.PATH", System.getProperty("user.home")) + File.separator
				+ "nacos" + File.separator + "config";
		LOCAL_SNAPSHOT_PATH = System.getProperty("JM.SNAPSHOT.PATH", System.getProperty("user.home")) + File.separator
				+ "nacos" + File.separator + "config";
		log.warn("LOCAL_SNAPSHOT_PATH:{}", LOCAL_SNAPSHOT_PATH);
	}

}
