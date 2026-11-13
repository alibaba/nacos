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

package com.alibaba.nacos.client.ai;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.skills.SkillBasicInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.client.ai.remote.AiHttpClientProxy;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounded pagination across the configured namespace and optional shared Skill catalogue.
 *
 * @author nacos
 */
final class SkillDiscoveryClient {
    
    private final AiHttpClientProxy proxy;
    
    private final String namespaceId;
    
    private final boolean accessHub;
    
    SkillDiscoveryClient(AiHttpClientProxy proxy, String namespaceId, boolean accessHub) {
        this.proxy = proxy;
        this.namespaceId = namespaceId;
        this.accessHub = accessHub;
    }
    
    Page<SkillBasicInfo> search(String query, List<String> tagsAll, int pageNo, int pageSize)
        throws NacosException {
        if (pageNo < 1 || pageSize < 1 || pageSize > 100
            || (long) pageNo * pageSize > Integer.MAX_VALUE) {
            throw new NacosException(NacosException.INVALID_PARAM,
                "Skill search pageNo must be positive, pageSize must be 1-100, and paging must fit integer range.");
        }
        Page<SkillBasicInfo> local = query(namespaceId, query, tagsAll, pageNo, pageSize);
        if (!accessHub || AiConstants.Hub.NAMESPACE_ID.equals(namespaceId)) {
            return local;
        }
        // Read a bounded metadata page even if this combined page contains only local items.
        Page<SkillBasicInfo> shared = query(AiConstants.Hub.NAMESPACE_ID, query, tagsAll, 1, 1);
        long total = (long) local.getTotalCount() + shared.getTotalCount();
        if (total > Integer.MAX_VALUE) {
            throw new NacosException(NacosException.SERVER_ERROR,
                "Combined Skill search total exceeds supported page range.");
        }
        List<SkillBasicInfo> items = new ArrayList<>(local.getPageItems());
        int offset = Math.max(0, (pageNo - 1) * pageSize - local.getTotalCount());
        int remaining = Math.min(pageSize - items.size(), shared.getTotalCount() - offset);
        while (remaining > 0) {
            int sharedPage = offset / pageSize + 1;
            int withinPage = offset % pageSize;
            Page<SkillBasicInfo> source = query(AiConstants.Hub.NAMESPACE_ID, query, tagsAll,
                sharedPage, pageSize);
            int take = Math.min(remaining, source.getPageItems().size() - withinPage);
            if (take <= 0) {
                // A concurrent removal can shorten the source page; never loop or duplicate it.
                break;
            }
            items.addAll(source.getPageItems().subList(withinPage, withinPage + take));
            offset += take;
            remaining -= take;
        }
        Page<SkillBasicInfo> result = new Page<>();
        result.setPageNumber(pageNo);
        result.setTotalCount((int) total);
        result.setPagesAvailable((int) ((total + pageSize - 1) / pageSize));
        result.setPageItems(items);
        return result;
    }
    
    byte[] download(SkillBasicInfo skill) throws NacosException {
        if (skill == null || StringUtils.isBlank(skill.getName())) {
            throw new NacosException(NacosException.INVALID_PARAM,
                "A Skill search result with a name is required.");
        }
        if ("HUB".equals(skill.getDiscoverySource())) {
            if (skill.getHubEntryId() == null || skill.getHubEntryId() <= 0
                || StringUtils.isBlank(skill.getVersion())) {
                throw new NacosException(NacosException.INVALID_PARAM,
                    "Shared Skill search result requires hubEntryId and pinned version.");
            }
            return proxy.downloadSkillZip(AiConstants.Hub.NAMESPACE_ID,
                String.valueOf(skill.getHubEntryId()), skill.getVersion(), null);
        }
        if (StringUtils.isNotBlank(skill.getNamespaceId())
            && !namespaceId.equals(skill.getNamespaceId())) {
            throw new NacosException(NacosException.INVALID_PARAM,
                "Skill result does not belong to this SDK namespace.");
        }
        return proxy.downloadSkillZip(skill.getName(), null, null);
    }
    
    private Page<SkillBasicInfo> query(String namespace, String query, List<String> tagsAll,
        int pageNo, int pageSize) throws NacosException {
        Page<SkillBasicInfo> result =
            proxy.searchSkills(namespace, query, tagsAll, pageNo, pageSize);
        for (SkillBasicInfo item : result.getPageItems()) {
            item.setNamespaceId(namespace);
            item.setDiscoverySource(
                AiConstants.Hub.NAMESPACE_ID.equals(namespace) ? "HUB" : "NAMESPACE");
        }
        return result;
    }
}
