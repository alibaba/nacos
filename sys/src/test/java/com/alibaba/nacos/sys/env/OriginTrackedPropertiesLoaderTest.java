/*
 * Copyright 2012-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.sys.env;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.boot.origin.TextResourceOrigin;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link OriginTrackedPropertiesLoader}. Copied from spring-boot project and do some modified.
 *
 * @author Madhura Bhave
 * @author Phillip Webb
 */
class OriginTrackedPropertiesLoaderTest {
    
    private ClassPathResource resource;
    
    private Map<String, OriginTrackedValue> documents;
    
    @BeforeEach
    void setUp() throws Exception {
        String path = "test-properties.properties";
        this.resource = new ClassPathResource(path);
        this.documents = new OriginTrackedPropertiesLoader(this.resource).load();
    }
    
    @Test
    void compareToJavaProperties() throws Exception {
        Properties java = PropertiesLoaderUtils.loadProperties(this.resource);
        Properties ours = new Properties();
        new OriginTrackedPropertiesLoader(this.resource).load(false).forEach((k, v) -> ours.put(k, v.getValue()));
        assertEquals(java, ours);
    }
    
    @Test
    void getSimpleProperty() {
        OriginTrackedValue value = getFromFirst("test");
        assertEquals("properties", getValue(value));
        assertEquals("27:6", getLocation(value));
    }
    
    @Test
    void getSimplePropertyWithColonSeparator() {
        OriginTrackedValue value = getFromFirst("test-colon-separator");
        assertEquals("my-property", getValue(value));
        assertEquals("31:23", getLocation(value));
    }
    
    @Test
    void getPropertyWithSeparatorSurroundedBySpaces() {
        OriginTrackedValue value = getFromFirst("blah");
        assertEquals("hello world", getValue(value));
        assertEquals("18:12", getLocation(value));
    }
    
    @Test
    void getUnicodeProperty() {
        OriginTrackedValue value = getFromFirst("test-unicode");
        assertEquals("properties人&test", getValue(value));
        assertEquals("28:14", getLocation(value));
    }
    
    @Test
    void getMalformedUnicodeProperty() {
        ClassPathResource resource = new ClassPathResource("test-properties-malformed-unicode.properties");
        assertThrows(IllegalStateException.class, () -> new OriginTrackedPropertiesLoader(resource).load());
    }
    
    @Test
    void getEscapedProperty() {
        OriginTrackedValue value = getFromFirst("test=property");
        assertEquals("helloworld", getValue(value));
        assertEquals("30:15", getLocation(value));
    }
    
    @Test
    void getPropertyWithTab() {
        OriginTrackedValue value = getFromFirst("test-tab-property");
        assertEquals("foo\tbar", getValue(value));
        assertEquals("32:19", getLocation(value));
    }
    
    @Test
    void getPropertyWithBang() {
        OriginTrackedValue value = getFromFirst("test-bang-property");
        assertEquals("foo!", getValue(value));
        assertEquals("50:20", getLocation(value));
    }
    
    @Test
    void getPropertyWithValueComment() {
        OriginTrackedValue value = getFromFirst("test-property-value-comment");
        assertEquals("foo !bar #foo", getValue(value));
        assertEquals("52:29", getLocation(value));
    }
    
    @Test
    void getPropertyWithMultilineImmediateBang() {
        OriginTrackedValue value = getFromFirst("test-multiline-immediate-bang");
        assertEquals("!foo", getValue(value));
        assertEquals("55:1", getLocation(value));
    }
    
    @Test
    void getPropertyWithCarriageReturn() {
        OriginTrackedValue value = getFromFirst("test-return-property");
        assertEquals("foo\rbar", getValue(value));
        assertEquals("33:22", getLocation(value));
    }
    
    @Test
    void getPropertyWithNewLine() {
        OriginTrackedValue value = getFromFirst("test-newline-property");
        assertEquals("foo\nbar", getValue(value));
        assertEquals("34:23", getLocation(value));
    }
    
    @Test
    void getPropertyWithFormFeed() {
        OriginTrackedValue value = getFromFirst("test-form-feed-property");
        assertEquals("foo\fbar", getValue(value));
        assertEquals("35:25", getLocation(value));
    }
    
