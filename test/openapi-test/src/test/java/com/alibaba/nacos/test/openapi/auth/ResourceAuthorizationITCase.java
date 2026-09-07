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

package com.alibaba.nacos.test.openapi.auth;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resource and action boundary tests for the default authorization plugin.
 *
 * <p>These scenarios use exact production resource strings. They verify that a permission for
 * one Config, Naming, or AI resource does not authorize an adjacent resource, group, namespace,
 * type, or write action. Console management remains global-administrator-only even if an
 * ordinary role is manually assigned the corresponding console resource.</p>
 *
 * @author Nacos
 */
public class ResourceAuthorizationITCase extends AuthITCase {

    private static final String CONFIG_ADMIN_PATH = CONTEXT_PATH + "/v3/admin/cs/config";

    private static final String CONFIG_CLIENT_PATH = CONTEXT_PATH + "/v3/client/cs/config";

    private static final String NAMING_INSTANCE_PATH =
            CONTEXT_PATH + "/v3/client/ns/instance";

    private static final String NAMING_INSTANCE_LIST_PATH = NAMING_INSTANCE_PATH + "/list";

    private static final String SKILL_ADMIN_PATH = CONTEXT_PATH + "/v3/admin/ai/skills";

    private static final String SKILL_CLIENT_PATH = CONTEXT_PATH + "/v3/client/ai/skills";

    private static final String VISIBILITY_PATH = CONTEXT_PATH + "/v3/auth/visibility";

    private static final String USER_PATH = CONTEXT_PATH + "/v3/auth/user";

    @Test
    void testConfigResourceNameGroupNamespaceAndActionBoundaries() throws Exception {
        TestIdentity reader = createIdentityWithoutPermission("config-boundary");
        String suffix = randomSuffix();
        String dataId = "auth-config-a-" + suffix;
        String adjacentDataId = "auth-config-b-" + suffix;
        String group = "AUTH_CONFIG_GROUP_" + suffix.toUpperCase();
        String adjacentGroup = group + "_OTHER";
        String initialContent = "initial-" + suffix;
        String updatedContent = "updated-" + suffix;

        createConfig(dataId, group, initialContent);
        createConfig(adjacentDataId, group, "adjacent-" + suffix);
        addCleanup(() -> deleteForm(SERVER_BASE_URL, CONFIG_ADMIN_PATH, adminToken(),
                configIdentity(dataId, group, "public")));
        addCleanup(() -> deleteForm(SERVER_BASE_URL, CONFIG_ADMIN_PATH, adminToken(),
                configIdentity(adjacentDataId, group, "public")));

        String resource = "public:" + group + ":config/" + dataId;
        grantReadPermission(reader, resource);

        Response exactRead = awaitSuccess(() -> get(SERVER_BASE_URL,
                configQuery(CONFIG_CLIENT_PATH, dataId, group, "public"), reader.token()));
        assertConfigContent(exactRead, initialContent);
        assertDenied(get(SERVER_BASE_URL,
                configQuery(CONFIG_CLIENT_PATH, adjacentDataId, group, "public"),
                reader.token()));
        assertDenied(get(SERVER_BASE_URL,
                configQuery(CONFIG_CLIENT_PATH, dataId, adjacentGroup, "public"),
                reader.token()));
        assertDenied(get(SERVER_BASE_URL,
                configQuery(CONFIG_CLIENT_PATH, dataId, group, "other-" + suffix),
                reader.token()));

        Map<String, String> update = configForm(dataId, group, "public", updatedContent);
        assertDenied(postForm(SERVER_BASE_URL, CONFIG_ADMIN_PATH, reader.token(), update));
        assertConfigContent(get(SERVER_BASE_URL,
                configQuery(CONFIG_ADMIN_PATH, dataId, group, "public"), adminToken()),
                initialContent);

        grantPermission(reader, resource, "w");
        assertSuccess(awaitSuccess(() -> postForm(SERVER_BASE_URL, CONFIG_ADMIN_PATH,
                reader.token(), update)));
        assertConfigContent(get(SERVER_BASE_URL,
                configQuery(CONFIG_ADMIN_PATH, dataId, group, "public"), adminToken()),
                updatedContent);
    }

