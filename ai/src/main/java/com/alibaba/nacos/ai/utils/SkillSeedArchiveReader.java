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

package com.alibaba.nacos.ai.utils;

import com.alibaba.nacos.api.ai.model.skills.SkillUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Read a bundled skills archive and convert each skill directory into a standalone skill zip.
 *
 * @author nacos
 */
public final class SkillSeedArchiveReader {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SkillSeedArchiveReader.class);
    
    private static final String SKILL_MD_FILE = "SKILL.md";
    
    private static final Pattern FRONT_MATTER_PATTERN =
        Pattern.compile("^---\\s*\\n(.*?)\\n---\\s*\\n", Pattern.DOTALL);
    
    private static final Pattern NAME_PATTERN = Pattern.compile(
        "(?m)^name:\\s*(?:\"([^\"]+)\"|'([^']+)'|([^\\n#]+))\\s*$");
    
    private SkillSeedArchiveReader() {
    }
    
    /**
     * Read bundled seed archive and return standalone skill packages.
     *
     * @param inputStream archive input stream
     * @return skill packages
     * @throws IOException if reading failed
     */
    public static List<SkillPackage> read(InputStream inputStream) throws IOException {
        Map<String, byte[]> entries = readArchiveEntries(inputStream);
        if (entries.isEmpty()) {
            return Collections.emptyList();
        }
        
        Set<String> roots = detectSkillRoots(entries.keySet());
        if (roots.isEmpty()) {
            return Collections.emptyList();
        }
        
        Set<String> seenSkillNames = new HashSet<>();
        List<SkillPackage> result = new ArrayList<>(roots.size());
        for (String root : roots) {
            String skillMdPath = rootPath(root, SKILL_MD_FILE);
            byte[] skillMdBytes = entries.get(skillMdPath);
            if (skillMdBytes == null) {
                continue;
            }
            String skillName = extractSkillName(skillMdBytes);
            if (StringUtils.isBlank(skillName)) {
                throw new IOException("Missing skill name in " + skillMdPath);
            }
            if (!seenSkillNames.add(skillName)) {
                LOGGER.warn("Skip duplicate built-in skill name `{}` from archive path `{}`",
                    skillName, root);
                continue;
            }
            result.add(new SkillPackage(skillName, extractFrom(root), root,
                buildSkillZip(entries, root, skillName)));
        }
        return result;
    }
    
    private static Map<String, byte[]> readArchiveEntries(InputStream inputStream)
        throws IOException {
        Map<String, byte[]> result = new LinkedHashMap<>();
        try (ZipArchiveInputStream zis =
            new ZipArchiveInputStream(inputStream, StandardCharsets.UTF_8.name(), true, true)) {
            ZipArchiveEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = normalizeEntryName(entry.getName());
                if (StringUtils.isBlank(entryName)) {
                    continue;
                }
                SkillUtils.validatePathSafety(entryName);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int bytesRead;
                while ((bytesRead = zis.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                result.put(entryName, out.toByteArray());
            }
        }
        return result;
    }
    
    private static Set<String> detectSkillRoots(Set<String> entryNames) {
        Set<String> result = new TreeSet<>();
        for (String entryName : entryNames) {
            if (SKILL_MD_FILE.equals(entryName)) {
                result.add("");
                continue;
            }
            if (entryName.endsWith("/" + SKILL_MD_FILE)) {
                result.add(entryName.substring(0, entryName.length() - SKILL_MD_FILE.length() - 1));
            }
        }
        return result;
    }
    
    private static String extractSkillName(byte[] skillMdBytes) {
        String content = new String(skillMdBytes, StandardCharsets.UTF_8);
        Matcher frontMatterMatcher = FRONT_MATTER_PATTERN.matcher(content);
        if (!frontMatterMatcher.find()) {
            return null;
        }
        Matcher nameMatcher = NAME_PATTERN.matcher(frontMatterMatcher.group(1));
        if (!nameMatcher.find()) {
            return null;
        }
        for (int i = 1; i <= nameMatcher.groupCount(); i++) {
            String candidate = nameMatcher.group(i);
            if (StringUtils.isNotBlank(candidate)) {
                return candidate.trim();
            }
        }
        return null;
    }
    
    private static byte[] buildSkillZip(Map<String, byte[]> entries, String root, String skillName)
        throws IOException {
        List<String> paths = new ArrayList<>();
        for (String entryName : entries.keySet()) {
            if (isInRoot(entryName, root)) {
                paths.add(entryName);
            }
        }
        Collections.sort(paths);
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (String path : paths) {
                String relativePath = root.isEmpty() ? path : path.substring(root.length() + 1);
                if (StringUtils.isBlank(relativePath)) {
                    continue;
                }
                ZipEntry zipEntry = new ZipEntry(skillName + "/" + relativePath);
                zos.putNextEntry(zipEntry);
                zos.write(entries.get(path));
                zos.closeEntry();
            }
        }
        return out.toByteArray();
    }
    
    private static boolean isInRoot(String entryName, String root) {
        return root.isEmpty() || entryName.startsWith(root + "/");
    }
    
    private static String normalizeEntryName(String entryName) {
        if (entryName == null) {
            return null;
        }
        String result = entryName.replace('\\', '/');
        while (result.startsWith("./")) {
            result = result.substring(2);
        }
        return result;
    }
    
    private static String rootPath(String root, String fileName) {
        return root.isEmpty() ? fileName : root + "/" + fileName;
    }
    
    private static String extractFrom(String sourcePath) {
        if (StringUtils.isBlank(sourcePath)) {
            return null;
        }
        String normalized = sourcePath;
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        int idx = normalized.lastIndexOf('/');
        if (idx <= 0) {
            return null;
        }
        String from = normalized.substring(0, idx).trim();
        return StringUtils.isBlank(from) ? null : from;
    }
    
    /**
     * Standalone skill package built from the seed archive.
     */
    public static final class SkillPackage {
        
        private final String skillName;
        
        private final String from;
        
        private final String sourcePath;
        
        private final byte[] zipBytes;
        
        public SkillPackage(String skillName, String from, String sourcePath, byte[] zipBytes) {
            this.skillName = skillName;
            this.from = from;
            this.sourcePath = sourcePath;
            this.zipBytes = zipBytes == null ? new byte[0] : zipBytes;
        }
        
        public String getSkillName() {
            return skillName;
        }
        
        public String getFrom() {
            return from;
        }
        
        public String getSourcePath() {
            return sourcePath;
        }
        
        public byte[] getZipBytes() {
            return zipBytes;
        }
    }
}