    @Test
    void getPropertyWithWhiteSpace() {
        OriginTrackedValue value = getFromFirst("test-whitespace-property");
        assertEquals("foo   bar", getValue(value));
        assertEquals("36:32", getLocation(value));
    }
    
    @Test
    void getCommentedOutPropertyShouldBeNull() {
        assertNull(getFromFirst("commented-property"));
        assertNull(getFromFirst("#commented-property"));
        assertNull(getFromFirst("commented-two"));
        assertNull(getFromFirst("!commented-two"));
    }
    
    @Test
    void getMultiline() {
        OriginTrackedValue value = getFromFirst("test-multiline");
        assertEquals("ab\\c", getValue(value));
        assertEquals("37:17", getLocation(value));
    }
    
    @Test
    void getImmediateMultiline() {
        OriginTrackedValue value = getFromFirst("test-multiline-immediate");
        assertEquals("foo", getValue(value));
        assertEquals("48:1", getLocation(value));
    }
    
    @Test
    void loadWhenMultiDocumentWithPoundPrefixAndWithoutWhitespaceLoadsMultiDoc() throws IOException {
        String content = "a=a\n#---\nb=b";
        Map<String, OriginTrackedValue> loaded = new OriginTrackedPropertiesLoader(
                new ByteArrayResource(content.getBytes())).load();
        assertEquals(2, loaded.size());
    }
    
    @Test
    void loadWhenMultiDocumentWithExclamationPrefixAndWithoutWhitespaceLoadsMultiDoc() throws IOException {
        String content = "a=a\n!---\nb=b";
        Map<String, OriginTrackedValue> loaded = new OriginTrackedPropertiesLoader(
                new ByteArrayResource(content.getBytes())).load();
        assertEquals(2, loaded.size());
    }
    
    @Test
    void loadWhenMultiDocumentWithPoundPrefixAndLeadingWhitespaceLoadsSingleDoc() throws IOException {
        String content = "a=a\n \t#---\nb=b";
        Map<String, OriginTrackedValue> loaded = new OriginTrackedPropertiesLoader(
                new ByteArrayResource(content.getBytes())).load();
        assertEquals(2, loaded.size());
    }
    
    @Test
    void loadWhenMultiDocumentWithExclamationPrefixAndLeadingWhitespaceLoadsSingleDoc() throws IOException {
        String content = "a=a\n \t!---\nb=b";
        Map<String, OriginTrackedValue> loaded = new OriginTrackedPropertiesLoader(
                new ByteArrayResource(content.getBytes())).load();
        assertEquals(2, loaded.size());
    }
    
    @Test
    void loadWhenMultiDocumentWithPoundPrefixAndTrailingWhitespaceLoadsMultiDoc() throws IOException {
        String content = "a=a\n#--- \t \nb=b";
        Map<String, OriginTrackedValue> loaded = new OriginTrackedPropertiesLoader(
                new ByteArrayResource(content.getBytes())).load();
        assertEquals(2, loaded.size());
    }
    
    @Test
    void loadWhenMultiDocumentWithExclamationPrefixAndTrailingWhitespaceLoadsMultiDoc() throws IOException {
        String content = "a=a\n!--- \t \nb=b";
        Map<String, OriginTrackedValue> loaded = new OriginTrackedPropertiesLoader(
                new ByteArrayResource(content.getBytes())).load();
        assertEquals(2, loaded.size());
    }
    
    @Test
    void loadWhenMultiDocumentWithPoundPrefixAndTrailingCharsLoadsSingleDoc() throws IOException {
        String content = "a=a\n#--- \tcomment\nb=b";
        Map<String, OriginTrackedValue> loaded = new OriginTrackedPropertiesLoader(
                new ByteArrayResource(content.getBytes())).load();
        assertEquals(2, loaded.size());
    }
    
    @Test
    void loadWhenMultiDocumentWithExclamationPrefixAndTrailingCharsLoadsSingleDoc() throws IOException {
        String content = "a=a\n!--- \tcomment\nb=b";
        Map<String, OriginTrackedValue> loaded = new OriginTrackedPropertiesLoader(
                new ByteArrayResource(content.getBytes())).load();
        assertEquals(2, loaded.size());
    }
    
