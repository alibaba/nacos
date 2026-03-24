// Copyright 1999-2026 Alibaba Group Holding Ltd.
// SPDX-License-Identifier: Apache-2.0
//
// Run from repo root (Java 11+):
//   java scripts/agentspec/AgencyAgentSpecGenerator.java
//
// Downloads agency-agents-zh from GitHub API, applies OpenClaw split (same as
// agency-agents-zh/scripts/convert.sh convert_openclaw), writes data/agentspec/**.

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Offline-capable generator: prefers local target/agency-agents-zh-src (after git clone + optional convert.sh);
 * otherwise fetches main.zip from GitHub and extracts to that path, then emits data/agentspec.
 */
public class AgencyAgentSpecGenerator {

    private static final String REPO_ZIP =
            "https://github.com/jnMetaCode/agency-agents-zh/archive/refs/heads/main.zip";

    private static final String[] AGENT_DIRS = {
            "academic", "design", "engineering", "finance", "game-development", "hr", "legal", "marketing",
            "paid-media", "sales", "product", "project-management", "supply-chain", "testing", "support",
            "spatial-computing", "specialized"
    };

    private static final String MEMORY_MD = "# 长期记忆\n\n由运行时写入；本模板未预置业务记忆。\n";

    private static final String NOTICE = "Third-party content: agent personas derived from agency-agents-zh (MIT).\n"
            + "Repository: https://github.com/jnMetaCode/agency-agents-zh\n"
            + "Upstream license: see LICENSE in that repository.\n";

    public static void main(String[] args) throws Exception {
        Path repoRoot = findRepoRoot();
        Path src = repoRoot.resolve("target/agency-agents-zh-src");
        if (!Files.isDirectory(src)) {
            Files.createDirectories(src.getParent());
            System.err.println("Fetching " + REPO_ZIP);
            byte[] zipBytes = httpGetBytes(REPO_ZIP);
            unzipTo(zipBytes, src.getParent());
            Path extracted = src.getParent().resolve("agency-agents-zh-main");
            if (!Files.isDirectory(extracted)) {
                throw new IllegalStateException("Expected " + extracted + " after unzip");
            }
            if (Files.isDirectory(src)) {
                deleteRecursive(src);
            }
            Files.move(extracted, src);
        }

        String commit = gitHead(src);
        Path outRoot = repoRoot.resolve("data/agentspec");
        if (Files.isDirectory(outRoot)) {
            deleteRecursive(outRoot);
        }
        Files.createDirectories(outRoot);

        int count = 0;
        Set<String> usedNames = new LinkedHashSet<>();
        for (String dir : AGENT_DIRS) {
            Path dirpath = src.resolve(dir);
            if (!Files.isDirectory(dirpath)) {
                continue;
            }
            List<Path> mds = new ArrayList<>();
            try (var stream = Files.walk(dirpath)) {
                stream.filter(p -> p.toString().endsWith(".md")).sorted().forEach(mds::add);
            }
            for (Path md : mds) {
                String text = Files.readString(md, StandardCharsets.UTF_8);
                if (!text.startsWith("---\n")) {
                    continue;
                }
                String name = getField("name", text);
                if (name.isEmpty()) {
                    continue;
                }
                String description = getField("description", text);
                String body = getBody(text);
                Path rel = src.relativize(md);
                String slug = stripExtension(rel.getFileName().toString());
                List<String> bizTags = getBizTags(rel);
                String suggestedName = resolveSuggestedName(name, bizTags, slug, usedNames);

                Path dest = outRoot.resolve(rel.getParent()).resolve(slug);
                Path cfg = dest.resolve("config");
                Path crons = dest.resolve("crons");
                Files.createDirectories(cfg);
                Files.createDirectories(crons);

                String soul;
                String agents;
                String identity;
                Path ocSoul = src.resolve("integrations/openclaw").resolve(slug).resolve("SOUL.md");
                if (Files.isRegularFile(ocSoul)) {
                    soul = Files.readString(src.resolve("integrations/openclaw").resolve(slug).resolve("SOUL.md"),
                            StandardCharsets.UTF_8);
                    agents = Files.readString(src.resolve("integrations/openclaw").resolve(slug).resolve("AGENTS.md"),
                            StandardCharsets.UTF_8);
                    identity = Files.readString(
                            src.resolve("integrations/openclaw").resolve(slug).resolve("IDENTITY.md"),
                            StandardCharsets.UTF_8);
                } else {
                    soul = convertOpenclawSoul(body);
                    agents = convertOpenclawAgents(body);
                    identity = "# " + name + "\n" + description + "\n";
                }

                Files.writeString(cfg.resolve("SOUL.md"), soul, StandardCharsets.UTF_8);
                Files.writeString(cfg.resolve("AGENTS.md"), agents, StandardCharsets.UTF_8);
                Files.writeString(cfg.resolve("IDENTITY.md"), identity, StandardCharsets.UTF_8);
                Files.writeString(cfg.resolve("MEMORY.md"), MEMORY_MD, StandardCharsets.UTF_8);

                String manifest = manifestJson(commit, rel.toString().replace('\\', '/'), suggestedName, description,
                        bizTags);
                Files.writeString(dest.resolve("manifest.json"), manifest, StandardCharsets.UTF_8);

                String toolAnalysis = "{\n  \"version\": \"1.0\",\n  \"notes\": \"Static export from agency-agents-zh OpenClaw conversion; not a runtime tool scan.\",\n"
                        + "  \"packages\": { \"apt\": [], \"pip\": [], \"npm\": [] }\n}\n";
                Files.writeString(dest.resolve("tool-analysis.json"), toolAnalysis, StandardCharsets.UTF_8);
                Files.writeString(crons.resolve("jobs.json"), "[]\n", StandardCharsets.UTF_8);
                count++;
            }
        }

        Files.writeString(outRoot.resolve("NOTICE"), NOTICE, StandardCharsets.UTF_8);
        System.out.println("Wrote " + count + " AgentSpec trees under " + outRoot);
    }

