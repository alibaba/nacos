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

import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SkillRequestUtil} frontmatter normalization and utility methods.
 *
 * @author nacos
 */
class SkillRequestUtilTest {
    
    // ========== parseFrontmatterField ==========
    
    @Test
    void testParseFrontmatterFieldReturnsValueWhenPresent() {
        String md = "---\nname: my-skill\ndescription: hello\n---\n# Body";
        assertEquals("my-skill", SkillRequestUtil.parseFrontmatterField(md, "name"));
        assertEquals("hello", SkillRequestUtil.parseFrontmatterField(md, "description"));
    }
    
    @Test
    void testParseFrontmatterFieldReturnsNullWhenFieldMissing() {
        String md = "---\nname: my-skill\n---\n# Body";
        assertNull(SkillRequestUtil.parseFrontmatterField(md, "version"));
    }
    
    @Test
    void testParseFrontmatterFieldReturnsNullWhenNoFrontmatter() {
        String md = "# Just markdown\nNo frontmatter here.";
        assertNull(SkillRequestUtil.parseFrontmatterField(md, "name"));
    }
    
    @Test
    void testParseFrontmatterFieldStripsQuotes() {
        String md = "---\nname: \"quoted-name\"\ndescription: 'single-quoted'\n---\n";
        assertEquals("quoted-name", SkillRequestUtil.parseFrontmatterField(md, "name"));
        assertEquals("single-quoted", SkillRequestUtil.parseFrontmatterField(md, "description"));
    }
    
    @Test
    void testParseFrontmatterFieldReturnsNullForEmptyInput() {
        assertNull(SkillRequestUtil.parseFrontmatterField(null, "name"));
        assertNull(SkillRequestUtil.parseFrontmatterField("", "name"));
    }
    
    // ========== hasNonFrontmatterContent / validateSkill ==========
    
    @Test
    void testHasNonFrontmatterContentReturnsFalseWhenOnlyFrontmatter() {
        String md = "---\nname: test-skill\ndescription: desc\n---\n\n   \n";
        assertEquals(false, SkillRequestUtil.hasNonFrontmatterContent(md));
    }
    
    @Test
    void testHasNonFrontmatterContentReturnsTrueWhenBodyExists() {
        String md = "---\nname: test-skill\ndescription: desc\n---\n\n## steps";
        assertTrue(SkillRequestUtil.hasNonFrontmatterContent(md));
    }
    
    @Test
    void testValidateSkillThrowsWhenMarkdownBodyEmptyAfterFrontmatter() {
        Skill skill =
            buildSkill("test-skill", "desc", "---\nname: test-skill\ndescription: desc\n---\n\n");
        NacosApiException exception =
            assertThrows(NacosApiException.class, () -> SkillRequestUtil.validateSkill(skill));
        assertTrue(exception.getErrMsg().contains("markdown body should not be empty"));
    }
    
    // ========== updateFrontmatterField ==========
    
    @Test
    void testUpdateFrontmatterFieldUpdatesExistingField() {
        String md = "---\nname: old-name\ndescription: hello\n---\n# Body";
        String result = SkillRequestUtil.updateFrontmatterField(md, "name", "new-name");
        assertEquals("new-name", SkillRequestUtil.parseFrontmatterField(result, "name"));
        assertEquals("hello", SkillRequestUtil.parseFrontmatterField(result, "description"));
        assertTrue(result.contains("# Body"));
    }
    
    @Test
    void testUpdateFrontmatterFieldInsertsAtBeginningWhenMissing() {
        String md = "---\ndescription: hello\n---\n# Body";
        String result = SkillRequestUtil.updateFrontmatterField(md, "name", "inserted-name");
        assertEquals("inserted-name", SkillRequestUtil.parseFrontmatterField(result, "name"));
        // name should appear before description in the frontmatter
        int nameIdx = result.indexOf("name:");
        int descIdx = result.indexOf("description:");
        assertTrue(nameIdx < descIdx, "name should be inserted at the beginning of frontmatter");
    }
    
    @Test
    void testUpdateFrontmatterFieldCreatesNewFrontmatterWhenNone() {
        String md = "# Just markdown";
        String result = SkillRequestUtil.updateFrontmatterField(md, "name", "new-skill");
        assertEquals("new-skill", SkillRequestUtil.parseFrontmatterField(result, "name"));
        assertTrue(result.contains("# Just markdown"));
    }
    
