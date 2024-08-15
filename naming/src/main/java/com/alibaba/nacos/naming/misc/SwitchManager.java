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

package com.alibaba.nacos.naming.misc;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.common.utils.ByteUtils;
import com.alibaba.nacos.common.utils.ConvertUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.TypeUtils;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.RequestProcessor4CP;
import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.core.exception.ErrorCode;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.persistent.impl.BatchReadResponse;
import com.alibaba.nacos.naming.consistency.persistent.impl.BatchWriteRequest;
import com.alibaba.nacos.naming.consistency.persistent.impl.OldDataOperation;
import com.alibaba.nacos.naming.pojo.Record;
import com.alibaba.nacos.sys.utils.DiskUtils;
import com.google.protobuf.ByteString;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Switch manager.
 *
 * @author nkorange
 * @since 1.0.0
 */
@Component
public class SwitchManager extends RequestProcessor4CP {
    
    private final SwitchDomain switchDomain;
    
    private final ProtocolManager protocolManager;
    
    private final ReentrantReadWriteLock raftLock;
    
    private final ReentrantLock requestLock;
    
    private final Serializer serializer;
    
    private final SwitchDomainSnapshotOperation snapshotOperation;
    
    private final File dataFile;
    
    public SwitchManager(SwitchDomain switchDomain, ProtocolManager protocolManager) {
        this.switchDomain = switchDomain;
        this.protocolManager = protocolManager;
        this.raftLock = new ReentrantReadWriteLock();
        this.requestLock = new ReentrantLock();
        this.serializer = SerializeFactory.getSerializer("JSON");
        this.snapshotOperation = new SwitchDomainSnapshotOperation(this.raftLock, this, this.serializer);
        this.dataFile = Paths.get(UtilsAndCommons.DATA_BASE_DIR, "data", KeyBuilder.getSwitchDomainKey()).toFile();
        try {
            DiskUtils.forceMkdir(this.dataFile.getParent());
        } catch (IOException e) {
            Loggers.RAFT.error("Init Switch Domain directory failed: ", e);
        }
        protocolManager.getCpProtocol().addRequestProcessors(Collections.singletonList(this));
    }
    
