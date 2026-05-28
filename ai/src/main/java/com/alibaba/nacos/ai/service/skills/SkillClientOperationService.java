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

package com.alibaba.nacos.ai.service.skills;

import com.alibaba.nacos.api.exception.NacosException;

/**
 * Client runtime entry-point for skill listener queries.
 *
 * <p>The implementation is responsible for resolving the requested version, comparing the
 * client-supplied content MD5 against the published one, lazily back-filling the MD5 for
 * historical versions, and signalling "client cache is fresh" via
 * {@link SkillQueryResult#isNotModified()} when the two MD5s match.
 *
 * @author nacos
 * @since 3.2.0
 */
public interface SkillClientOperationService {
    
    /**
     * Query a skill from the client runtime path with optional MD5-based not-modified semantics.
     *
     * <p>When {@code clientMd5} is non-blank and equals the published content MD5 of the resolved
     * version, this method returns a result whose {@link SkillQueryResult#isNotModified()} is
     * {@code true} and {@link SkillQueryResult#getSkill()} is {@code null}, so the controller can
     * translate it into HTTP 304 without loading content. Otherwise the resolved skill is returned
     * together with its content MD5 and resolved version.
     *
     * <p>If the published content MD5 is missing on the version row (e.g. legacy data published
     * before the listener feature shipped), the implementation MUST back-fill it synchronously and
     * still respond with the loaded skill — never with not-modified — when back-fill fails.
     *
     * @param namespaceId namespace
     * @param name        skill name
     * @param version     explicit version, may be {@code null} when {@code label} is provided
     * @param label       label, may be {@code null} when {@code version} is provided
     * @param clientMd5   MD5 carried by the listener; may be {@code null} or blank for the first poll
     * @return resolved skill plus its content MD5 and resolved version, or a not-modified marker
     * @throws NacosException if resolution or load fails
     */
    SkillQueryResult querySkill(String namespaceId, String name, String version, String label,
        String clientMd5) throws NacosException;
}
