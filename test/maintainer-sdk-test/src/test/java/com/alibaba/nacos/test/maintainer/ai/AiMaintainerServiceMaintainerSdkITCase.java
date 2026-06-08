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

package com.alibaba.nacos.test.maintainer.ai;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.a2a.AgentCapabilities;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecMeta;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpTool;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.ai.model.skills.BatchUploadResult;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillMeta;
import com.alibaba.nacos.api.ai.model.skills.SkillUtils;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerFactory;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import com.alibaba.nacos.maintainer.client.ai.McpMaintainerService;
import com.alibaba.nacos.test.maintainer.MaintainerSdkBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link AiMaintainerService}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: the AI maintainer factory exposes MCP, A2A,
 *     Prompt, Skill, AgentSpec, and Pipeline delegate services against a
 *     standalone Nacos server.</li>
 *     <li>Expected capability: representative MCP, A2A, Prompt, Skill, and
 *     AgentSpec admin lifecycle workflows create, query, list, update,
 *     force-publish when applicable, and delete isolated resources.</li>
 *     <li>Boundary/validation: null factory properties and invalid MCP
 *     local/remote specifications fail with controlled SDK exceptions.</li>
 *     <li>Expected capability: Skill and AgentSpec ZIP uploads, including
 *     Skill batch upload, create editable drafts that can be queried through
 *     the maintainer SDK.</li>
 *     <li>Known standalone limitation: real pipeline approval workflows are
 *     documented as follow-up coverage because this IT uses force-publish
 *     instead of enabling review plugins.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
class AiMaintainerServiceMaintainerSdkITCase extends MaintainerSdkBaseITCase {
    
    private static final String NAMESPACE_ID = Constants.DEFAULT_NAMESPACE_ID;
    
    private static final String VERSION = "1.0.0";
    
    @Test
    void shouldCreateAiMaintainerServiceAndQueryDelegates() throws Exception {
        AiMaintainerService maintainerService = createAiMaintainerService();
        
        assertNotNull(maintainerService.mcp());
        assertNotNull(maintainerService.a2a());
        assertNotNull(maintainerService.prompt());
        assertNotNull(maintainerService.skill());
        assertNotNull(maintainerService.agentSpec());
        assertNotNull(maintainerService.pipeline());
        
        assertPage(maintainerService.mcp()
                .listMcpServer(NAMESPACE_ID, randomMaintainerName("missing-mcp"), 1, 10));
        assertPage(maintainerService.a2a()
                .listAgentCards(NAMESPACE_ID, randomMaintainerName("missing-agent"), 1, 10));
        assertPage(maintainerService.prompt().listPrompts(NAMESPACE_ID,
                randomMaintainerName("missing-prompt"), "blur", null, 1, 10));
        assertPage(maintainerService.skill().listSkills(NAMESPACE_ID,
                randomMaintainerName("missing-skill"), "blur", 1, 10));
        assertPage(maintainerService.agentSpec().listAgentSpecs(NAMESPACE_ID,
                randomMaintainerName("missing-agentspec"), "blur", 1, 10));
        assertSuccessResult(maintainerService.pipeline()
                .listPipelineExecutions("prompt", null, NAMESPACE_ID, null, 1, 10));
        NacosException missingPipeline = assertThrows(NacosException.class,
                () -> maintainerService.pipeline()
                        .getPipelineDetail(randomMaintainerName("missing-pipeline")));
        assertEquals(NacosException.NOT_FOUND, missingPipeline.getErrCode());
    }
    