    /**
     * Update switch information.
     *
     * @param entry item entry of switch, {@link SwitchEntry}
     * @param value switch value
     * @param debug whether debug
     * @throws Exception exception
     */
    public void update(String entry, String value, boolean debug) throws Exception {
        
        this.requestLock.lock();
        try {
            
            SwitchDomain tempSwitchDomain = this.switchDomain.clone();
            
            if (SwitchEntry.BATCH.equals(entry)) {
                //batch update
                SwitchDomain dom = JacksonUtils.toObj(value, SwitchDomain.class);
                dom.setEnableStandalone(tempSwitchDomain.isEnableStandalone());
                if (dom.getHttpHealthParams().getMin() < SwitchDomain.HttpHealthParams.MIN_MIN
                        || dom.getTcpHealthParams().getMin() < SwitchDomain.HttpHealthParams.MIN_MIN) {
                    
                    throw new IllegalArgumentException("min check time for http or tcp is too small(<500)");
                }
                
                if (dom.getHttpHealthParams().getMax() < SwitchDomain.HttpHealthParams.MIN_MAX
                        || dom.getTcpHealthParams().getMax() < SwitchDomain.HttpHealthParams.MIN_MAX) {
                    
                    throw new IllegalArgumentException("max check time for http or tcp is too small(<3000)");
                }
                
                if (dom.getHttpHealthParams().getFactor() < 0 || dom.getHttpHealthParams().getFactor() > 1
                        || dom.getTcpHealthParams().getFactor() < 0 || dom.getTcpHealthParams().getFactor() > 1) {
                    
                    throw new IllegalArgumentException("malformed factor");
                }
                
                tempSwitchDomain = dom;
            }
            
            if (entry.equals(SwitchEntry.DISTRO_THRESHOLD)) {
                float threshold = Float.parseFloat(value);
                if (threshold <= 0) {
                    throw new IllegalArgumentException("distroThreshold can not be zero or negative: " + threshold);
                }
                tempSwitchDomain.setDistroThreshold(threshold);
            }
            
            if (entry.equals(SwitchEntry.CLIENT_BEAT_INTERVAL)) {
                long clientBeatInterval = Long.parseLong(value);
                tempSwitchDomain.setClientBeatInterval(clientBeatInterval);
            }
            
            if (entry.equals(SwitchEntry.PUSH_VERSION)) {
                
                String type = value.split(":")[0];
                String version = value.split(":")[1];
                
                if (!version.matches(UtilsAndCommons.VERSION_STRING_SYNTAX)) {
                    throw new IllegalArgumentException(
                            "illegal version, must match: " + UtilsAndCommons.VERSION_STRING_SYNTAX);
                }
                
                if (StringUtils.equals(SwitchEntry.CLIENT_JAVA, type)) {
                    tempSwitchDomain.setPushJavaVersion(version);
                } else if (StringUtils.equals(SwitchEntry.CLIENT_PYTHON, type)) {
                    tempSwitchDomain.setPushPythonVersion(version);
                } else if (StringUtils.equals(SwitchEntry.CLIENT_C, type)) {
                    tempSwitchDomain.setPushCVersion(version);
                } else if (StringUtils.equals(SwitchEntry.CLIENT_GO, type)) {
                    tempSwitchDomain.setPushGoVersion(version);
                } else {
                    throw new IllegalArgumentException("unsupported client type: " + type);
                }
            }
            
            if (entry.equals(SwitchEntry.PUSH_CACHE_MILLIS)) {
                long cacheMillis = Long.parseLong(value);
                
                if (cacheMillis < SwitchEntry.MIN_PUSH_CACHE_TIME_MIILIS) {
                    throw new IllegalArgumentException("min cache time for http or tcp is too small(<10000)");
                }
                
                tempSwitchDomain.setDefaultPushCacheMillis(cacheMillis);
            }
            
            // extremely careful while modifying this, cause it will affect all clients without pushing enabled
            if (entry.equals(SwitchEntry.DEFAULT_CACHE_MILLIS)) {
                long cacheMillis = Long.parseLong(value);
                
                if (cacheMillis < SwitchEntry.MIN_CACHE_TIME_MIILIS) {
                    throw new IllegalArgumentException("min default cache time  is too small(<1000)");
                }
                
                tempSwitchDomain.setDefaultCacheMillis(cacheMillis);
            }
            
            if (entry.equals(SwitchEntry.MASTERS)) {
                List<String> masters = Arrays.asList(value.split(","));
                tempSwitchDomain.setMasters(masters);
            }
            
            if (entry.equals(SwitchEntry.DISTRO)) {
                boolean enabled = Boolean.parseBoolean(value);
                tempSwitchDomain.setDistroEnabled(enabled);
            }
            
            if (entry.equals(SwitchEntry.CHECK)) {
                boolean enabled = Boolean.parseBoolean(value);
                tempSwitchDomain.setHealthCheckEnabled(enabled);
            }
            
            if (entry.equals(SwitchEntry.PUSH_ENABLED)) {
                boolean enabled = Boolean.parseBoolean(value);
                tempSwitchDomain.setPushEnabled(enabled);
            }
            
            if (entry.equals(SwitchEntry.SERVICE_STATUS_SYNC_PERIOD)) {
                long millis = Long.parseLong(value);
                
                if (millis < SwitchEntry.MIN_SERVICE_SYNC_TIME_MIILIS) {
                    throw new IllegalArgumentException("serviceStatusSynchronizationPeriodMillis is too small(<5000)");
                }
                
                tempSwitchDomain.setServiceStatusSynchronizationPeriodMillis(millis);
            }
            
            if (entry.equals(SwitchEntry.SERVER_STATUS_SYNC_PERIOD)) {
                long millis = Long.parseLong(value);
                
                if (millis < SwitchEntry.MIN_SERVER_SYNC_TIME_MIILIS) {
                    throw new IllegalArgumentException("serverStatusSynchronizationPeriodMillis is too small(<15000)");
                }
                
                tempSwitchDomain.setServerStatusSynchronizationPeriodMillis(millis);
            }
            
            if (entry.equals(SwitchEntry.HEALTH_CHECK_TIMES)) {
                int times = Integer.parseInt(value);
                
                tempSwitchDomain.setCheckTimes(times);
            }
            
            if (entry.equals(SwitchEntry.DISABLE_ADD_IP)) {
                boolean disableAddIp = Boolean.parseBoolean(value);
                
                tempSwitchDomain.setDisableAddIP(disableAddIp);
            }
            
            if (entry.equals(SwitchEntry.SEND_BEAT_ONLY)) {
                boolean sendBeatOnly = Boolean.parseBoolean(value);
                
                tempSwitchDomain.setSendBeatOnly(sendBeatOnly);
            }
            
            if (entry.equals(SwitchEntry.LIMITED_URL_MAP)) {
                Map<String, Integer> limitedUrlMap = new HashMap<>(16);
                
                if (!StringUtils.isEmpty(value)) {
                    String[] entries = value.split(",");
                    for (String each : entries) {
                        String[] parts = each.split(":");
                        if (parts.length < 2) {
                            throw new IllegalArgumentException("invalid input for limited urls");
                        }
                        
                        String limitedUrl = parts[0];
                        if (StringUtils.isEmpty(limitedUrl)) {
                            throw new IllegalArgumentException("url can not be empty, url: " + limitedUrl);
                        }
                        
                        int statusCode = Integer.parseInt(parts[1]);
                        if (statusCode <= 0) {
                            throw new IllegalArgumentException("illegal normal status code: " + statusCode);
                        }
                        
                        limitedUrlMap.put(limitedUrl, statusCode);
                        
                    }
                    
                    tempSwitchDomain.setLimitedUrlMap(limitedUrlMap);
                }
            }
            
            if (entry.equals(SwitchEntry.ENABLE_STANDALONE)) {
                
                if (!StringUtils.isNotEmpty(value)) {
                    tempSwitchDomain.setEnableStandalone(Boolean.parseBoolean(value));
                }
            }
            
            if (entry.equals(SwitchEntry.OVERRIDDEN_SERVER_STATUS)) {
                String status = value;
                if (Constants.NULL_STRING.equals(status)) {
                    status = StringUtils.EMPTY;
                }
                tempSwitchDomain.setOverriddenServerStatus(status);
            }
            
            if (entry.equals(SwitchEntry.DEFAULT_INSTANCE_EPHEMERAL)) {
                tempSwitchDomain.setDefaultInstanceEphemeral(Boolean.parseBoolean(value));
            }
            
            if (entry.equals(SwitchEntry.DISTRO_SERVER_EXPIRED_MILLIS)) {
                tempSwitchDomain.setDistroServerExpiredMillis(Long.parseLong(value));
            }
            
            if (entry.equals(SwitchEntry.LIGHT_BEAT_ENABLED)) {
                tempSwitchDomain.setLightBeatEnabled(ConvertUtils.toBoolean(value));
            }
            
            if (entry.equals(SwitchEntry.AUTO_CHANGE_HEALTH_CHECK_ENABLED)) {
                tempSwitchDomain.setAutoChangeHealthCheckEnabled(ConvertUtils.toBoolean(value));
            }
            
            if (debug) {
                update(tempSwitchDomain);
            } else {
                updateWithConsistency(tempSwitchDomain);
            }
            
        } finally {
            this.requestLock.unlock();
        }
        
    }
    