    @Test
    void testUpdateFrontmatterFieldHandlesEmptyInput() {
        String result = SkillRequestUtil.updateFrontmatterField("", "name", "test");
        assertEquals("test", SkillRequestUtil.parseFrontmatterField(result, "name"));
    }
    
    @Test
    void testUpdateFrontmatterFieldPreservesNestedIndent() {
        // Issue #14997: nested `metadata.version` lost its indent and was promoted to a top-level key.
        String md = "---\nname: hello-world\nmetadata:\n  version: latest\n  openclaw:\n"
            + "    emoji: 👋\n---\n# Body";
        String result = SkillRequestUtil.updateFrontmatterField(md, "version", "latest");
        assertTrue(result.contains("\n  version: latest\n"),
            "Nested version should keep its 2-space indent");
        assertTrue(!result.contains("\nversion: latest\n"),
            "Nested version must not be promoted to a top-level key");
        assertTrue(result.contains("\n  openclaw:\n    emoji: 👋\n"),
            "Sibling nested keys must remain intact");
    }
    
    @Test
    void testUpdateFrontmatterFieldUpdatesNestedValuePreservingIndent() {
        String md = "---\nname: my-skill\nmetadata:\n  version: 1.0.0\n---\n# Body";
        String result = SkillRequestUtil.updateFrontmatterField(md, "version", "2.0.0");
        assertTrue(result.contains("\n  version: 2.0.0\n"),
            "Nested version value should be updated while preserving indent");
        assertTrue(!result.contains("\nversion: 2.0.0\n"));
    }
    
    @Test
    void testUpdateFrontmatterFieldKeepsTopLevelBehavior() {
        String md = "---\nname: my-skill\nversion: 0.0.1\n---\n# Body";
        String result = SkillRequestUtil.updateFrontmatterField(md, "version", "2.0.0");
        assertTrue(result.contains("\nversion: 2.0.0\n"),
            "Top-level version must remain at the top level after update");
        assertTrue(!result.contains("  version: 2.0.0"),
            "Top-level version must not gain unexpected indentation");
    }
    
    // ========== normalizeSkillFrontmatter — first create ==========
    
    @Test
    void testNormalizeFirstCreateUsesFrontmatterNameWhenPresent() {
        Skill skill = buildSkill("request-name", "req-desc",
            "---\nname: fm-name\ndescription: fm-desc\nversion: 1.0.0\n---\n# Body");
        SkillRequestUtil.normalizeSkillFrontmatter(skill, "request-name", "2.0.0", true);
        // First create: frontmatter name wins
        assertEquals("fm-name", skill.getName());
        assertEquals("fm-name", SkillRequestUtil.parseFrontmatterField(skill.getSkillMd(), "name"));
    }
    
    @Test
    void testNormalizeFirstCreateUsesAuthoritativeNameWhenFrontmatterNameMissing() {
        Skill skill = buildSkill("request-name", "req-desc",
            "---\ndescription: fm-desc\n---\n# Body");
        SkillRequestUtil.normalizeSkillFrontmatter(skill, "request-name", "0.0.1", true);
        assertEquals("request-name", skill.getName());
        assertEquals("request-name",
            SkillRequestUtil.parseFrontmatterField(skill.getSkillMd(), "name"));
    }
    
    @Test
    void testNormalizeFirstCreateUsesFrontmatterDescriptionWhenPresent() {
        Skill skill = buildSkill("my-skill", "req-desc",
            "---\nname: my-skill\ndescription: fm-desc\n---\n# Body");
        SkillRequestUtil.normalizeSkillFrontmatter(skill, "my-skill", "0.0.1", true);
        assertEquals("fm-desc", skill.getDescription());
    }
    
    @Test
    void testNormalizeFirstCreateUsesSkillDescriptionWhenFrontmatterMissing() {
        Skill skill = buildSkill("my-skill", "req-desc",
            "---\nname: my-skill\n---\n# Body");
        SkillRequestUtil.normalizeSkillFrontmatter(skill, "my-skill", "0.0.1", true);
        assertEquals("req-desc", skill.getDescription());
        assertEquals("req-desc",
            SkillRequestUtil.parseFrontmatterField(skill.getSkillMd(), "description"));
    }
    
    @Test
    void testNormalizeFirstCreateSyncsVersionToFrontmatter() {
        Skill skill = buildSkill("my-skill", "desc",
            "---\nname: my-skill\nversion: 0.0.1\n---\n# Body");
        SkillRequestUtil.normalizeSkillFrontmatter(skill, "my-skill", "1.2.3", true);
        assertEquals("1.2.3",
            SkillRequestUtil.parseFrontmatterField(skill.getSkillMd(), "version"));
    }
    
