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

import { describe, expect, it } from 'vitest';
import { canCopyResource, canShareResource } from '../resource-sharing';

describe('Skill sharing and direct copying eligibility', () => {
  it('shares only an enabled, public, explicit online version', () => {
    expect(canShareResource('skill', '1.0.0', 'online', 'PUBLIC')).toBe(true);
    for (const status of ['draft', 'reviewing', 'reviewed', 'offline', undefined]) {
      expect(canShareResource('skill', '1.0.0', status, 'PUBLIC')).toBe(false);
    }
    expect(canShareResource('skill', '1.0.0', 'online', 'PRIVATE')).toBe(false);
    expect(canShareResource('skill', '1.0.0', 'online', 'PUBLIC', false)).toBe(false);
    expect(canShareResource('skill', '', 'online', 'PUBLIC')).toBe(false);
  });
  it('allows a readable private Skill to be copied without making it public', () => {
    expect(canCopyResource('skill', '1.0.0', 'online')).toBe(true);
    expect(canShareResource('skill', '1.0.0', 'online', 'PRIVATE')).toBe(false);
  });
  it('requires an enabled published source for direct copy', () => {
    for (const status of ['draft', 'reviewing', 'reviewed', 'offline', undefined]) {
      expect(canCopyResource('skill', '1.0.0', status)).toBe(false);
    }
    expect(canCopyResource('skill', '1.0.0', 'online', false)).toBe(false);
    expect(canCopyResource('skill', '', 'online')).toBe(false);
  });
});