    /**
     * Update switch information from new switch domain.
     *
     * @param newSwitchDomain new switch domain
     */
    public void update(SwitchDomain newSwitchDomain) {
        switchDomain.setMasters(newSwitchDomain.getMasters());
        switchDomain.setAdWeightMap(newSwitchDomain.getAdWeightMap());
        switchDomain.setDefaultPushCacheMillis(newSwitchDomain.getDefaultPushCacheMillis());
        switchDomain.setClientBeatInterval(newSwitchDomain.getClientBeatInterval());
        switchDomain.setDefaultCacheMillis(newSwitchDomain.getDefaultCacheMillis());
        switchDomain.setDistroThreshold(newSwitchDomain.getDistroThreshold());
        switchDomain.setHealthCheckEnabled(newSwitchDomain.isHealthCheckEnabled());
        switchDomain.setAutoChangeHealthCheckEnabled(newSwitchDomain.isAutoChangeHealthCheckEnabled());
        switchDomain.setDistroEnabled(newSwitchDomain.isDistroEnabled());
        switchDomain.setPushEnabled(newSwitchDomain.isPushEnabled());
        switchDomain.setEnableStandalone(newSwitchDomain.isEnableStandalone());
        switchDomain.setCheckTimes(newSwitchDomain.getCheckTimes());
        switchDomain.setHttpHealthParams(newSwitchDomain.getHttpHealthParams());
        switchDomain.setTcpHealthParams(newSwitchDomain.getTcpHealthParams());
        switchDomain.setMysqlHealthParams(newSwitchDomain.getMysqlHealthParams());
        switchDomain.setIncrementalList(newSwitchDomain.getIncrementalList());
        switchDomain.setServerStatusSynchronizationPeriodMillis(
                newSwitchDomain.getServerStatusSynchronizationPeriodMillis());
        switchDomain.setServiceStatusSynchronizationPeriodMillis(
                newSwitchDomain.getServiceStatusSynchronizationPeriodMillis());
        switchDomain.setDisableAddIP(newSwitchDomain.isDisableAddIP());
        switchDomain.setSendBeatOnly(newSwitchDomain.isSendBeatOnly());
        switchDomain.setLimitedUrlMap(newSwitchDomain.getLimitedUrlMap());
        switchDomain.setDistroServerExpiredMillis(newSwitchDomain.getDistroServerExpiredMillis());
        switchDomain.setPushGoVersion(newSwitchDomain.getPushGoVersion());
        switchDomain.setPushJavaVersion(newSwitchDomain.getPushJavaVersion());
        switchDomain.setPushPythonVersion(newSwitchDomain.getPushPythonVersion());
        switchDomain.setPushCVersion(newSwitchDomain.getPushCVersion());
        switchDomain.setEnableAuthentication(newSwitchDomain.isEnableAuthentication());
        switchDomain.setOverriddenServerStatus(newSwitchDomain.getOverriddenServerStatus());
        switchDomain.setDefaultInstanceEphemeral(newSwitchDomain.isDefaultInstanceEphemeral());
        switchDomain.setLightBeatEnabled(newSwitchDomain.isLightBeatEnabled());
    }
    
