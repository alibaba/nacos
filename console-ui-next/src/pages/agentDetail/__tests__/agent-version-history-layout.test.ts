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

import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

const SOURCE = fs.readFileSync(path.resolve(import.meta.dirname, '../index.tsx'), 'utf8');

describe('Agent version-history layout', () => {
  it('keeps draft creation in the hero action row instead of the top toolbar', () => {
    const topToolbar = SOURCE.slice(
      SOURCE.indexOf('<div className="mb-4 flex flex-col'),
      SOURCE.indexOf('<div className="flex items-start gap-4">'),
    );

    expect(topToolbar).not.toContain("t('agent.createDraft')");
    expect(SOURCE).toContain('!currentVersion && currentOverview.versionPage.totalCount === 0');
    expect(SOURCE).toContain("onClick={() => editPath('draft-create')}");
  });

  it('opens version history from the toolbar and selects versions from a side sheet', () => {
    expect(SOURCE).toContain('onClick={() => setVersionSheetOpen(true)}');
    expect(SOURCE).toContain('<Sheet open={versionSheetOpen} onOpenChange={setVersionSheetOpen}>');
    expect(SOURCE).toContain('setSelectedVersion(version.version);');
    expect(SOURCE).toContain('setVersionSheetOpen(false);');
  });

  it('shares the lifecycle version-option presentation used by other AI resources', () => {
    expect(SOURCE).toContain("import { AiVersionSelectOption }");
    expect(SOURCE).toContain('<AiVersionSelectOption');
  });
});
