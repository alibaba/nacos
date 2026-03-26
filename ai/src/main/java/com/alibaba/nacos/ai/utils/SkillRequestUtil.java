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

package com.alibaba.nacos.ai.utils;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.form.skills.admin.SkillDetailForm;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Skill request util.
 *
 * @author nacos
 */
public class SkillRequestUtil {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SkillRequestUtil.class);
    
    /**
     * Build a ZIP download {@link ResponseEntity} from a {@link Skill} object.
     *
     * <p>Shared by all controllers that need to export a skill as ZIP.</p>
     *
     * @param skill the Skill object
     * @return ResponseEntity containing ZIP bytes with proper headers
     * @throws NacosException if ZIP creation fails
     */
    public static ResponseEntity<byte[]> buildSkillZipResponse(Skill skill) throws NacosException {
        try {
            byte[] zipBytes = SkillUtils.toZipBytes(skill);
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Disposition", "attachment;filename=" + skill.getName() + ".zip");
            return new ResponseEntity<>(zipBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            throw new NacosException(NacosException.SERVER_ERROR,
                    "Failed to create skill zip: " + e.getMessage(), e);
        }
    }

    /**
     * Parse Skill request form to {@link Skill}.
     *
     * @param skillDetailForm skill detail form.
     * @return skill
     * @throws NacosApiException if parse failed or request parameter is conflicted.
     */
    public static Skill parseSkill(SkillDetailForm skillDetailForm) throws NacosApiException {
        try {
            Skill result = JacksonUtils.toObj(skillDetailForm.getSkillCard(), new TypeReference<>() {
            });
            validateSkill(result);
            return result;
        } catch (NacosDeserializationException e) {
            LOGGER.error(String.format("Deserialize %s from %s failed, ", Skill.class.getSimpleName(),
                    skillDetailForm.getSkillCard()), e);
            throw new NacosApiException(NacosApiException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "skillCard is invalid. Can't be parsed.");
        }
    }
    
    /**
     * Validate skill is legal.
     *
     * @param skill skill
     * @throws NacosApiException if skill is illegal.
     */
    public static void validateSkill(Skill skill) throws NacosApiException {
        validateSkillField("name", skill.getName());
        validateSkillField("description", skill.getDescription());
        validateSkillField("skillMd", skill.getSkillMd());
    }
    
    private static void validateSkillField(String fieldName, String fieldValue) throws NacosApiException {
        if (StringUtils.isEmpty(fieldValue)) {
            throw new NacosApiException(NacosApiException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `skillCard." + fieldName + "` not present");
        }
    }

    /**
     * Validates parsed draft-create skill against request namespace and resolved skill name, then sets
     * {@link Skill#setNamespaceId(String)}. Call after {@link #parseSkill(SkillDetailForm)} when handling POST draft.
     *
     * @param skill         non-null skill from skillCard
     * @param namespaceId   request namespace
     * @param expectedName  resolved name (query {@code skillName} or name inside skillCard)
     */
    public static void validateInitialDraftSkill(Skill skill, String namespaceId, String expectedName)
            throws NacosApiException {
        if (skill == null || StringUtils.isBlank(skill.getName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Skill name is required in skillCard when creating draft with content");
        }
        if (!expectedName.equals(skill.getName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "skillCard name must match skillName parameter");
        }
        if (StringUtils.isNotBlank(skill.getNamespaceId()) && !namespaceId.equals(skill.getNamespaceId())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "skillCard namespaceId must match request namespaceId");
        }
        skill.setNamespaceId(namespaceId);
    }

    /**
     * Validate uploaded skill zip file and extract bytes.
     *
     * <p>Validates the file is not null/empty, checks file size against the maximum limit,
     * and extracts the file bytes. This method is shared by both admin and console upload endpoints.</p>
     *
     * @param file the uploaded multipart file
     * @return the file bytes
     * @throws NacosException if validation fails or file reading fails
     */
    public static byte[] validateAndExtractZipBytes(MultipartFile file) throws NacosException {
        if (file == null || file.isEmpty()) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.DATA_EMPTY, "File is required");
        }
        if (file.getSize() > Constants.Skills.MAX_UPLOAD_ZIP_BYTES) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Skill zip size must not exceed " + (Constants.Skills.MAX_UPLOAD_ZIP_BYTES / 1024 / 1024)
                            + "MB, current: " + (file.getSize() / 1024 / 1024) + "MB");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.PARSING_DATA_FAILED,
                    "Failed to read file: " + e.getMessage());
        }
    }
}