    private void updateWithConsistency(SwitchDomain tempSwitchDomain) throws NacosException {
        try {
            final BatchWriteRequest req = new BatchWriteRequest();
            String switchDomainKey = KeyBuilder.getSwitchDomainKey();
            Datum datum = Datum.createDatum(switchDomainKey, tempSwitchDomain);
            req.append(ByteUtils.toBytes(switchDomainKey), serializer.serialize(datum));
            WriteRequest operationLog = WriteRequest.newBuilder().setGroup(group())
                    .setOperation(OldDataOperation.Write.getDesc()).setData(ByteString.copyFrom(serializer.serialize(req)))
                    .build();
            protocolManager.getCpProtocol().write(operationLog);
        } catch (Exception e) {
            Loggers.RAFT.error("Submit switch domain failed: ", e);
            throw new NacosException(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage());
        }
    }
    
    public SwitchDomain getSwitchDomain() {
        return switchDomain;
    }
    
    @Override
    public List<SnapshotOperation> loadSnapshotOperate() {
        return Collections.singletonList(snapshotOperation);
    }
    
    /**
     * Load Snapshot from snapshot dir.
     *
     * @param snapshotPath snapshot dir
     */
    public void loadSnapshot(String snapshotPath) {
        this.raftLock.writeLock().lock();
        try {
            File srcDir = Paths.get(snapshotPath).toFile();
            // If snapshot path is non-exist, means snapshot is empty
            if (srcDir.exists()) {
                // First clean up the local file information, before the file copy
                String baseDir = this.dataFile.getParent();
                DiskUtils.deleteDirThenMkdir(baseDir);
                File descDir = Paths.get(baseDir).toFile();
                DiskUtils.copyDirectory(srcDir, descDir);
                if (!this.dataFile.exists()) {
                    return;
                }
                byte[] snapshotData = DiskUtils.readFileBytes(this.dataFile);
                final Datum datum = serializer.deserialize(snapshotData, getDatumType());
                final Record value = null != datum ? datum.value : null;
                if (!(value instanceof SwitchDomain)) {
                    return;
                }
                update((SwitchDomain) value);
            }
        } catch (IOException e) {
            throw new NacosRuntimeException(ErrorCode.IOCopyDirError.getCode(), e);
        } finally {
            this.raftLock.writeLock().unlock();
        }
    }
    
