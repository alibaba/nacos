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

import { createElement, useState } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { CopilotConfigView } from '@/lib/copilot-config';
import SettingCenterPage from '../index';

vi.mock('react', async (importOriginal) => {
  const react = await importOriginal<typeof import('react')>();
  return { ...react, useState: vi.fn(react.useState) };
});
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));
vi.mock('react-router-dom', () => ({ useNavigate: () => vi.fn() }));
vi.mock('@/api/client', () => ({ default: { get: vi.fn(), post: vi.fn() } }));
vi.mock('@/stores/server-store', () => ({
  useServerStore: (selector: (state: object) => unknown) => selector({
    copilotEnabled: true,
    stateLoaded: true,
  }),
}));

afterEach(() => vi.clearAllMocks());

function renderLoadedSettings(config: CopilotConfigView): string {
  // SSR cannot run loadConfig's effect; seed only the page's loaded form state.
  const formState = [false, false, false, config, '', config.apiKey || '', config.model || 'qwen-turbo'];
  for (const value of formState) {
    vi.mocked(useState).mockImplementationOnce(() => [value, vi.fn()]);
  }
  return renderToStaticMarkup(createElement(SettingCenterPage));
}

describe('deprecated legacy editor', () => {
  it.each([
    { name: 'missing runtime table', connections: undefined },
    { name: 'empty runtime table', connections: [] },
    {
      name: 'deployment-only table',
      connections: [{ name: 'prod-qwen', source: 'DEPLOYMENT' as const }],
    },
    {
      name: 'deployment connection replacing legacy',
      connections: [{ name: 'legacy', source: 'DEPLOYMENT' as const }],
    },
  ])('keeps stored fields editable with $name', ({ connections }) => {
    const html = renderLoadedSettings({
      connections,
      apiKey: 'st******ey',
      model: 'qwen-max',
    });

    expect(html).toContain('settings.legacySection');
    expect(html).toContain('settings.deprecated');
    expect(html).toContain('settings.apiKey');
    expect(html).toContain('type="password"');
    expect(html).toContain('value="st******ey"');
    expect(html).toContain('settings.model');
    expect(html).not.toContain('disabled=""');
  });

  it('retains automatic selection and disables unusable runtime connections', () => {
    const html = renderLoadedSettings({
      connections: [{ name: 'legacy', source: 'LEGACY', enabled: true, available: false }],
    });

    expect(html).toContain('settings.connectionAutomatic');
    expect(html).toContain('id="copilot-connection-automatic"');
    expect(html).toContain('id="copilot-connection-legacy"');
    expect(html).toContain('disabled=""');
    expect(html).toContain('settings.legacySection');
  });
});