    @Test
    void testNamingResourceNameGroupAndActionBoundaries() throws Exception {
        TestIdentity reader = createIdentityWithoutPermission("naming-boundary");
        String suffix = randomSuffix();
        String serviceName = "auth-naming-a-" + suffix;
        String adjacentService = "auth-naming-b-" + suffix;
        String group = "AUTH_NAMING_GROUP_" + suffix.toUpperCase();
        String adjacentGroup = group + "_OTHER";
        String ip = "10.24.0.1";
        String port = "9824";
        String resource = "public:" + group + ":naming/" + serviceName;
        grantReadPermission(reader, resource);

        String exactList = namingListQuery(serviceName, group, "public");
        assertSuccess(awaitSuccess(() -> get(SERVER_BASE_URL, exactList, reader.token())));
        assertDenied(get(SERVER_BASE_URL,
                namingListQuery(adjacentService, group, "public"), reader.token()));
        assertDenied(get(SERVER_BASE_URL,
                namingListQuery(serviceName, adjacentGroup, "public"), reader.token()));
        assertDenied(get(SERVER_BASE_URL,
                namingListQuery(serviceName, group, "other-" + suffix), reader.token()));

        Map<String, String> instance = params("namespaceId", "public", "groupName", group,
                "serviceName", serviceName, "ip", ip, "port", port,
                "clusterName", "DEFAULT");
        assertDenied(postForm(SERVER_BASE_URL, NAMING_INSTANCE_PATH, reader.token(), instance));
        assertEquals(0, assertSuccess(get(SERVER_BASE_URL, exactList, reader.token()))
                .path("data").size());

        grantPermission(reader, resource, "w");
        assertSuccess(awaitSuccess(() -> postForm(SERVER_BASE_URL, NAMING_INSTANCE_PATH,
                reader.token(), instance)));
        addCleanup(() -> deleteForm(SERVER_BASE_URL, NAMING_INSTANCE_PATH, adminToken(),
                instance));
        JsonNode listed = assertSuccess(awaitSuccess(
                () -> get(SERVER_BASE_URL, exactList, reader.token()))).path("data");
        assertEquals(1, listed.size(), listed.toString());
        assertEquals(ip, listed.get(0).path("ip").asText(), listed.toString());
        assertEquals(Integer.parseInt(port), listed.get(0).path("port").asInt(),
                listed.toString());
    }

    @Disabled("DAUTH-F01: Skill Client name is not mapped to the authorization resource; "
            + "see UNEXPECTED_PRODUCT_FINDINGS.md")
    @Test
    void testAiRequestPermissionAndVisibilityGrantBoundaries() throws Exception {
        TestIdentity reader = createIdentityWithoutPermission("ai-boundary-reader");
        String suffix = randomSuffix();
        String skillName = "auth-skill-a-" + suffix;
        String adjacentSkill = "auth-skill-b-" + suffix;
        createAndPublishSkill(skillName, adminToken());
        createAndPublishSkill(adjacentSkill, adminToken());
        addCleanup(() -> deleteForm(SERVER_BASE_URL, SKILL_ADMIN_PATH, adminToken(),
                skillIdentity(skillName)));
        addCleanup(() -> deleteForm(SERVER_BASE_URL, SKILL_ADMIN_PATH, adminToken(),
                skillIdentity(adjacentSkill)));

        grantReadPermission(reader, "public:DEFAULT_GROUP:ai/" + skillName);
        String skillQuery = skillQuery(skillName);
        Response hidden = awaitAuthorized(
                () -> get(SERVER_BASE_URL, skillQuery, reader.token()));
        assertNotEquals(403, hidden.status(), hidden.body());
        assertNotEquals(200, hidden.status(), hidden.body());
        assertDenied(get(SERVER_BASE_URL, skillQuery(adjacentSkill), reader.token()));

        Map<String, String> visibility = visibilityGrant(skillName, reader.username());
        assertSuccess(postForm(SERVER_BASE_URL, VISIBILITY_PATH, adminToken(), visibility));
        addCleanup(() -> deleteForm(SERVER_BASE_URL, VISIBILITY_PATH, adminToken(),
                visibility));
        Response visible = awaitStatus(
                () -> get(SERVER_BASE_URL, skillQuery, reader.token()), 200);
        assertEquals(200, visible.status(), visible.body());

        assertSuccess(deleteForm(SERVER_BASE_URL, VISIBILITY_PATH, adminToken(), visibility));
        Response hiddenAgain = awaitStatus(
                () -> get(SERVER_BASE_URL, skillQuery, reader.token()), 404);
        assertEquals(404, hiddenAgain.status(), hiddenAgain.body());
    }

    @Test
    void testAnonymousSkillRejectsExplicitInvalidCredentials() throws Exception {
        String skillQuery = skillQuery("auth-missing-skill-" + randomSuffix());

        Response anonymous = get(SERVER_BASE_URL, skillQuery, null);
        assertEquals(404, anonymous.status(), anonymous.body());
        assertDenied(getWithAuthorization(SERVER_BASE_URL, skillQuery,
                "Bearer invalid-token"));
        assertDenied(getWithAuthorization(SERVER_BASE_URL, skillQuery, ""));
        assertDenied(get(SERVER_BASE_URL, skillQuery + "&accessToken=invalid-token", null));
        assertDenied(get(SERVER_BASE_URL, skillQuery + "&accessToken=", null));
    }

