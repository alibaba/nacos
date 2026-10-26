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
import path from 'node:path';
import vm from 'node:vm';
import { describe, expect, it } from 'vitest';
import viteConfig from '../../vite.config';

describe('browser compatibility target', () => {
  it('keeps a multi-browser build target so Safari CSS prefixes survive', () => {
    const config = viteConfig({ command: 'build', mode: 'production' });
    expect(config.build?.target).toEqual([
      'chrome90',
      'edge111',
      'firefox114',
      'safari16.4',
      'ios16.4',
    ]);
    expect(config.build?.cssTarget).toEqual([
      'chrome90',
      'edge111',
      'firefox114',
      'safari16.4',
      'ios16.4',
    ]);
  });

  it('transforms syntax unsupported by Chromium 90', async () => {
    const config = viteConfig({ command: 'build', mode: 'production' });
    const fixture = 'class BrowserCompatFixture { static { this.value = 1; } }';

    const result = await build({
      configFile: false,
      logLevel: 'silent',
      plugins: [
        {
          name: 'browser-compat-fixture',
          resolveId(id) {
            return id === 'browser-compat-fixture' ? '\0browser-compat-fixture' : null;
          },
          load(id) {
            return id === '\0browser-compat-fixture' ? fixture : null;
          },
        },
      ],
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

  it('emits an Object.hasOwn polyfill banner for Chromium 90 runtimes', () => {
    const config = viteConfig({ command: 'build', mode: 'production' });
    const banner = config.build?.rollupOptions?.output;
    const output = Array.isArray(banner) ? banner[0] : banner;
    expect(String(output?.banner ?? '')).toMatch(/Object\.hasOwn/);
    expect(String(output?.banner ?? '')).toMatch(/hasOwnProperty/);
  });

  it('runs a built worker when Object.hasOwn is unavailable', async () => {
    const config = viteConfig({ command: 'build', mode: 'production' });
    const result = await build({
      configFile: false,
      logLevel: 'silent',
      worker: config.worker,
      build: {
        write: false,
        minify: false,
        target: config.build?.target,
        rolldownOptions: {
          input: path.resolve(__dirname, 'fixtures/worker-host.ts'),
        },
      },
    });

    const bundles = Array.isArray(result) ? result : [result];
    const worker = bundles
      .flatMap((bundle) => bundle.output)
      .find((output) => output.type === 'asset' && output.fileName.includes('worker-runtime'));
    expect(worker?.type).toBe('asset');
    if (worker?.type !== 'asset') return;

    const messages: unknown[] = [];
    const context = { self: { postMessage: (message: unknown) => messages.push(message) } };
    vm.runInNewContext(`Object.hasOwn = undefined;\n${String(worker.source)}`, context, {
      timeout: 1000,
    });
    expect(messages).toEqual([true]);
  });

  it('lists Safari in cssTarget so prefix emission is not chrome-only', () => {
    const config = viteConfig({ command: 'build', mode: 'production' });
    const cssTarget = config.build?.cssTarget;
    expect(Array.isArray(cssTarget) ? cssTarget : [cssTarget]).toEqual(
      expect.arrayContaining(['safari16.4', 'ios16.4']),
    );
  });
});
