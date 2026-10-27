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

import { describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { SkillManagementTabs } from '../SkillManagementTabs';

const server = vi.hoisted(() => ({ hubEnabled: true }));
vi.mock('@/stores/server-store', () => ({
  useServerStore: (select: (value: typeof server) => unknown) => select(server),
}));
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }));

function render(shared = false) {
  return renderToStaticMarkup(<MemoryRouter initialEntries={['/skill?namespace=team-a']}>
    <SkillManagementTabs shared={shared} />
  </MemoryRouter>);
}

describe('Skill current namespace and shared navigation', () => {
  it('shows both scopes and preserves the selected namespace when switching', () => {
    server.hubEnabled = true;
    const html = render();
    expect(html).toContain('hub.currentNamespace');
    expect(html).toContain('hub.title');
    expect(html).toContain('href="/skill?namespace=team-a"');
    expect(html).toContain('href="/skill/shared/list?namespace=team-a"');
    expect(html).toMatch(/aria-current="page"[^>]*href="\/skill\?namespace=team-a"/);
  });

  it('marks shared discovery as selected independently of the namespace', () => {
    server.hubEnabled = true;
    expect(render(true)).toMatch(/aria-current="page"[^>]*href="\/skill\/shared\/list\?namespace=team-a"/);
  });

  it('hides the shared entry when the server disables sharing', () => {
    server.hubEnabled = false;
    expect(render()).toBe('');
  });
});
