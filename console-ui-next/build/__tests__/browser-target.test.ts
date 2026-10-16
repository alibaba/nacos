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

describe('browser compatibility target', () => {
  it('transforms syntax unsupported by Chromium 90', async () => {
    const config = viteConfig({ command: 'build', mode: 'production' });
    const fixture = 'class BrowserCompatFixture { static { this.value = 1; } }';

    const result = await build({
      configFile: false,
      logLevel: 'silent',
      plugins: [{
        name: 'browser-compat-fixture',
        resolveId(id) {
          return id === 'browser-compat-fixture' ? '\0browser-compat-fixture' : null;
        },
        load(id) {
          return id === '\0browser-compat-fixture' ? fixture : null;
        },
      }],
      build: {
        write: false,
        minify: false,
        target: config.build?.target,
        rollupOptions: { input: 'browser-compat-fixture' },
      },
    });

    const bundles = Array.isArray(result) ? result : [result];
    const code = bundles
      .flatMap((bundle) => bundle.output)
      .filter((output) => output.type === 'chunk')
      .map((output) => output.code)
      .join('\n');

    expect(code).not.toMatch(/\bstatic\s*\{/);
  });
});
