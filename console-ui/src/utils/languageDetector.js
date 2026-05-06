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

/**
 * Get Monaco Editor language based on file extension
 * @param {string} fileName - The file name or path
 * @returns {string} - Monaco Editor language identifier
 */
export function getLanguageFromFileName(fileName) {
  if (!fileName || typeof fileName !== 'string') {
    return 'plaintext';
  }

  // Extract file extension
  const lastDotIndex = fileName.lastIndexOf('.');
  if (lastDotIndex === -1 || lastDotIndex === fileName.length - 1) {
    return 'plaintext';
  }

  const extension = fileName.substring(lastDotIndex + 1).toLowerCase();

  // Map file extensions to Monaco Editor languages
  const languageMap = {
    // Scripting languages
    sh: 'shell',
    bash: 'shell',
    zsh: 'shell',
    fish: 'shell',
    py: 'python',
    python: 'python',
    rb: 'ruby',
    ruby: 'ruby',
    pl: 'perl',
    perl: 'perl',
    php: 'php',
    lua: 'lua',
    r: 'r',
    ps1: 'powershell',
    ps: 'powershell',
    bat: 'bat',
    cmd: 'bat',

    // Web technologies
    js: 'javascript',
    javascript: 'javascript',
    jsx: 'javascript',
    ts: 'typescript',
    typescript: 'typescript',
    tsx: 'typescript',
    html: 'html',
    htm: 'html',
    css: 'css',
    scss: 'scss',
    sass: 'sass',
    less: 'less',
    vue: 'vue',
    svelte: 'svelte',

    // Data formats
    json: 'json',
    yaml: 'yaml',
    yml: 'yaml',
    xml: 'xml',
    toml: 'toml',
    ini: 'ini',
    properties: 'properties',
    conf: 'properties',
    config: 'properties',

    // Compiled languages
    java: 'java',
    c: 'c',
    cpp: 'cpp',
    cc: 'cpp',
    cxx: 'cpp',
    h: 'c',
    hpp: 'cpp',
    hxx: 'cpp',
    cs: 'csharp',
    csharp: 'csharp',
    go: 'go',
    rs: 'rust',
    rust: 'rust',
    swift: 'swift',
    kt: 'kotlin',
    kotlin: 'kotlin',
    scala: 'scala',
    clj: 'clojure',
    clojure: 'clojure',

    // Database
    sql: 'sql',
    plsql: 'sql',

    // Configuration and markup
    md: 'markdown',
    markdown: 'markdown',
    txt: 'plaintext',
    log: 'plaintext',

    // Other
    dockerfile: 'dockerfile',
    makefile: 'makefile',
    mk: 'makefile',
  };

  return languageMap[extension] || 'plaintext';
}

/**
 * Get language display name for UI
 * @param {string} fileName - The file name or path
 * @returns {string} - Display name of the language
 */
export function getLanguageDisplayName(fileName) {
  const language = getLanguageFromFileName(fileName);
  const displayNames = {
    shell: 'Bash',
    python: 'Python',
    javascript: 'JavaScript',
    typescript: 'TypeScript',
    java: 'Java',
    json: 'JSON',
    yaml: 'YAML',
    xml: 'XML',
    markdown: 'Markdown',
    plaintext: 'Plain Text',
  };

  return displayNames[language] || language;
}