    @Test
    void shouldManageMcpServerLifecycle() throws Exception {
        McpMaintainerService mcpMaintainerService = createAiMaintainerService().mcp();
        String mcpName = randomMaintainerName("mcp");
        McpToolSpecification toolSpec = buildMcpToolSpecification(mcpName);
        
        assertThrows(NacosException.class,
                () -> mcpMaintainerService.getMcpServerDetail(NAMESPACE_ID, mcpName, VERSION));
        
        String mcpId = mcpMaintainerService.createLocalMcpServer(mcpName, VERSION,
                "Maintainer SDK IT MCP server", toolSpec);
        assertNotNull(mcpId);
        addCleanup(() -> mcpMaintainerService.deleteMcpServer(NAMESPACE_ID, mcpName, mcpId,
                VERSION));
        
        McpServerDetailInfo detail =
                mcpMaintainerService.getMcpServerDetail(NAMESPACE_ID, mcpName, mcpId, VERSION);
        assertMcpServer(detail, mcpName, VERSION, "Maintainer SDK IT MCP server");
        assertNotNull(detail.getToolSpec());
        assertFalse(detail.getToolSpec().getTools().isEmpty());
        
        assertContainsPageItem(mcpMaintainerService.listMcpServer(NAMESPACE_ID, mcpName, 1, 10),
                each -> mcpName.equals(each.getName()));
        assertContainsPageItem(mcpMaintainerService.searchMcpServer(NAMESPACE_ID, mcpName, 1, 10),
                each -> mcpName.equals(each.getName()));
        
        McpServerBasicInfo updatedSpec =
                buildMcpServer(mcpName, VERSION, "Maintainer SDK IT MCP server updated");
        updatedSpec.setId(mcpId);
        assertTrue(mcpMaintainerService.updateMcpServer(NAMESPACE_ID, mcpName, true, updatedSpec,
                toolSpec, null, true));
        
        McpServerDetailInfo updated =
                mcpMaintainerService.getMcpServerDetail(NAMESPACE_ID, mcpName, mcpId, VERSION);
        assertMcpServer(updated, mcpName, VERSION, "Maintainer SDK IT MCP server updated");
        
        assertTrue(mcpMaintainerService.deleteMcpServer(NAMESPACE_ID, mcpName, mcpId, VERSION));
        assertNoPageItem(mcpMaintainerService.listMcpServer(NAMESPACE_ID, mcpName, 1, 10),
                each -> mcpName.equals(each.getName()));
    }
    
    @Test
    void shouldManageA2aAgentLifecycle() throws Exception {
        AiMaintainerService maintainerService = createAiMaintainerService();
        String agentName = randomMaintainerName("agent");
        AgentCard agentCard = buildAgentCard(agentName, VERSION, "Maintainer SDK IT agent");
        
        assertTrue(maintainerService.a2a().registerAgent(agentCard, NAMESPACE_ID,
                AiConstants.A2a.A2A_ENDPOINT_TYPE_URL));
        addCleanup(() -> maintainerService.a2a().deleteAgent(agentName, NAMESPACE_ID, ""));
        
        AgentCardDetailInfo detail = maintainerService.a2a()
                .getAgentCard(agentName, NAMESPACE_ID, AiConstants.A2a.A2A_ENDPOINT_TYPE_URL,
                        VERSION);
        assertAgentCard(detail, agentName, VERSION, "Maintainer SDK IT agent");
        
        List<AgentVersionDetail> versions =
                maintainerService.a2a().listAllVersionOfAgent(agentName, NAMESPACE_ID);
        assertTrue(versions.stream().anyMatch(each -> VERSION.equals(each.getVersion())));
        assertContainsPageItem(
                maintainerService.a2a().listAgentCards(NAMESPACE_ID, agentName, 1, 10),
                each -> agentName.equals(each.getName()));
        assertContainsPageItem(
                maintainerService.a2a().searchAgentCardsByName(NAMESPACE_ID, agentName, 1, 10),
                each -> agentName.equals(each.getName()));
        
        AgentCard updatedCard =
                buildAgentCard(agentName, VERSION, "Maintainer SDK IT agent updated");
        assertTrue(maintainerService.a2a().updateAgentCard(updatedCard, NAMESPACE_ID, true,
                AiConstants.A2a.A2A_ENDPOINT_TYPE_URL));
        
        AgentCardDetailInfo updated = maintainerService.a2a()
                .getAgentCard(agentName, NAMESPACE_ID, AiConstants.A2a.A2A_ENDPOINT_TYPE_URL,
                        VERSION);
        assertAgentCard(updated, agentName, VERSION, "Maintainer SDK IT agent updated");
        
        assertTrue(maintainerService.a2a().deleteAgent(agentName, NAMESPACE_ID, ""));
        assertNoPageItem(maintainerService.a2a().listAgentCards(NAMESPACE_ID, agentName, 1, 10),
                each -> agentName.equals(each.getName()));
    }
    
