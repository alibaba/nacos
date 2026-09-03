/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service.a2a.migration;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.utils.ConfigPersistContext;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * Temporary compatibility support for migrating Nacos 3.0-3.2 A2A data.
 *
 * <p>TODO(remove in 4.0): remove after the historical A2A migration window closes.</p>
 *
 * @author Nacos
 */
@Component
public class A2aMigrationControlStore {
    
    public static final String MIGRATION_MARKER_DATA_ID = "nacos.ai.a2a.migration.v1";
    
    public static final String RECONCILIATION_LEASE_DATA_ID =
        "nacos.ai.a2a.reconciliation.lease.v1";
    
    public static final String RECONCILIATION_PROGRESS_DATA_ID =
        "nacos.ai.a2a.reconciliation.progress.v1";
    
    public static final String INTERNAL_GROUP = "nacos_internal";
    
    private final ConfigInfoPersistService configInfoPersistService;
    
    private final ConfigOperationService configOperationService;
    
    public A2aMigrationControlStore(ConfigInfoPersistService configInfoPersistService,
        ConfigOperationService configOperationService) {
        this.configInfoPersistService = configInfoPersistService;
        this.configOperationService = configOperationService;
    }
    
    /**
     * Read the authoritative migration marker and its Config MD5.
     *
     * @return versioned marker, or {@code null} when absent
     */
    public VersionedValue<A2aMigrationMarker> readMarker() {
        return read(MIGRATION_MARKER_DATA_ID,
            content -> JacksonUtils.toObj(content, A2aMigrationMarker.class));
    }
    
    /**
     * Create the initial marker without overwriting an existing plan.
     *
     * @param marker initial marker
     * @return whether Config accepted the write
     * @throws NacosException when the Config write fails
     */
    public boolean createMarker(A2aMigrationMarker marker) throws NacosException {
        return publish(MIGRATION_MARKER_DATA_ID, marker, false, null);
    }
    
    /**
     * Compare and set the migration marker.
     *
     * @param marker replacement marker
     * @param expectedMd5 expected Config MD5
     * @return whether Config accepted the write
     * @throws NacosException when the Config write fails
     */
    public boolean compareAndSetMarker(A2aMigrationMarker marker, String expectedMd5)
        throws NacosException {
        return publish(MIGRATION_MARKER_DATA_ID, marker, true, expectedMd5);
    }
    
    /**
     * Read the current renewable lease and its Config MD5.
     *
     * @return versioned lease, or {@code null} when absent
     */
    public VersionedValue<A2aMigrationLeaseRecord> readLease() {
        return read(RECONCILIATION_LEASE_DATA_ID,
            content -> JacksonUtils.toObj(content, A2aMigrationLeaseRecord.class));
    }
    
    /**
     * Create a lease without replacing another owner.
     *
     * @param lease lease value
     * @return whether Config accepted the write
     * @throws NacosException when the Config write fails
     */
    public boolean createLease(A2aMigrationLeaseRecord lease) throws NacosException {
        return publish(RECONCILIATION_LEASE_DATA_ID, lease, false, null);
    }
    
    /**
     * Compare and set a lease for takeover, renewal, or release.
     *
     * @param lease replacement lease
     * @param expectedMd5 expected Config MD5
     * @return whether Config accepted the write
     * @throws NacosException when the Config write fails
     */
    public boolean compareAndSetLease(A2aMigrationLeaseRecord lease, String expectedMd5)
        throws NacosException {
        return publish(RECONCILIATION_LEASE_DATA_ID, lease, true, expectedMd5);
    }
    
    /**
     * Persist bounded, non-authoritative reconciliation diagnostics.
     *
     * @param progress bounded progress value
     * @return whether Config accepted the write
     * @throws NacosException when the Config write fails
     */
    public boolean saveProgress(A2aMigrationProgress progress) throws NacosException {
        return publish(RECONCILIATION_PROGRESS_DATA_ID, progress, true, null);
    }
    
    private <T> VersionedValue<T> read(String dataId, Function<String, T> parser) {
        // TODO(remove in 4.0): migration CAS must read the authoritative persistence MD5.
        // Config query-chain results intentionally follow the asynchronous dump/cache path and
        // can briefly expose the previous MD5 immediately after an internal control write.
        ConfigInfoWrapper response = configInfoPersistService.findConfigInfo(dataId,
            INTERNAL_GROUP, Constants.AI_INTERNAL_STATE_NAMESPACE);
        if (response == null) {
            return null;
        }
        if (StringUtils.isBlank(response.getContent()) || StringUtils.isBlank(response.getMd5())) {
            throw new IllegalStateException("Unavailable A2A migration control object: " + dataId);
        }
        T value = parser.apply(response.getContent());
        if (value == null) {
            throw new IllegalStateException("Invalid A2A migration control object: " + dataId);
        }
        return new VersionedValue<>(value, response.getMd5());
    }
    
    private boolean publish(String dataId, Object value, boolean updateForExist,
        String expectedMd5) throws NacosException {
        ConfigRequestInfo requestInfo = new ConfigRequestInfo();
        requestInfo.setUpdateForExist(updateForExist);
        requestInfo.setCasMd5(expectedMd5);
        // TODO(remove in 4.0): migration control objects are current-state coordination records,
        // not user Config revisions. In particular, lease renewals and progress snapshots must not
        // continuously grow Config history while a long migration remains active.
        try (ConfigPersistContext.Guard ignored = ConfigPersistContext.withSkipHistory()) {
            return Boolean.TRUE.equals(configOperationService.publishConfig(
                internalForm(dataId, JacksonUtils.toJson(value)), requestInfo, null));
        }
    }
    
    private ConfigForm internalForm(String dataId, String content) {
        ConfigForm result = new ConfigForm();
        result.setNamespaceId(Constants.AI_INTERNAL_STATE_NAMESPACE);
        result.setGroup(INTERNAL_GROUP);
        result.setDataId(dataId);
        result.setContent(content);
        result.setType(ConfigType.JSON.getType());
        result.setSrcUser("nacos");
        return result;
    }
    
    /**
     * Value together with the Config MD5 required for compare-and-set.
     *
     * @param <T> control value type
     */
    public static final class VersionedValue<T> {
        
        private final T value;
        
        private final String md5;
        
        public VersionedValue(T value, String md5) {
            this.value = value;
            this.md5 = md5;
        }
        
        public T getValue() {
            return value;
        }
        
        public String getMd5() {
            return md5;
        }
    }
}