    @Test
    void testVisibilityOwnerAndConsoleGlobalAdminBoundaries() throws Exception {
        TestIdentity owner = createIdentityWithoutPermission("visibility-owner");
        TestIdentity grantee = createIdentityWithoutPermission("visibility-grantee");
        String skillName = "auth-owner-skill-" + randomSuffix();
        grantPermission(owner, "public:DEFAULT_GROUP:ai/" + skillName, "rw");
        assertSuccess(awaitSuccess(
                () -> postForm(SERVER_BASE_URL, SKILL_ADMIN_PATH + "/draft", owner.token(),
                        skillDraft(skillName))));
        addCleanup(() -> deleteForm(SERVER_BASE_URL, SKILL_ADMIN_PATH, adminToken(),
                skillIdentity(skillName)));

        Map<String, String> visibility = visibilityGrant(skillName, grantee.username());
        assertSuccess(postForm(SERVER_BASE_URL, VISIBILITY_PATH, owner.token(), visibility));
        addCleanup(() -> deleteForm(SERVER_BASE_URL, VISIBILITY_PATH, owner.token(),
                visibility));

        Map<String, String> forbiddenGrant = visibilityGrant(skillName, owner.username());
        assertDenied(postForm(SERVER_BASE_URL, VISIBILITY_PATH, grantee.token(),
                forbiddenGrant));
        assertSuccess(deleteForm(SERVER_BASE_URL, VISIBILITY_PATH, owner.token(), visibility));

        grantReadPermission(owner, "console/users");
        assertDenied(get(SERVER_BASE_URL, USER_PATH + "/list?pageNo=1&pageSize=10",
                owner.token()));
        assertSuccess(get(SERVER_BASE_URL, USER_PATH + "/list?pageNo=1&pageSize=10",
                adminToken()));
    }

    private void createConfig(String dataId, String group, String content) throws Exception {
        assertSuccess(postForm(SERVER_BASE_URL, CONFIG_ADMIN_PATH, adminToken(),
                configForm(dataId, group, "public", content)));
    }

    private void assertConfigContent(Response response, String expectedContent) {
        assertEquals(expectedContent,
                assertSuccess(response).path("data").path("content").asText(), response.body());
    }

    private Map<String, String> configIdentity(String dataId, String group,
            String namespaceId) {
        return params("dataId", dataId, "groupName", group, "namespaceId", namespaceId);
    }

    private Map<String, String> configForm(String dataId, String group, String namespaceId,
            String content) {
        Map<String, String> result = configIdentity(dataId, group, namespaceId);
        result.put("content", content);
        return result;
    }

    private String configQuery(String path, String dataId, String group, String namespaceId) {
        return path + "?dataId=" + dataId + "&groupName=" + group + "&namespaceId="
                + namespaceId;
    }

    private String namingListQuery(String serviceName, String group, String namespaceId) {
        return NAMING_INSTANCE_LIST_PATH + "?serviceName=" + serviceName + "&groupName="
                + group + "&namespaceId=" + namespaceId;
    }

    private void createAndPublishSkill(String skillName, String token) throws Exception {
        assertSuccess(postForm(SERVER_BASE_URL, SKILL_ADMIN_PATH + "/draft", token,
                skillDraft(skillName)));
        assertSuccess(postForm(SERVER_BASE_URL, SKILL_ADMIN_PATH + "/force-publish", token,
                params("namespaceId", "public", "skillName", skillName,
                        "version", "1.0.0")));
    }

    private Map<String, String> skillDraft(String skillName) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("name", skillName);
        card.put("description", "authorization boundary skill");
        card.put("skillMd", "# " + skillName + "\n\nAuthorization boundary body.");
        card.put("resource", Map.of());
        return params("namespaceId", "public", "skillName", skillName,
                "targetVersion", "1.0.0", "skillCard", JacksonUtils.toJson(card),
                "commitMsg", "authorization boundary test");
    }

    private Map<String, String> skillIdentity(String skillName) {
        return params("namespaceId", "public", "skillName", skillName);
    }

    private String skillQuery(String skillName) {
        return SKILL_CLIENT_PATH + "?namespaceId=public&name=" + skillName;
    }

    private Map<String, String> visibilityGrant(String skillName, String username) {
        return params("namespaceId", "public", "resourceType", "skill",
                "resourceName", skillName, "username", username, "action", "r");
    }

    private String randomSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
