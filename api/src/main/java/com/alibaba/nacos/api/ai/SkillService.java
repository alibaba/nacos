/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.ai;

import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.ai.listener.AbstractNacosSkillListener;
import com.alibaba.nacos.api.ai.model.skills.SkillBasicInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;

import java.util.List;

/**
 * SkillService operations obtained from {@link AiService#skill()}.
 *
 * @author Nacos
 * @since 3.3.0
 */
public interface SkillService {
    
    /**
     * Search online skills in the bound namespace and, when configured, shared skills.
     *
     * @param query search text, optional
     * @param tagsAll tags which must all match, optional
     * @param pageNo one-based page number
     * @param pageSize page size, from 1 to 100
     * @return namespace-first result page, retaining each resource's origin
     * @throws NacosException if arguments are invalid or either requested source fails
     */
    @Since("3.3.0")
    default Page<SkillBasicInfo> searchSkills(String query, List<String> tagsAll, int pageNo,
        int pageSize) throws NacosException {
        throw new NacosException(NacosException.SERVER_NOT_IMPLEMENTED,
            "Skill search is not implemented by this SDK.");
    }
    
    /**
     * Download a selected search result, retaining its namespace and pinned shared version.
     *
     * @param skill skill returned by search
     * @return ZIP file bytes
     * @throws NacosException if the result is invalid or the download fails
     */
    @Since("3.3.0")
    default byte[] downloadSkillZipBySearchResult(SkillBasicInfo skill) throws NacosException {
        if (skill == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "Skill must not be null.");
        }
        if ("HUB".equals(skill.getDiscoverySource())) {
            throw new NacosException(NacosException.SERVER_NOT_IMPLEMENTED,
                "Shared Skill download is not implemented by this SDK.");
        }
        return downloadSkillZip(skill.getName());
    }
    
    /**
     * Download skill as ZIP byte array by skill name. Defaults to latest version.
     *
     * <p>The ZIP contains the skill directory structure: SKILL.md and all resource files.
     * Binary resources are decoded from Base64 back to raw bytes.</p>
     *
     * @param skillName skill name (unique identifier)
     * @return ZIP file as byte array
     * @throws NacosException if skill not found or query error
     */
    @Since("3.2.0")
    byte[] downloadSkillZip(String skillName) throws NacosException;
    
    /**
     * Download skill as ZIP byte array by skill name and target version.
     *
     * @param skillName skill name (unique identifier)
     * @param version   target skill version, if null, will get latest version
     * @return ZIP file as byte array
     * @throws NacosException if skill not found or query error
     */
    @Since("3.2.0")
    byte[] downloadSkillZipByVersion(String skillName, String version) throws NacosException;
    
    /**
     * Download skill as ZIP byte array by skill name and target label.
     *
     * @param skillName skill name (unique identifier)
     * @param label     target skill label (e.g. "latest", "stable")
     * @return ZIP file as byte array
     * @throws NacosException if skill not found or query error
     */
    @Since("3.2.0")
    byte[] downloadSkillZipByLabel(String skillName, String label) throws NacosException;
    
    /**
     * Subscribe skill changes.
     *
     * @param skillName     skill name
     * @param version       target skill version, optional
     * @param label         target skill label, optional
     * @param skillListener listener for skill changes
     * @return current skill ZIP bytes, may be {@code null} when the skill is not found
     * @throws NacosException if request parameter is invalid or handle error
     */
    @Since("3.2.2")
    byte[] subscribeSkill(String skillName, String version, String label,
        AbstractNacosSkillListener skillListener) throws NacosException;
    
    /**
     * Un-subscribe skill changes.
     *
     * @param skillName     skill name
     * @param version       target skill version, optional
     * @param label         target skill label, optional
     * @param skillListener listener previously registered via
     *                      {@link #subscribeSkill(String, String, String, AbstractNacosSkillListener)}
     * @throws NacosException if request parameter is invalid or handle error
     */
    @Since("3.2.2")
    void unsubscribeSkill(String skillName, String version, String label,
        AbstractNacosSkillListener skillListener) throws NacosException;
}