    private static String manifestJson(String commit, String originalPath, String suggestedName, String description,
            List<String> bizTags) {
        return "{\n  \"version\": \"1.0\",\n  \"source\": {\n    \"repository\": \"https://github.com/jnMetaCode/agency-agents-zh\",\n"
                + "    \"commit\": \"" + escapeJson(commit) + "\",\n    \"original_path\": \"" + escapeJson(originalPath)
                + "\",\n    \"openclaw_mode\": true\n  },\n  \"description\": \"" + escapeJson(description)
                + "\",\n  \"tags\": " + toJsonArray(bizTags)
                + ",\n  \"worker\": {\n    \"suggested_name\": \"" + escapeJson(suggestedName)
                + "\",\n    \"base_image\": \"hiclaw/worker-agent:latest\",\n    \"apt_packages\": [],\n"
                + "    \"pip_packages\": [],\n    \"npm_packages\": []\n  },\n  \"proxy\": {\n    \"suggested\": false,\n"
                + "    \"reason\": \"\"\n  }\n}\n";
    }

    private static String toJsonArray(List<String> bizTags) {
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < bizTags.size(); i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append('"').append(escapeJson(bizTags.get(i))).append('"');
        }
        result.append(']');
        return result.toString();
    }

    private static List<String> getBizTags(Path rel) {
        List<String> result = new ArrayList<>();
        Path parent = rel.getParent();
        if (parent == null) {
            return result;
        }
        for (Path each : parent) {
            result.add(each.toString());
        }
        return result;
    }

    private static String resolveSuggestedName(String originalName, List<String> bizTags, String slug,
            Set<String> usedNames) {
        if (usedNames.add(originalName)) {
            return originalName;
        }
        String taggedName = originalName + "（" + String.join("/", bizTags) + "）";
        if (usedNames.add(taggedName)) {
            return taggedName;
        }
        String slugName = originalName + "（" + slug + "）";
        usedNames.add(slugName);
        return slugName;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static Path findRepoRoot() {
        Path p = Path.of("").toAbsolutePath();
        for (int i = 0; i < 8; i++) {
            if (Files.isDirectory(p.resolve("data")) && Files.isDirectory(p.resolve("ai"))) {
                return p;
            }
            p = p.getParent();
            if (p == null) {
                break;
            }
        }
        return Path.of("").toAbsolutePath();
    }

    private static String gitHead(Path src) {
        try {
            Process p = new ProcessBuilder("git", "-C", src.toString(), "rev-parse", "HEAD").start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (p.waitFor() == 0 && !out.isEmpty()) {
                return out;
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    private static void deleteRecursive(Path root) throws IOException {
        if (Files.isDirectory(root)) {
            try (var stream = Files.walk(root)) {
                stream.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        } else {
            Files.deleteIfExists(root);
        }
    }

    private static void unzipTo(byte[] zipBytes, Path destParent) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName();
                Path out = destParent.resolve(name);
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static byte[] httpGetBytes(String uri) throws IOException, InterruptedException {
        HttpClient c = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(uri)).GET().build();
        HttpResponse<byte[]> resp = c.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + resp.statusCode() + " for " + uri);
        }
        return resp.body();
    }

    private static String getField(String field, String text) {
        if (!text.startsWith("---\n")) {
            return "";
        }
        int end = text.indexOf("\n---\n", 3);
        if (end < 0) {
            return "";
        }
        String fm = text.substring(4, end);
        String prefix = field + ": ";
        for (String line : fm.split("\n")) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    private static String getBody(String text) {
        if (!text.startsWith("---\n")) {
            return text;
        }
        int end = text.indexOf("\n---\n", 3);
        if (end < 0) {
            return text;
        }
        return text.substring(end + 5);
    }

    private static boolean sectionHeaderIsSoul(String headerLine) {
        String h = headerLine.toLowerCase(Locale.ROOT);
        if (headerLine.contains("身份") || headerLine.contains("记忆") || headerLine.contains("沟通") || headerLine.contains("风格")
                || headerLine.contains("关键规则")) {
            return true;
        }
        if (h.contains("identity") || h.contains("communication") || h.contains("style")) {
            return true;
        }
        if (Pattern.compile("critical.rule").matcher(h).find()) {
            return true;
        }
        if (Pattern.compile("rules.you.must.follow").matcher(h).find()) {
            return true;
        }
        return false;
    }

    private static String convertOpenclawSoul(String body) {
        return splitOpenclaw(body)[0];
    }

    private static String convertOpenclawAgents(String body) {
        return splitOpenclaw(body)[1];
    }

    private static String[] splitOpenclaw(String body) {
        StringBuilder soul = new StringBuilder();
        StringBuilder agents = new StringBuilder();
        String currentTarget = "agents";
        StringBuilder currentSection = new StringBuilder();

        String[] lines = body.split("(?<=\n)", -1);

        for (String line : lines) {
            if (isH2(line)) {
                if (currentSection.length() > 0) {
                    if ("soul".equals(currentTarget)) {
                        soul.append(currentSection);
                    } else {
                        agents.append(currentSection);
                    }
                }
                currentSection = new StringBuilder();
                currentTarget = sectionHeaderIsSoul(line) ? "soul" : "agents";
            }
            currentSection.append(line);
        }
        if (currentSection.length() > 0) {
            if ("soul".equals(currentTarget)) {
                soul.append(currentSection);
            } else {
                agents.append(currentSection);
            }
        }
        return new String[] { soul.toString(), agents.toString() };
    }

    /** Bash: [[ "$line" =~ ^##[[:space:]] ]] */
    private static boolean isH2(String line) {
        if (!line.startsWith("##")) {
            return false;
        }
        return line.length() == 2 || Character.isWhitespace(line.charAt(2));
    }

    private static String stripExtension(String filename) {
        int i = filename.lastIndexOf('.');
        return i > 0 ? filename.substring(0, i) : filename;
    }
}
