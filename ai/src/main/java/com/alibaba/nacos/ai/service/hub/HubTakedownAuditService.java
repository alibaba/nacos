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

package com.alibaba.nacos.ai.service.hub;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.hub.HubResourceReference;
import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.ai.service.trace.AiResourceTraceService;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Emits Hub takedown audit events through Nacos's existing AI resource tracing.
 *
 * <p>Events follow a completed CAS. Trace delivery is asynchronous/best effort:
 * durable, transaction-coupled audit guarantees are outside the S1 persistence
 * contract and must be addressed separately if a deployment requires them.</p>
 */
@Service
public class HubTakedownAuditService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(HubTakedownAuditService.class);
    
    private static final String OP_HUB_UNSHARE = "HUB_UNSHARE";
    
    private static final String OP_HUB_FORCE_UNSHARE = "HUB_FORCE_UNSHARE";
    
    /**
     * Record a committed Hub deactivation, including administrator reason.
     *
     * @param hubEntryId immutable Hub entry ID
     * @param reference version reference that was deactivated
     * @param committedMetaVersion newly committed metadata version
     * @param administrative true for a forced governance action
     * @param reason administrator-provided reason, null for ordinary unshare
     */
    public void recordUnshare(long hubEntryId, HubResourceReference reference,
        long committedMetaVersion, boolean administrative, String reason) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("hubEntryId", hubEntryId);
        details.put("metaVersion", committedMetaVersion);
        details.put("sourceNamespaceId", reference.getSourceNamespaceId());
        details.put("sourceResourceId", reference.getSourceResourceId());
        details.put("sourceVersionId", reference.getSourceVersionId());
        if (administrative) {
            details.put("reason", reason);
        }
        try {
            AiResourceTraceService.logSuccess(AiResourceConstants.RESOURCE_TYPE_SKILL,
                "hub-entry-" + hubEntryId, reference.getSourceVersion(),
                administrative ? OP_HUB_FORCE_UNSHARE : OP_HUB_UNSHARE,
                VisibilityHelper.resolveCurrentIdentity(), VisibilityHelper.resolveClientIp(),
                JacksonUtils.toJson(details));
        } catch (RuntimeException traceFailure) {
            // Never turn a successful CAS into an apparent API failure. The
            // event may be retried out of band by an audit pipeline.
            LOGGER.warn("Hub takedown succeeded but trace emission failed, entryId={}",
                hubEntryId, traceFailure);
        }
    }
}