    @Test
    void shouldManagePromptLifecycle() throws Exception {
        AiMaintainerService maintainerService = createAiMaintainerService();
        String promptKey = randomMaintainerName("prompt");
        
        String draftVersion = maintainerService.prompt().createDraft(NAMESPACE_ID, promptKey, null,
                VERSION, "Maintainer SDK IT prompt template", null, "create prompt",
                "Maintainer SDK IT prompt", "[\"maintainer-sdk-it\"]");
        assertEquals(VERSION, draftVersion);
        addCleanup(() -> maintainerService.prompt().deletePrompt(NAMESPACE_ID, promptKey));
        
        maintainerService.prompt().updateDraft(NAMESPACE_ID, promptKey,
                "Maintainer SDK IT prompt template updated", null, "update prompt");
        PromptVersionInfo draftDetail =
                maintainerService.prompt().getVersionDetail(NAMESPACE_ID, promptKey, VERSION);
        assertEquals("Maintainer SDK IT prompt template updated", draftDetail.getTemplate());
        
        maintainerService.prompt().forcePublish(NAMESPACE_ID, promptKey, VERSION, true);
        maintainerService.prompt().updateLabels(NAMESPACE_ID, promptKey,
                "{\"latest\":\"" + VERSION + "\",\"stable\":\"" + VERSION + "\"}");
        maintainerService.prompt().updateDescription(NAMESPACE_ID, promptKey,
                "Maintainer SDK IT prompt updated");
        maintainerService.prompt().updateBizTags(NAMESPACE_ID, promptKey,
                "[\"maintainer-sdk-it\",\"prompt\"]");
        maintainerService.prompt().changeOnlineStatus(NAMESPACE_ID, promptKey, VERSION, false);
        maintainerService.prompt().changeOnlineStatus(NAMESPACE_ID, promptKey, VERSION, true);
        
        PromptMetaInfo governance =
                maintainerService.prompt().getPromptGovernanceDetail(NAMESPACE_ID, promptKey);
        assertEquals(promptKey, governance.getPromptKey());
        assertEquals(VERSION, governance.getLabels().get("latest"));
        assertContainsPageItem(maintainerService.prompt().listPrompts(NAMESPACE_ID, promptKey,
                "accurate", null, 1, 10), each -> promptKey.equals(each.getPromptKey()));
        assertContainsPageItem(maintainerService.prompt().listPromptVersions(NAMESPACE_ID,
                promptKey, 1, 10), each -> VERSION.equals(each.getVersion()));
        
        assertTrue(maintainerService.prompt().deletePrompt(NAMESPACE_ID, promptKey));
        assertThrows(NacosException.class,
                () -> maintainerService.prompt().getPromptGovernanceDetail(NAMESPACE_ID,
                        promptKey));
    }
    
