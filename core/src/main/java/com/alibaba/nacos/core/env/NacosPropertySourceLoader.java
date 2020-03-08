/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.env;

import com.alibaba.nacos.core.file.FileChangeEvent;
import com.alibaba.nacos.core.file.FileWatcher;
import com.alibaba.nacos.core.file.WatchFileCenter;
import com.alibaba.nacos.core.notify.NotifyCenter;
import com.alibaba.nacos.core.utils.SystemUtils;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.boot.origin.TextResourceOrigin;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

/**
 * Listen for changes in the application.conf file
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("PMD.UndefineMagicConstantRule")
public class NacosPropertySourceLoader implements PropertySourceLoader {

    private final Map<String, Object> properties = new ConcurrentHashMap<>(16);

    private Resource holder = null;

    @Override
    public String[] getFileExtensions() {
        return new String[]{"nconf"};
    }

    @Override
    public List<PropertySource<?>> load(String name, Resource resource) throws IOException {
        holder = resource;
        Map<String, ?> tmp = loadProperties(resource);
        properties.putAll(tmp);

        NotifyCenter.registerPublisher(RefreshEvent::new, RefreshEvent.class);

        WatchFileCenter.registerWatcher(SystemUtils.getConfFilePath(), new FileWatcher() {
            @Override
            public void onChange(FileChangeEvent event) {
                try {
                    Map<String, ?> tmp1 = loadProperties(holder);
                    properties.putAll(tmp1);
                    NotifyCenter.publishEvent(RefreshEvent.class, new RefreshEvent());
                } catch (IOException ignore) {

                }
            }

            @Override
            public boolean interest(String context) {
                return StringUtils.contains(context, "application.nconf");
            }
        });

        if (properties.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections
                .singletonList(new OriginTrackedMapPropertySource(name, properties));
    }

    private Map<String, ?> loadProperties(Resource resource) throws IOException {
        return new OriginTrackedPropertiesLoader(resource).load();
    }

    static class OriginTrackedPropertiesLoader {

        private final Resource resource;

        /**
         * Create a new {@link OriginTrackedPropertiesLoader} instance.
         *
         * @param resource the resource of the {@code .properties} data
         */
        OriginTrackedPropertiesLoader(Resource resource) {
            Assert.notNull(resource, "Resource must not be null");
            this.resource = resource;
        }

        /**
         * Load {@code .properties} data and return a map of {@code String} ->
         * {@link OriginTrackedValue}.
         *
         * @return the loaded properties
         * @throws IOException on read error
         */
        public Map<String, OriginTrackedValue> load() throws IOException {
            return load(true);
        }

        /**
         * Load {@code .properties} data and return a map of {@code String} ->
         * {@link OriginTrackedValue}.
         *
         * @param expandLists if list {@code name[]=a,b,c} shortcuts should be expanded
         * @return the loaded properties
         * @throws IOException on read error
         */
        public Map<String, OriginTrackedValue> load(boolean expandLists) throws IOException {
            try (OriginTrackedPropertiesLoader.CharacterReader reader = new OriginTrackedPropertiesLoader.CharacterReader(this.resource)) {
                Map<String, OriginTrackedValue> result = new LinkedHashMap<>();
                StringBuilder buffer = new StringBuilder();
                while (reader.read()) {
                    String key = loadKey(buffer, reader).trim();
                    if (expandLists && key.endsWith("[]")) {
                        key = key.substring(0, key.length() - 2);
                        int index = 0;
                        do {
                            OriginTrackedValue value = loadValue(buffer, reader, true);
                            put(result, key + "[" + (index++) + "]", value);
                            if (!reader.isEndOfLine()) {
                                reader.read();
                            }
                        }
                        while (!reader.isEndOfLine());
                    } else {
                        OriginTrackedValue value = loadValue(buffer, reader, false);
                        put(result, key, value);
                    }
                }
                return result;
            }
        }

        private void put(Map<String, OriginTrackedValue> result, String key,
                         OriginTrackedValue value) {
            if (!key.isEmpty()) {
                result.put(key, value);
            }
        }

        private String loadKey(StringBuilder buffer, OriginTrackedPropertiesLoader.CharacterReader reader)
                throws IOException {
            buffer.setLength(0);
            boolean previousWhitespace = false;
            while (!reader.isEndOfLine()) {
                if (reader.isPropertyDelimiter()) {
                    reader.read();
                    return buffer.toString();
                }
                if (!reader.isWhiteSpace() && previousWhitespace) {
                    return buffer.toString();
                }
                previousWhitespace = reader.isWhiteSpace();
                buffer.append(reader.getCharacter());
                reader.read();
            }
            return buffer.toString();
        }

        private OriginTrackedValue loadValue(StringBuilder buffer, OriginTrackedPropertiesLoader.CharacterReader reader,
                                             boolean splitLists) throws IOException {
            buffer.setLength(0);
            while (reader.isWhiteSpace() && !reader.isEndOfLine()) {
                reader.read();
            }
            TextResourceOrigin.Location location = reader.getLocation();
            while (!reader.isEndOfLine() && !(splitLists && reader.isListDelimiter())) {
                buffer.append(reader.getCharacter());
                reader.read();
            }
            Origin origin = new TextResourceOrigin(this.resource, location);
            return OriginTrackedValue.of(buffer.toString(), origin);
        }

        /**
         * Reads characters from the source resource, taking care of skipping comments,
         * handling multi-line values and tracking {@code '\'} escapes.
         */
        private class CharacterReader implements Closeable {

            private final String[] ESCAPES = {"trnf", "\t\r\n\f"};

            private final LineNumberReader reader;

            private int columnNumber = -1;

            private boolean escaped;

            private int character;

            CharacterReader(Resource resource) throws IOException {
                this.reader = new LineNumberReader(new InputStreamReader(
                        resource.getInputStream(), StandardCharsets.ISO_8859_1));
            }

            @Override
            public void close() throws IOException {
                this.reader.close();
            }

            public boolean read() throws IOException {
                return read(false);
            }

            public boolean read(boolean wrappedLine) throws IOException {
                this.escaped = false;
                this.character = this.reader.read();
                this.columnNumber++;
                if (this.columnNumber == 0) {
                    skipLeadingWhitespace();
                    if (!wrappedLine) {
                        skipComment();
                    }
                }
                if (this.character == '\\') {
                    this.escaped = true;
                    readEscaped();
                } else if (this.character == '\n') {
                    this.columnNumber = -1;
                }
                return !isEndOfFile();
            }

            private void skipLeadingWhitespace() throws IOException {
                while (isWhiteSpace()) {
                    this.character = this.reader.read();
                    this.columnNumber++;
                }
            }

            private void skipComment() throws IOException {
                if (this.character == '#' || this.character == '!') {
                    while (this.character != '\n' && this.character != -1) {
                        this.character = this.reader.read();
                    }
                    this.columnNumber = -1;
                    read();
                }
            }

            private void readEscaped() throws IOException {
                this.character = this.reader.read();
                int escapeIndex = ESCAPES[0].indexOf(this.character);
                if (escapeIndex != -1) {
                    this.character = ESCAPES[1].charAt(escapeIndex);
                } else if (this.character == '\n') {
                    this.columnNumber = -1;
                    read(true);
                } else if (this.character == 'u') {
                    readUnicode();
                }
            }

            private void readUnicode() throws IOException {
                this.character = 0;
                for (int i = 0; i < 4; i++) {
                    int digit = this.reader.read();
                    if (digit >= '0' && digit <= '9') {
                        this.character = (this.character << 4) + digit - '0';
                    } else if (digit >= 'a' && digit <= 'f') {
                        this.character = (this.character << 4) + digit - 'a' + 10;
                    } else if (digit >= 'A' && digit <= 'F') {
                        this.character = (this.character << 4) + digit - 'A' + 10;
                    } else {
                        throw new IllegalStateException("Malformed \\uxxxx encoding.");
                    }
                }
            }

            public boolean isWhiteSpace() {
                return !this.escaped && (this.character == ' ' || this.character == '\t'
                        || this.character == '\f');
            }

            public boolean isEndOfFile() {
                return this.character == -1;
            }

            public boolean isEndOfLine() {
                return this.character == -1 || (!this.escaped && this.character == '\n');
            }

            public boolean isListDelimiter() {
                return !this.escaped && this.character == ',';
            }

            public boolean isPropertyDelimiter() {
                return !this.escaped && (this.character == '=' || this.character == ':');
            }

            public char getCharacter() {
                return (char) this.character;
            }

            public TextResourceOrigin.Location getLocation() {
                return new TextResourceOrigin.Location(this.reader.getLineNumber(), this.columnNumber);
            }

        }

    }
}