    @Test
    void loadWhenMultiDocumentSeparatorPrefixDifferentFromCommentPrefixLoadsMultiDoc() throws IOException {
        String[] contents = new String[] {"a=a\n# comment\n!---\nb=b", "a=a\n! comment\n#---\nb=b"};
        for (String content : contents) {
            Map<String, OriginTrackedValue> loaded = new OriginTrackedPropertiesLoader(
                    new ByteArrayResource(content.getBytes())).load();
            assertEquals(2, loaded.size());
        }
    }
    
    @Test
    void getPropertyWithWhitespaceAfterKey() {
        OriginTrackedValue value = getFromFirst("bar");
        assertEquals("foo=baz", getValue(value));
        assertEquals("19:7", getLocation(value));
    }
    
    @Test
    void getPropertyWithSpaceSeparator() {
        OriginTrackedValue value = getFromFirst("hello");
        assertEquals("world", getValue(value));
        assertEquals("20:9", getLocation(value));
    }
    
    @Test
    void getPropertyWithBackslashEscaped() {
        OriginTrackedValue value = getFromFirst("proper\\ty");
        assertEquals("test", getValue(value));
        assertEquals("21:11", getLocation(value));
    }
    
    @Test
    void getPropertyWithEmptyValue() {
        OriginTrackedValue value = getFromFirst("foo");
        assertEquals("", getValue(value));
        assertEquals("23:0", getLocation(value));
    }
    
    @Test
    void getPropertyWithBackslashEscapedInValue() {
        OriginTrackedValue value = getFromFirst("bat");
        assertEquals("a\\", getValue(value));
        assertEquals("23:7", getLocation(value));
    }
    
    @Test
    void getPropertyWithSeparatorInValue() {
        OriginTrackedValue value = getFromFirst("bling");
        assertEquals("a=b", getValue(value));
        assertEquals("24:9", getLocation(value));
    }
    
    @Test
    void getListProperty() {
        OriginTrackedValue apple = getFromFirst("foods[0]");
        assertEquals("Apple", getValue(apple));
        assertEquals("40:9", getLocation(apple));
        OriginTrackedValue orange = getFromFirst("foods[1]");
        assertEquals("Orange", getValue(orange));
        assertEquals("41:1", getLocation(orange));
        OriginTrackedValue strawberry = getFromFirst("foods[2]");
        assertEquals("Strawberry", getValue(strawberry));
        assertEquals("42:1", getLocation(strawberry));
        OriginTrackedValue mango = getFromFirst("foods[3]");
        assertEquals("Mango", getValue(mango));
        assertEquals("43:1", getLocation(mango));
    }
    
    @Test
    @SuppressWarnings({"checkstyle:AbbreviationAsWordInName"})
    void getPropertyWithISO88591Character() {
        OriginTrackedValue value = getFromFirst("test-iso8859-1-chars");
        assertEquals("æ×ÈÅÞßáñÀÿ", getValue(value));
    }
    
    @Test
    void getPropertyWithTrailingSpace() {
        OriginTrackedValue value = getFromFirst("test-with-trailing-space");
        assertEquals("trailing ", getValue(value));
    }
    
    @Test
    void getPropertyWithEscapedTrailingSpace() {
        OriginTrackedValue value = getFromFirst("test-with-escaped-trailing-space");
        assertEquals("trailing ", getValue(value));
    }
    
    @Test
    void existingCommentsAreNotTreatedAsMultiDoc() throws Exception {
        this.resource = new ClassPathResource("existing-non-multi-document.properties");
        this.documents = new OriginTrackedPropertiesLoader(this.resource).load();
        assertEquals(6, this.documents.size());
    }
    
    @Test
    void getPropertyAfterPoundCharacter() {
        OriginTrackedValue value = getFromFirst("test-line-after-empty-pound");
        assertEquals("abc ", getValue(value));
    }
    
    private OriginTrackedValue getFromFirst(String key) {
        return this.documents.get(key);
    }
    
    private Object getValue(OriginTrackedValue value) {
        return (value != null) ? value.getValue() : null;
    }
    
    private String getLocation(OriginTrackedValue value) {
        if (value == null) {
            return null;
        }
        return ((TextResourceOrigin) value.getOrigin()).getLocation().toString();
    }
    
}