    @Test
    void shouldManageSkillAndAgentSpecLifecycle() throws Exception {
        AiMaintainerService maintainerService = createAiMaintainerService();
        
        String skillName = randomMaintainerName("skill");
        String skillCard = buildSkillCard(skillName, "Maintainer SDK IT skill");
        String skillVersion = maintainerService.skill()
                .createDraft(NAMESPACE_ID, skillName, null, VERSION, skillCard, "create skill");
        assertEquals(VERSION, skillVersion);
        addCleanup(() -> maintainerService.skill().deleteSkill(NAMESPACE_ID, skillName));
        
        String updatedSkillCard = buildSkillCard(skillName, "Maintainer SDK IT skill updated");
        assertTrue(maintainerService.skill()
                .updateDraft(NAMESPACE_ID, updatedSkillCard, true, "update skill"));
        Skill skillDraft =
                maintainerService.skill().getSkillVersionDetail(NAMESPACE_ID, skillName, VERSION);
        assertEquals(skillName, skillDraft.getName());
        assertEquals("Maintainer SDK IT skill updated", skillDraft.getDescription());
        assertTrue(skillDraft.getSkillMd().contains("Maintainer SDK IT skill updated"));
        
        assertTrue(maintainerService.skill().forcePublish(NAMESPACE_ID, skillName, VERSION, true));
        assertTrue(maintainerService.skill().updateLabels(NAMESPACE_ID, skillName,
                "{\"latest\":\"" + VERSION + "\",\"stable\":\"" + VERSION + "\"}"));
        assertTrue(maintainerService.skill().updateBizTags(NAMESPACE_ID, skillName,
                "[\"maintainer-sdk-it\",\"skill\"]"));
        assertTrue(maintainerService.skill().updateScope(NAMESPACE_ID, skillName, "PUBLIC"));
        assertTrue(maintainerService.skill()
                .changeOnlineStatus(NAMESPACE_ID, skillName, "version", VERSION, false));
        assertTrue(maintainerService.skill()
                .changeOnlineStatus(NAMESPACE_ID, skillName, "version", VERSION, true));
        
        SkillMeta skillMeta = maintainerService.skill().getSkillMeta(NAMESPACE_ID, skillName);
        assertEquals(skillName, skillMeta.getName());
        assertContainsPageItem(maintainerService.skill().listSkills(NAMESPACE_ID, skillName,
                "accurate", 1, 10), each -> skillName.equals(each.getName()));
        
        String agentSpecName = randomMaintainerName("agentspec");
        String agentSpecVersion = maintainerService.agentSpec()
                .createDraft(NAMESPACE_ID, agentSpecName, null, VERSION);
        assertEquals(VERSION, agentSpecVersion);
        addCleanup(() -> maintainerService.agentSpec().deleteAgentSpec(NAMESPACE_ID,
                agentSpecName));
        
        String agentSpecCard =
                buildAgentSpecCard(agentSpecName, "Maintainer SDK IT AgentSpec");
        assertTrue(maintainerService.agentSpec().updateDraft(NAMESPACE_ID, agentSpecCard, true));
        AgentSpec agentSpecDraft = maintainerService.agentSpec()
                .getAgentSpecVersionDetail(NAMESPACE_ID, agentSpecName, VERSION);
        assertEquals(agentSpecName, agentSpecDraft.getName());
        assertTrue(agentSpecDraft.getContent().contains(agentSpecName));
        
        assertTrue(maintainerService.agentSpec()
                .forcePublish(NAMESPACE_ID, agentSpecName, VERSION, true));
        assertTrue(maintainerService.agentSpec().updateLabels(NAMESPACE_ID, agentSpecName,
                "{\"latest\":\"" + VERSION + "\",\"stable\":\"" + VERSION + "\"}"));
        assertTrue(maintainerService.agentSpec().updateBizTags(NAMESPACE_ID, agentSpecName,
                "[\"maintainer-sdk-it\",\"agentspec\"]"));
        assertTrue(maintainerService.agentSpec()
                .updateScope(NAMESPACE_ID, agentSpecName, "PUBLIC"));
        assertTrue(maintainerService.agentSpec()
                .changeOnlineStatus(NAMESPACE_ID, agentSpecName, "version", VERSION, false));
        assertTrue(maintainerService.agentSpec()
                .changeOnlineStatus(NAMESPACE_ID, agentSpecName, "version", VERSION, true));
        
        AgentSpecMeta agentSpecMeta =
                maintainerService.agentSpec().getAgentSpecAdminDetail(NAMESPACE_ID,
                        agentSpecName);
        assertEquals(agentSpecName, agentSpecMeta.getName());
        AgentSpec agentSpecDetail =
                maintainerService.agentSpec().getAgentSpecDetail(NAMESPACE_ID, agentSpecName);
        assertEquals(agentSpecName, agentSpecDetail.getName());
        assertContainsPageItem(maintainerService.agentSpec().listAgentSpecs(NAMESPACE_ID,
                agentSpecName, "accurate", 1, 10), each -> agentSpecName.equals(each.getName()));
        assertContainsPageItem(maintainerService.agentSpec().listAgentSpecAdminItems(NAMESPACE_ID,
                agentSpecName, "accurate", 1, 10),
                each -> agentSpecName.equals(each.getName()));
        
        assertTrue(maintainerService.skill().deleteSkill(NAMESPACE_ID, skillName));
        assertTrue(maintainerService.agentSpec().deleteAgentSpec(NAMESPACE_ID, agentSpecName));
        assertThrows(NacosException.class,
                () -> maintainerService.skill().getSkillMeta(NAMESPACE_ID, skillName));
        assertThrows(NacosException.class,
                () -> maintainerService.agentSpec().getAgentSpecAdminDetail(NAMESPACE_ID,
                        agentSpecName));
    }
    