    // ========== normalizeSkillFrontmatter — edit (not first create) ==========
    
    @Test
    void testNormalizeEditOverridesNameWithAuthoritative() {
        Skill skill = buildSkill("wrong-name", "desc",
            "---\nname: wrong-name\ndescription: desc\n---\n# Body");
        SkillRequestUtil.normalizeSkillFrontmatter(skill, "correct-name", "1.0.0", false);
        // Edit: authoritative name wins
        assertEquals("correct-name", skill.getName());
        assertEquals("correct-name",
            SkillRequestUtil.parseFrontmatterField(skill.getSkillMd(), "name"));
    }
    
    @Test
    void testNormalizeEditUsesFrontmatterDescriptionWhenPresent() {
        Skill skill = buildSkill("my-skill", "old-desc",
            "---\nname: my-skill\ndescription: new-desc\n---\n# Body");
        SkillRequestUtil.normalizeSkillFrontmatter(skill, "my-skill", "1.0.0", false);
        assertEquals("new-desc", skill.getDescription());
    }
    
    @Test
    void testNormalizeEditUsesSkillDescriptionWhenFrontmatterMissing() {
        Skill skill = buildSkill("my-skill", "skill-desc",
            "---\nname: my-skill\n---\n# Body");
        SkillRequestUtil.normalizeSkillFrontmatter(skill, "my-skill", "1.0.0", false);
        assertEquals("skill-desc", skill.getDescription());
        assertEquals("skill-desc",
            SkillRequestUtil.parseFrontmatterField(skill.getSkillMd(), "description"));
    }
    
    @Test
    void testNormalizeEditSyncsVersionToFrontmatter() {
        Skill skill = buildSkill("my-skill", "desc",
            "---\nname: my-skill\nversion: 0.0.1\n---\n# Body");
        SkillRequestUtil.normalizeSkillFrontmatter(skill, "my-skill", "0.0.2", false);
        assertEquals("0.0.2",
            SkillRequestUtil.parseFrontmatterField(skill.getSkillMd(), "version"));
    }
    
    @Test
    void testNormalizeEditRestoresDeletedName() {
        // Simulates the original bug: user deleted name from frontmatter
        Skill skill = buildSkill("my-skill", "desc",
            "---\ndescription: desc\n---\n# Body");
        SkillRequestUtil.normalizeSkillFrontmatter(skill, "my-skill", "1.0.0", false);
        assertEquals("my-skill", skill.getName());
        assertEquals("my-skill",
            SkillRequestUtil.parseFrontmatterField(skill.getSkillMd(), "name"));
    }
    
    // ========== normalizeSkillFrontmatter — edge cases ==========
    
    @Test
    void testNormalizeSkipsWhenSkillMdIsEmpty() {
        Skill skill = buildSkill("my-skill", "desc", "");
        SkillRequestUtil.normalizeSkillFrontmatter(skill, "my-skill", "1.0.0", true);
        // Should not throw, skillMd remains empty
        assertEquals("", skill.getSkillMd());
    }
    
    @Test
    void testNormalizeSkipsWhenSkillMdIsNull() {
        Skill skill = buildSkill("my-skill", "desc", null);
        SkillRequestUtil.normalizeSkillFrontmatter(skill, "my-skill", "1.0.0", true);
        assertNull(skill.getSkillMd());
    }
    
    @Test
    void testNormalizeCreatesFullFrontmatterWhenNoneExists() {
        Skill skill = buildSkill("my-skill", "desc", "# Just body content");
        SkillRequestUtil.normalizeSkillFrontmatter(skill, "my-skill", "0.0.1", true);
        assertEquals("my-skill",
            SkillRequestUtil.parseFrontmatterField(skill.getSkillMd(), "name"));
        assertEquals("desc",
            SkillRequestUtil.parseFrontmatterField(skill.getSkillMd(), "description"));
        assertEquals("0.0.1",
            SkillRequestUtil.parseFrontmatterField(skill.getSkillMd(), "version"));
        assertTrue(skill.getSkillMd().contains("# Just body content"));
    }
    
    private static Skill buildSkill(String name, String description, String skillMd) {
        Skill skill = new Skill();
        skill.setName(name);
        skill.setDescription(description);
        skill.setSkillMd(skillMd);
        return skill;
    }
}
