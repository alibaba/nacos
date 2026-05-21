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

import com.alibaba.nacos.common.utils.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Resource content encoder shared by Skill and AgentSpec zip parsers.
 *
 * <p>Decision policy: a file is treated as plain text only when its name (or extension)
 * matches the text whitelist; everything else is encoded as Base64 with metadata
 * {@code encoding=base64}. The detection contract used at download time
 * ({@code metadata.encoding == "base64"}) is unchanged, so legacy resources written
 * without metadata continue to be decoded as UTF-8 text.
 *
 * @author nacos
 */
public final class ResourceContentEncoder {
    
    /** Metadata key indicating the resource content encoding. */
    public static final String METADATA_ENCODING = "encoding";
    
    /** Metadata value meaning the content is Base64-encoded binary data. */
    public static final String METADATA_ENCODING_BASE64 = "base64";
    
    /** File extensions whose content is safe to store as UTF-8 text. */
    private static final Set<String> TEXT_EXTENSIONS;
    
    /** Lower-cased file names (no extension or with leading dot) that are always text. */
    private static final Set<String> TEXT_FILE_NAMES;
    
    static {
        Set<String> exts = new HashSet<>();
        Collections.addAll(exts,
            // Markup / docs
            "md", "markdown", "mdx", "txt", "rst", "adoc", "asciidoc",
            // Structured data / config
            "json", "json5", "yaml", "yml", "xml", "html", "htm", "css", "scss", "sass", "less",
            "properties", "conf", "cfg", "ini", "toml", "env", "tpl", "tmpl", "j2", "mustache",
            "hbs",
            // Common script / source code
            "js", "mjs", "cjs", "ts", "tsx", "jsx", "vue", "svelte",
            "py", "java", "kt", "kts", "scala", "groovy", "go", "rs", "rb", "php",
            "swift", "m", "mm", "c", "h", "cpp", "cc", "cxx", "hpp", "hh", "hxx",
            "cs", "fs", "fsx", "vb", "lua", "r", "pl", "pm", "ex", "exs", "erl",
            "dart", "zig", "nim", "jl", "clj", "cljs", "edn", "elm",
            // Shell / build
            "sh", "bash", "zsh", "fish", "ps1", "psm1", "bat", "cmd",
            "gradle", "sbt", "make", "mk",
            // Data / log
            "sql", "graphql", "gql", "csv", "tsv", "log", "diff", "patch",
            // Misc text
            "proto", "thrift", "ipynb");
        TEXT_EXTENSIONS = Collections.unmodifiableSet(exts);
        
        Set<String> names = new HashSet<>();
        Collections.addAll(names,
            // Common no-extension text files (case-insensitive match)
            "dockerfile", "containerfile", "makefile", "rakefile", "gemfile", "gemfile.lock",
            "jenkinsfile", "vagrantfile", "procfile", "brewfile",
            "license", "license.txt", "notice", "readme", "changelog", "authors",
            "contributors", "maintainers", "codeowners", "version", "manifest",
            // Dotfiles
            ".gitignore", ".gitattributes", ".gitmodules", ".gitkeep",
            ".dockerignore", ".editorconfig", ".env", ".envrc",
            ".npmrc", ".nvmrc", ".yarnrc", ".prettierrc", ".eslintrc",
            ".babelrc", ".browserslistrc", ".stylelintrc");
        TEXT_FILE_NAMES = Collections.unmodifiableSet(names);
    }
    
    private ResourceContentEncoder() {
    }
    
    /**
     * Decide whether the given file should be persisted as UTF-8 text.
     * Text whitelist matches by exact filename (for files with no extension or leading dot)
     * and by lower-cased extension. Anything not matched is treated as binary.
     *
     * @param fileName file name including extension (path prefix is allowed)
     * @return {@code true} if the file is recognized as plain text
     */
    public static boolean isText(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return false;
        }
        String pureName = stripDirectory(fileName).trim();
        if (pureName.isEmpty()) {
            return false;
        }
        String lower = pureName.toLowerCase();
        if (TEXT_FILE_NAMES.contains(lower)) {
            return true;
        }
        int dot = lower.lastIndexOf('.');
        // No extension, or trailing dot: rely solely on the filename whitelist above.
        if (dot <= 0 || dot == lower.length() - 1) {
            return false;
        }
        String ext = lower.substring(dot + 1);
        return TEXT_EXTENSIONS.contains(ext);
    }
    
    /**
     * Convenience inverse of {@link #isText(String)}.
     *
     * @param fileName file name including extension
     * @return {@code true} when the file is not in the text whitelist
     */
    public static boolean isBinary(String fileName) {
        return !isText(fileName);
    }
    
    /**
     * Encode raw resource bytes for storage.
     * Text files are stored as UTF-8 strings with no encoding metadata; binary files are
     * stored as Base64 strings with {@code metadata.encoding=base64} so download paths
     * can restore the original bytes via {@code SkillUtils.resolveResourceBytes}.
     *
     * @param data     raw bytes; {@code null} is treated as empty content
     * @param fileName file name used to look up the text whitelist
     * @return immutable encoded content holder
     */
    public static EncodedContent encode(byte[] data, String fileName) {
        if (data == null || data.length == 0) {
            return new EncodedContent("", null);
        }
        if (isText(fileName)) {
            return new EncodedContent(new String(data, StandardCharsets.UTF_8), null);
        }
        Map<String, Object> metadata = new HashMap<>(2);
        metadata.put(METADATA_ENCODING, METADATA_ENCODING_BASE64);
        return new EncodedContent(Base64.getEncoder().encodeToString(data), metadata);
    }
    
    /**
     * Build a metadata map flagging Base64 encoding. Used by storage-side reconstruction
     * paths that already hold a content string and only need to attach the encoding hint.
     *
     * @return mutable metadata map containing only the Base64 encoding flag
     */
    public static Map<String, Object> base64Metadata() {
        Map<String, Object> metadata = new HashMap<>(2);
        metadata.put(METADATA_ENCODING, METADATA_ENCODING_BASE64);
        return metadata;
    }
    
    private static String stripDirectory(String name) {
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            return name.substring(slash + 1);
        }
        int back = name.lastIndexOf('\\');
        return back >= 0 ? name.substring(back + 1) : name;
    }
    
    /**
     * Immutable holder describing an encoded resource: the textual content and optional metadata.
     * Metadata is {@code null} when no special encoding hint is needed (plain text).
     */
    public static final class EncodedContent {
        
        private final String content;
        
        private final Map<String, Object> metadata;
        
        EncodedContent(String content, Map<String, Object> metadata) {
            this.content = content;
            this.metadata = metadata;
        }
        
        public String getContent() {
            return content;
        }
        
        public Map<String, Object> getMetadata() {
            return metadata;
        }
    }
}