    @Test
    void shouldUploadSkillFromZipWithTargetVersion() throws Exception {
        AiMaintainerService maintainerService = createAiMaintainerService();
        String skillName = randomMaintainerName("skill-zip");
        String targetVersion = "2.1.0";
        String commitMsg = "upload skill zip";
        
        String uploadedName = maintainerService.skill()
                .uploadSkillFromZip(NAMESPACE_ID,
                        buildSkillZip(skillName, "Maintainer SDK IT uploaded skill", null),
                        false, targetVersion, commitMsg);
        assertEquals(skillName, uploadedName);
        addCleanup(() -> maintainerService.skill().deleteSkill(NAMESPACE_ID, skillName));
        
        Skill detail =
                maintainerService.skill().getSkillVersionDetail(NAMESPACE_ID, skillName,
                        targetVersion);
        assertEquals(skillName, detail.getName());
        assertEquals("Maintainer SDK IT uploaded skill", detail.getDescription());
        assertTrue(detail.getSkillMd().contains("Maintainer SDK IT uploaded skill"));
        
        SkillMeta meta = maintainerService.skill().getSkillMeta(NAMESPACE_ID, skillName);
        assertNotNull(meta.getVersions());
        assertTrue(meta.getVersions().stream()
                .anyMatch(version -> targetVersion.equals(version.getVersion())
                        && commitMsg.equals(version.getCommitMsg())));
        assertEquals(targetVersion,
                maintainerService.skill().submit(NAMESPACE_ID, skillName, targetVersion));
    }
    
    @Test
    void shouldBatchUploadSkillsFromZip() throws Exception {
        AiMaintainerService maintainerService = createAiMaintainerService();
        String firstSkillName = randomMaintainerName("skill-batch-one");
        String secondSkillName = randomMaintainerName("skill-batch-two");
        addCleanup(() -> maintainerService.skill().deleteSkill(NAMESPACE_ID, secondSkillName));
        addCleanup(() -> maintainerService.skill().deleteSkill(NAMESPACE_ID, firstSkillName));
        
        BatchUploadResult result = maintainerService.skill()
                .batchUploadSkillsFromZip(NAMESPACE_ID,
                        buildMultiSkillZip(
                                buildSkill(firstSkillName, "Maintainer SDK IT batch skill one",
                                        VERSION),
                                buildSkill(secondSkillName, "Maintainer SDK IT batch skill two",
                                        VERSION)),
                        false);
        
        assertNotNull(result);
        assertTrue(result.getFailed().isEmpty(), () -> result.getFailed().toString());
        assertTrue(result.getSucceeded().contains(firstSkillName));
        assertTrue(result.getSucceeded().contains(secondSkillName));
        assertEquals(firstSkillName, maintainerService.skill()
                .getSkillVersionDetail(NAMESPACE_ID, firstSkillName, VERSION).getName());
        assertEquals(secondSkillName, maintainerService.skill()
                .getSkillVersionDetail(NAMESPACE_ID, secondSkillName, VERSION).getName());
    }
    
