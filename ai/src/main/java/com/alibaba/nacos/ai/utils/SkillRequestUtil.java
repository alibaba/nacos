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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Skill request util.
 *
 * @author nacos
 */
public class SkillRequestUtil {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SkillRequestUtil.class);
    
    private static final Pattern FRONTMATTER_PATTERN =
        Pattern.compile("^---\\r?\\n([\\s\\S]*?)\\r?\\n---");
    
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
            headers.setContentType(MediaType.parseMediaType("application/zip"));
            headers.add("Content-Disposition", "attachment;filename=" + skill.getName() + ".zip");
            return new ResponseEntity<>(zipBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            throw new NacosException(NacosException.SERVER_ERROR,
                "Failed to create skill zip: " + e.getMessage(), e);
        }
    }
    
    /**
     * Build a ZIP download {@link ResponseEntity} together with the listener-related headers
     * ({@code ETag}, {@code X-Nacos-Skill-Md5} and {@code X-Nacos-Skill-Resolved-Version}).
     *
     * <p>{@code md5} and {@code resolvedVersion} may be blank; only non-blank values are emitted as
     * headers so legacy paths that do not yet carry MD5 keep their existing response shape.
     *
     * @param skill           the Skill object
     * @param md5             published content MD5, optional
     * @param resolvedVersion resolved version when caller queries by label, optional
     * @return ResponseEntity containing ZIP bytes with proper headers
     * @throws NacosException if ZIP creation fails
     */
    public static ResponseEntity<byte[]> buildSkillZipResponseWithMd5(Skill skill, String md5,
        String resolvedVersion) throws NacosException {
        try {
            byte[] zipBytes = SkillUtils.toZipBytes(skill);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/zip"));
            headers.add("Content-Disposition", "attachment;filename=" + skill.getName() + ".zip");
            applyListenerHeaders(headers, md5, resolvedVersion);
            return new ResponseEntity<>(zipBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            throw new NacosException(NacosException.SERVER_ERROR,
                "Failed to create skill zip: " + e.getMessage(), e);
        }
    }
    
    /**
     * Build an HTTP 304 Not Modified response carrying the listener-related headers so the client
     * can refresh its local cache metadata even when the body is empty.
     *
     * @param md5             published content MD5
     * @param resolvedVersion resolved version, may be {@code null}
     * @return ResponseEntity with status 304 and listener headers
     */
    public static ResponseEntity<byte[]> buildSkillNotModifiedResponse(String md5,
        String resolvedVersion) {
        HttpHeaders headers = new HttpHeaders();
        applyListenerHeaders(headers, md5, resolvedVersion);
        return new ResponseEntity<>(headers, HttpStatus.NOT_MODIFIED);
    }
    
    private static void applyListenerHeaders(HttpHeaders headers, String md5,
        String resolvedVersion) {
        if (StringUtils.isNotBlank(md5)) {
            headers.add(HttpHeaders.ETAG, md5);
            headers.add(Constants.Skills.HEADER_SKILL_MD5, md5);
        }
        if (StringUtils.isNotBlank(resolvedVersion)) {
            headers.add(Constants.Skills.HEADER_SKILL_RESOLVED_VERSION, resolvedVersion);
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
            Skill result =
                JacksonUtils.toObj(skillDetailForm.getSkillCard(), new TypeReference<>() {
                });
            validateSkill(result);
            return result;
        } catch (NacosDeserializationException e) {
            LOGGER
                .error(String.format("Deserialize %s from %s failed, ", Skill.class.getSimpleName(),
                    skillDetailForm.getSkillCard()), e);
            throw new NacosApiException(NacosApiException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
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
        validateSkillMarkdownBody("skillCard.skillMd", skill.getSkillMd());
    }
    
    /**
     * Normalize the YAML frontmatter inside {@code skill.getSkillMd()} so that name, description, and version
     * are consistent with the authoritative values. This method modifies the skill in-place.
     *
     * <p>Rules (per KomachiSion's proposal on #14949):
     * <ul>
     *   <li><b>name</b>: always overwritten with {@code authoritativeName} (nacos-side truth)</li>
     *   <li><b>description</b>: if present in frontmatter and {@code isFirstCreate}, use frontmatter value and sync
     *       to {@code skill.setDescription()}; otherwise use {@code skill.getDescription()} and write back</li>
     *   <li><b>version</b>: always overwritten with {@code resolvedVersion} (the version determined by service layer)</li>
     * </ul>
     *
     * @param skill              the skill object (modified in-place)
     * @param authoritativeName  the canonical skill name from nacos
     * @param resolvedVersion    the version string determined by the service layer
     * @param isFirstCreate      true when this is the first import/creation (no existing meta)
     */
    public static void normalizeSkillFrontmatter(Skill skill, String authoritativeName,
        String resolvedVersion,
        boolean isFirstCreate) {
        String md = skill.getSkillMd();
        if (StringUtils.isEmpty(md)) {
            return;
        }
        
        // 1. name: on first create, frontmatter wins if present; on edit, authoritative name wins
        String fmName = parseFrontmatterField(md, "name");
        if (isFirstCreate && StringUtils.isNotBlank(fmName)) {
            // First create: use frontmatter name as the source of truth
            skill.setName(fmName);
            authoritativeName = fmName;
        }
        md = updateFrontmatterField(md, "name", authoritativeName);
        skill.setName(authoritativeName);
        
        // 2. description: frontmatter wins if present (both create and edit); otherwise use skill object value
        String fmDescription = parseFrontmatterField(md, "description");
        if (StringUtils.isNotBlank(fmDescription)) {
            skill.setDescription(fmDescription);
        } else if (StringUtils.isNotBlank(skill.getDescription())) {
            md = updateFrontmatterField(md, "description", skill.getDescription());
        }
        
        // 3. version: always sync to resolved version
        if (StringUtils.isNotBlank(resolvedVersion)) {
            md = updateFrontmatterField(md, "version", resolvedVersion);
        }
        
        skill.setSkillMd(md);
    }
    
    /**
     * Update (or insert) a field in the YAML frontmatter of a markdown string. If no frontmatter block exists,
     * one is created at the beginning.
     *
     * @param md    markdown content
     * @param field field name
     * @param value field value
     * @return updated markdown content
     */
    static String updateFrontmatterField(String md, String field, String value) {
        if (StringUtils.isEmpty(md)) {
            return "---\n" + field + ": " + value + "\n---\n";
        }
        java.util.regex.Matcher matcher = FRONTMATTER_PATTERN.matcher(md);
        if (!matcher.find()) {
            return "---\n" + field + ": " + value + "\n---\n\n" + md;
        }
        String frontmatterBlock = matcher.group(1);
        String[] lines = frontmatterBlock.split("\\r?\\n");
        boolean found = false;
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            int colonIdx = line.indexOf(':');
            if (colonIdx > 0 && line.substring(0, colonIdx).trim().equals(field)) {
                int indentLen = 0;
                while (indentLen < line.length() && Character.isWhitespace(
                    line.charAt(indentLen))) {
                    indentLen++;
                }
                sb.append(line, 0, indentLen).append(field).append(": ").append(value);
                found = true;
            } else {
                sb.append(line);
            }
            sb.append('\n');
        }
        if (!found) {
            // Insert at the beginning of frontmatter so name always appears first
            sb.insert(0, field + ": " + value + "\n");
        }
        // Remove trailing newline before closing ---
        String updatedBlock = sb.toString();
        if (updatedBlock.endsWith("\n")) {
            updatedBlock = updatedBlock.substring(0, updatedBlock.length() - 1);
        }
        return md.substring(0, matcher.start()) + "---\n" + updatedBlock + "\n---"
            + md.substring(matcher.end());
    }
    
    /**
     * Parse a single field value from YAML frontmatter in a markdown string.
     *
     * @param md    markdown content
     * @param field field name to extract
     * @return field value, or {@code null} if frontmatter or field is absent
     */
    static String parseFrontmatterField(String md, String field) {
        if (StringUtils.isEmpty(md)) {
            return null;
        }
        java.util.regex.Matcher matcher = FRONTMATTER_PATTERN.matcher(md);
        if (!matcher.find()) {
            return null;
        }
        String frontmatterBlock = matcher.group(1);
        for (String line : frontmatterBlock.split("\\r?\\n")) {
            int colonIdx = line.indexOf(':');
            if (colonIdx > 0) {
                String key = line.substring(0, colonIdx).trim();
                if (field.equals(key)) {
                    String value = line.substring(colonIdx + 1).trim();
                    // Strip surrounding quotes
                    if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    return value;
                }
            }
        }
        return null;
    }
    
    private static void validateSkillField(String fieldName, String fieldValue)
        throws NacosApiException {
        if (StringUtils.isEmpty(fieldValue)) {
            throw new NacosApiException(NacosApiException.INVALID_PARAM,
                ErrorCode.PARAMETER_MISSING,
                "Required parameter `skillCard." + fieldName + "` not present");
        }
    }
    
    /**
     * Validate markdown content is present and has non-empty body after removing frontmatter.
     *
     * @param fieldPath field path used in error message
     * @param markdown markdown content to validate
     * @throws NacosApiException if markdown is missing or body is empty
     */
    public static void validateSkillMarkdownBody(String fieldPath, String markdown)
        throws NacosApiException {
        if (StringUtils.isEmpty(markdown)) {
            throw new NacosApiException(NacosApiException.INVALID_PARAM,
                ErrorCode.PARAMETER_MISSING,
                "Required parameter `" + fieldPath + "` not present");
        }
        if (!hasNonFrontmatterContent(markdown)) {
            throw new NacosApiException(NacosApiException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Required parameter `" + fieldPath + "` markdown body should not be empty");
        }
    }
    
    /**
     * Check if markdown has non-empty body after removing YAML frontmatter.
     *
     * @param markdown markdown content
     * @return true if body has non-blank content
     */
    public static boolean hasNonFrontmatterContent(String markdown) {
        if (StringUtils.isEmpty(markdown)) {
            return false;
        }
        java.util.regex.Matcher matcher = FRONTMATTER_PATTERN.matcher(markdown);
        String body = matcher.find() ? markdown.substring(matcher.end()) : markdown;
        return StringUtils.isNotBlank(body);
    }
    
    /**
     * Validates parsed draft-create skill against request namespace and resolved skill name, then sets
     * {@link Skill#setNamespaceId(String)}. Call after {@link #parseSkill(SkillDetailForm)} when handling POST draft.
     *
     * @param skill         non-null skill from skillCard
     * @param namespaceId   request namespace
     * @param expectedName  resolved name (query {@code skillName} or name inside skillCard)
     */
    public static void validateInitialDraftSkill(Skill skill, String namespaceId,
        String expectedName)
        throws NacosApiException {
        if (skill == null || StringUtils.isBlank(skill.getName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "Skill name is required in skillCard when creating draft with content");
        }
        if (!expectedName.equals(skill.getName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "skillCard name must match skillName parameter");
        }
        if (StringUtils.isNotBlank(skill.getNamespaceId())
            && !namespaceId.equals(skill.getNamespaceId())) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
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
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.DATA_EMPTY,
                "File is required");
        }
        long maxUploadBytes = SkillZipParser.resolveMaxUploadBytes();
        if (file.getSize() > maxUploadBytes) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Skill zip size must not exceed "
                    + (maxUploadBytes / 1024 / 1024)
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
