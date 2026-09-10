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
import { build } from 'vite';
import { describe, expect, it } from 'vitest';
import viteConfig from '../../vite.config';

describe('Monaco sanitizer bundle', () => {
  it('bundles the patched DOMPurify package instead of Monaco\'s embedded copy', async () => {
    const moduleIds: string[] = [];
    const config = viteConfig({ command: 'build', mode: 'production' });

    await build({
      configFile: false,
      resolve: config.resolve,
      logLevel: 'silent',
      plugins: [{
        name: 'capture-sanitizer-modules',
        generateBundle() {
          moduleIds.push(...this.getModuleIds());
        },
      }],
      build: {
        write: false,
        minify: false,
        rollupOptions: {
          input: 'monaco-editor/esm/vs/base/browser/domSanitize.js',
          preserveEntrySignatures: 'strict',
        },
      },
    });

    const normalizedIds = moduleIds.map((id) => id.replaceAll('\\', '/'));
    expect(normalizedIds.some((id) =>
      id.endsWith('/monaco-editor/esm/vs/base/browser/domSanitize.js'),
    )).toBe(true);
    expect(normalizedIds.some((id) =>
      id.endsWith('/node_modules/dompurify/dist/purify.es.mjs'),
    )).toBe(true);
    expect(normalizedIds.some((id) =>
      id.includes('/monaco-editor/esm/vs/base/browser/dompurify/'),
    )).toBe(false);
  });
});