    @Test
    void shouldUploadAgentSpecFromZip() throws Exception {
        AiMaintainerService maintainerService = createAiMaintainerService();
        String agentSpecName = randomMaintainerName("agentspec-zip");
        
        String uploadedName = maintainerService.agentSpec()
                .uploadAgentSpecFromZip(NAMESPACE_ID,
                        buildAgentSpecZip(agentSpecName,
                                "Maintainer SDK IT uploaded AgentSpec"),
                        false);
        assertEquals(agentSpecName, uploadedName);
        addCleanup(() -> maintainerService.agentSpec().deleteAgentSpec(NAMESPACE_ID,
                agentSpecName));
        
        AgentSpec detail = maintainerService.agentSpec()
                .getAgentSpecVersionDetail(NAMESPACE_ID, agentSpecName, "0.0.1");
        assertEquals(agentSpecName, detail.getName());
        assertEquals("Maintainer SDK IT uploaded AgentSpec", detail.getDescription());
        assertNotNull(detail.getResource());
        assertTrue(detail.getResource().values().stream()
                .anyMatch(resource -> "README.md".equals(resource.getName())
                        && "docs".equals(resource.getType())));
        
        AgentSpec meta = maintainerService.agentSpec()
                .getAgentSpecVersionMeta(NAMESPACE_ID, agentSpecName, "0.0.1");
        assertEquals(agentSpecName, meta.getName());
        assertNotNull(meta.getResource());
        assertTrue(meta.getResource().values().stream()
                .anyMatch(resource -> "README.md".equals(resource.getName())
                        && "docs".equals(resource.getType())));
        assertEquals("0.0.1",
                maintainerService.agentSpec().submit(NAMESPACE_ID, agentSpecName, "0.0.1"));
    }
    
    @Test
    void shouldRejectInvalidAiMaintainerParameters() throws Exception {
        AiMaintainerService maintainerService = createAiMaintainerService();
        
        NacosException nullProperties = assertThrows(NacosException.class,
                () -> AiMaintainerFactory.createAiMaintainerService(null));
        assertEquals(NacosException.INVALID_PARAM, nullProperties.getErrCode());
        
        McpServerBasicInfo remoteProtocol =
                buildMcpServer(randomMaintainerName("invalid-mcp"), VERSION, "invalid");
        remoteProtocol.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_SSE);
        NacosException localProtocol = assertThrows(NacosException.class,
                () -> maintainerService.mcp().createLocalMcpServer(remoteProtocol.getName(),
                        remoteProtocol, null));
        assertEquals(NacosException.INVALID_PARAM, localProtocol.getErrCode());
        