    /**
     * Dump data from data dir to snapshot dir.
     *
     * @param backupPath snapshot dir
     */
    public void dumpSnapshot(String backupPath) {
        this.raftLock.writeLock().lock();
        try {
            File srcDir = Paths.get(this.dataFile.getParent()).toFile();
            File descDir = Paths.get(backupPath).toFile();
            DiskUtils.copyDirectory(srcDir, descDir);
        } catch (IOException e) {
            throw new NacosRuntimeException(ErrorCode.IOCopyDirError.getCode(), e);
        } finally {
            this.raftLock.writeLock().unlock();
        }
    }
    
    @Override
    public Response onRequest(ReadRequest request) {
        this.raftLock.readLock().lock();
        try {
            final List<byte[]> keys = serializer.deserialize(request.getData().toByteArray(),
                    TypeUtils.parameterize(List.class, byte[].class));
            if (isNotSwitchDomainKey(keys)) {
                return Response.newBuilder().setSuccess(false).setErrMsg("not switch domain key").build();
            }
            Datum datum = Datum.createDatum(KeyBuilder.getSwitchDomainKey(), switchDomain);
            final BatchReadResponse response = new BatchReadResponse();
            response.append(ByteUtils.toBytes(KeyBuilder.getSwitchDomainKey()), serializer.serialize(datum));
            return Response.newBuilder().setSuccess(true).setData(ByteString.copyFrom(serializer.serialize(response)))
                    .build();
        } catch (Exception e) {
            Loggers.RAFT.warn("On read switch domain failed, ", e);
            return Response.newBuilder().setSuccess(false).setErrMsg(e.getMessage()).build();
        } finally {
            this.raftLock.readLock().unlock();
        }
    }
    
    @Override
    public Response onApply(WriteRequest log) {
        this.raftLock.writeLock().lock();
        try {
            BatchWriteRequest bwRequest = serializer.deserialize(log.getData().toByteArray(), BatchWriteRequest.class);
            if (isNotSwitchDomainKey(bwRequest.getKeys())) {
                return Response.newBuilder().setSuccess(false).setErrMsg("not switch domain key").build();
            }
            final Datum datum = serializer.deserialize(bwRequest.getValues().get(0), getDatumType());
            final Record value = null != datum ? datum.value : null;
            if (!(value instanceof SwitchDomain)) {
                return Response.newBuilder().setSuccess(false).setErrMsg("datum is not switch domain").build();
            }
            DiskUtils.touch(dataFile);
            DiskUtils.writeFile(dataFile, bwRequest.getValues().get(0), false);
            SwitchDomain switchDomain = (SwitchDomain) value;
            update(switchDomain);
            return Response.newBuilder().setSuccess(true).build();
        } catch (Exception e) {
            Loggers.RAFT.warn("On apply switch domain failed, ", e);
            return Response.newBuilder().setSuccess(false).setErrMsg(e.getMessage()).build();
        } finally {
            this.raftLock.writeLock().unlock();
        }
    }
    
    @Override
    public String group() {
        return com.alibaba.nacos.naming.constants.Constants.NAMING_PERSISTENT_SERVICE_GROUP;
    }
    
    private boolean isNotSwitchDomainKey(List<byte[]> keys) {
        if (1 != keys.size()) {
            return false;
        }
        String keyString = new String(keys.get(0));
        return !KeyBuilder.getSwitchDomainKey().equals(keyString);
    }
    
    private Type getDatumType() {
        return TypeUtils.parameterize(Datum.class, SwitchDomain.class);
    }
}
