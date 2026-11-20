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

// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { skillApi } from '@/api/skill';
import { useSkillStore } from '@/stores/skill-store';
import { TooltipProvider } from '@/components/ui/tooltip';
import type { SkillAdminDetail } from '@/types/skill';
import SkillDetailPage from '..';

vi.mock('@/api/skill', () => ({ skillApi: { getDetail: vi.fn(), getVersion: vi.fn() } }));
vi.mock('react-i18next', async importOriginal => ({
  ...await importOriginal<typeof import('react-i18next')>(),
  useTranslation: () => ({ t: (key: string) => key }),
}));
vi.mock('@uiw/react-md-editor', () => ({ default: { Markdown: () => null } }));
vi.mock('../SkillResourcePanel', () => ({ SkillResourcePanel: () => null }));
vi.mock('../SkillVersionDiffPanel', () => ({ SkillVersionDiffPanel: () => null }));
vi.mock('@/components/ai/skill/SkillOptimizeDialog', () => ({ SkillOptimizeDialog: () => null }));

function detail(version: string, draft = false): SkillAdminDetail {
  return {
    owner: 'nacos', enable: true, scope: 'PUBLIC', bizTags: '[]', from: 'local',
    editingVersion: draft ? version : null, reviewingVersion: null,
    labels: draft ? {} : { latest: version }, onlineCnt: draft ? 0 : 1,
    updateTime: 0, downloadCount: 0, writable: false,
    versions: [{ version, status: draft ? 'draft' : 'online', author: 'nacos',
      commitMsg: '', createTime: 0, updateTime: 0, publishPipelineInfo: null, downloadCount: 0 }],
  };
}

let root: Root;
let container: HTMLDivElement;

beforeEach(() => {
  vi.clearAllMocks();
  Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });
  useSkillStore.getState().clearDetail();
  useSkillStore.getState().clearError();
  vi.mocked(skillApi.getDetail).mockImplementation(async ({ skillName }) => ({
    data: detail(skillName === 'source' ? '1.0.0' : '0.0.1', skillName !== 'source'),
  }) as Awaited<ReturnType<typeof skillApi.getDetail>>);
  vi.mocked(skillApi.getVersion).mockImplementation(async ({ namespaceId, skillName }) => ({
    data: { namespaceId, name: skillName, description: '', skillMd: '', resource: {} },
  }) as Awaited<ReturnType<typeof skillApi.getVersion>>);
  container = document.createElement('div');
  document.body.append(container);
  root = createRoot(container);
});

afterEach(async () => {
  await act(async () => root.unmount());
  container.remove();
});

async function openSource() {
  const router = createMemoryRouter([{ path: '/skill/:name', element: <SkillDetailPage /> }], {
    initialEntries: ['/skill/source?namespaceId=team-a&version=1.0.0'],
  });
  await act(async () => root.render(<TooltipProvider><RouterProvider router={router} /></TooltipProvider>));
  expect(skillApi.getVersion).toHaveBeenCalledWith({
    namespaceId: 'team-a', skillName: 'source', version: '1.0.0',
  });
  vi.mocked(skillApi.getVersion).mockClear();
  return router;
}

describe('Skill detail navigation after copy', () => {
  it.each([
    ['public', 'copy'], // Cross-namespace copy with a new name.
    ['team-a', 'copy'], // Copy within the same namespace.
    ['public', 'source'], // Same resource name in another namespace.
  ])('only loads the target draft for %s/%s', async (namespaceId, name) => {
    const router = await openSource();
    vi.mocked(skillApi.getDetail).mockResolvedValue({ data: detail('0.0.1', true) } as
      Awaited<ReturnType<typeof skillApi.getDetail>>);
    await act(async () => { await router.navigate(`/skill/${name}?namespaceId=${namespaceId}&version=0.0.1`); });
    expect(vi.mocked(skillApi.getVersion).mock.calls).toEqual([[{ namespaceId, skillName: name, version: '0.0.1' }]]);
    expect(container.textContent).not.toContain('skill.versionNotFound');
  });

  it('follows version pins and browser history without requesting the old version', async () => {
    const router = await openSource();
    await act(async () => { await router.navigate('/skill/copy?namespaceId=public&version=0.0.1'); });
    vi.mocked(skillApi.getVersion).mockClear();
    await act(async () => { await router.navigate(-1); });
    expect(vi.mocked(skillApi.getVersion).mock.calls).toEqual([[{
      namespaceId: 'team-a', skillName: 'source', version: '1.0.0',
    }]]);
  });

  it('does not fall back to latest when an explicitly pinned version is missing', async () => {
    const router = await openSource();
    await act(async () => { await router.navigate('/skill/source?namespaceId=team-a&version=9.9.9'); });
    expect(skillApi.getVersion).not.toHaveBeenCalled();
  });

  it('waits for the target metadata before loading any target version', async () => {
    const router = await openSource();
    let resolve!: (value: Awaited<ReturnType<typeof skillApi.getDetail>>) => void;
    vi.mocked(skillApi.getDetail).mockReturnValueOnce(new Promise(res => { resolve = res; }));
    await act(async () => { await router.navigate('/skill/copy?namespaceId=public&version=0.0.1'); });
    expect(skillApi.getVersion).not.toHaveBeenCalled();
    await act(async () => {
      resolve({ data: detail('0.0.1', true) } as Awaited<ReturnType<typeof skillApi.getDetail>>);
    });
    expect(vi.mocked(skillApi.getVersion).mock.calls).toEqual([[{
      namespaceId: 'public', skillName: 'copy', version: '0.0.1',
    }]]);
  });
});