        NacosException missingEndpoint = assertThrows(NacosException.class,
                () -> maintainerService.mcp().createRemoteMcpServer(
                        randomMaintainerName("invalid-remote"), VERSION,
                        AiConstants.Mcp.MCP_PROTOCOL_SSE, null));
        assertEquals(NacosException.INVALID_PARAM, missingEndpoint.getErrCode());
    }
    
    private McpServerBasicInfo buildMcpServer(String mcpName, String version,
            String description) {
        McpServerBasicInfo result = new McpServerBasicInfo();
        result.setName(mcpName);
        result.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        result.setDescription(description);
        ServerVersionDetail versionDetail = new ServerVersionDetail();
        versionDetail.setVersion(version);
        result.setVersionDetail(versionDetail);
        return result;
    }
    
    private McpToolSpecification buildMcpToolSpecification(String mcpName) {
        McpTool tool = new McpTool();
        tool.setName("tool_" + mcpName.replace('-', '_'));
        tool.setDescription("Echo text for Maintainer SDK IT");
        tool.setInputSchema(Collections.singletonMap("type", "object"));
        McpToolSpecification result = new McpToolSpecification();
        result.setTools(Collections.singletonList(tool));
        return result;
    }
    
    private AgentCard buildAgentCard(String agentName, String version, String description) {
        AgentInterface jsonRpc = new AgentInterface();
        jsonRpc.setUrl("https://example.com/" + agentName + "/jsonrpc");
        jsonRpc.setProtocolBinding(AiConstants.A2a.A2A_ENDPOINT_DEFAULT_TRANSPORT);
        jsonRpc.setProtocolVersion("1.0");
        
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.setStreaming(true);
        capabilities.setExtendedAgentCard(true);
        
        AgentCard result = new AgentCard();
        result.setName(agentName);
        result.setVersion(version);
        result.setDescription(description);
        result.setSupportedInterfaces(Collections.singletonList(jsonRpc));
        result.setCapabilities(capabilities);
        return result;
    }
    
    private String buildSkillCard(String skillName, String description) {
        return JacksonUtils.toJson(buildSkill(skillName, description, null));
    }
    
    private Skill buildSkill(String skillName, String description, String version) {
        Skill skill = new Skill();
        skill.setNamespaceId(NAMESPACE_ID);
        skill.setName(skillName);
        skill.setDescription(description);
        StringBuilder skillMd = new StringBuilder("---\nname: ").append(skillName)
                .append("\ndescription: ").append(description).append('\n');
        if (null != version) {
            skillMd.append("version: ").append(version).append('\n');
        }
        skillMd.append("---\n\n").append(description).append('\n');
        skill.setSkillMd(skillMd.toString());
        return skill;
    }
    
    private String buildAgentSpecCard(String agentSpecName, String description) {
        AgentSpec agentSpec = new AgentSpec();
        agentSpec.setNamespaceId(NAMESPACE_ID);
        agentSpec.setName(agentSpecName);
        agentSpec.setDescription(description);
        agentSpec.setBizTags("[\"maintainer-sdk-it\"]");
        agentSpec.setContent(JacksonUtils.toJson(buildAgentSpecManifest(agentSpecName,
                description)));
        return JacksonUtils.toJson(agentSpec);
    }
    
    private byte[] buildSkillZip(String skillName, String description, String version)
            throws IOException {
        return SkillUtils.toZipBytes(buildSkill(skillName, description, version));
    }
    
    private byte[] buildMultiSkillZip(Skill... skills) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            for (Skill skill : skills) {
                writeZipEntry(zipOutputStream, skill.getName() + "/SKILL.md",
                        skill.getSkillMd());
            }
        }
        return outputStream.toByteArray();
    }
    
    private byte[] buildAgentSpecZip(String agentSpecName, String description)
            throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            writeZipEntry(zipOutputStream, "manifest.json",
                    JacksonUtils.toJson(buildAgentSpecManifest(agentSpecName, description)));
            writeZipEntry(zipOutputStream, "docs/README.md", description);
        }
        return outputStream.toByteArray();
    }
    
    private void writeZipEntry(ZipOutputStream zipOutputStream, String name, String content)
            throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(name));
        zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }
    
    private Map<String, Object> buildAgentSpecManifest(String agentSpecName, String description) {
        Map<String, Object> worker = new HashMap<>();
        worker.put("suggested_name", agentSpecName);
        Map<String, Object> result = new HashMap<>();
        result.put("version", "1.0");
        result.put("description", description);
        result.put("worker", worker);
        return result;
    }
    
    private void assertMcpServer(McpServerDetailInfo detail, String mcpName, String version,
            String description) {
        assertNotNull(detail);
        assertEquals(mcpName, detail.getName());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STDIO, detail.getProtocol());
        assertEquals(description, detail.getDescription());
        assertNotNull(detail.getVersionDetail());
        assertEquals(version, detail.getVersionDetail().getVersion());
    }
    
    private void assertAgentCard(AgentCardDetailInfo detail, String agentName, String version,
            String description) {
        assertNotNull(detail);
        assertEquals(agentName, detail.getName());
        assertEquals(version, detail.getVersion());
        assertEquals(description, detail.getDescription());
        assertEquals(AiConstants.A2a.A2A_ENDPOINT_TYPE_URL, detail.getRegistrationType());
        assertNotNull(detail.getSupportedInterfaces());
        assertFalse(detail.getSupportedInterfaces().isEmpty());
    }
    
    private <T> void assertPage(Page<T> page) {
        assertNotNull(page);
        assertNotNull(page.getPageItems());
    }
    
    private <T> void assertContainsPageItem(Page<T> page, Predicate<T> predicate) {
        assertPage(page);
        assertTrue(containsPageItem(page, predicate),
                () -> "Expected page item was not found in " + page.getPageItems());
    }
    
    private <T> void assertNoPageItem(Page<T> page, Predicate<T> predicate) {
        assertPage(page);
        assertFalse(containsPageItem(page, predicate),
                () -> "Unexpected page item was found in " + page.getPageItems());
    }
    
    private <T> boolean containsPageItem(Page<T> page, Predicate<T> predicate) {
        return page.getPageItems().stream().anyMatch(predicate);
    }
    
    private void assertSuccessResult(Result<JsonNode> result) {
        assertNotNull(result);
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode(), result.toString());
        assertNotNull(result.getData());
    }
}
