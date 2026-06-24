import { describe, expect, it } from 'vitest';

import type { Config } from '@/types/config';

import {
  buildCloneItems,
  hasInvalidCloneItems,
  normalizeCloneItems,
  updateCloneItemField,
} from '../clone-items';

const config = (id: string, dataId: string, groupName: string): Config => ({
  id,
  dataId,
  groupName,
  content: '',
  md5: '',
  type: 'text',
  appName: '',
  configTags: '',
  desc: '',
  createTime: '',
  modifyTime: '',
});

describe('configuration clone items', () => {
  it('builds clone payload items from the selected source configs', () => {
    const items = buildCloneItems(
      [
        config('101', 'order-service.yaml', 'DEFAULT_GROUP'),
        config('102', 'billing-service.yaml', 'DEFAULT_GROUP'),
      ],
      new Set(['102']),
    );

    expect(items).toEqual([
      {
        cfgId: '102',
        dataId: 'billing-service.yaml',
        group: 'DEFAULT_GROUP',
      },
    ]);
  });

  it('keeps edited target dataId and group in the clone payload', () => {
    const sourceItems = buildCloneItems(
      [config('101', 'order-service.yaml', 'DEFAULT_GROUP')],
      new Set(['101']),
    );

    const renamed = updateCloneItemField(
      updateCloneItemField(sourceItems, '101', 'dataId', 'order-service-gray.yaml'),
      '101',
      'group',
      'GRAY_GROUP',
    );

    expect(normalizeCloneItems(renamed)).toEqual([
      {
        cfgId: '101',
        dataId: 'order-service-gray.yaml',
        group: 'GRAY_GROUP',
      },
    ]);
  });

  it('treats blank target dataId or group as invalid', () => {
    expect(
      hasInvalidCloneItems([
        { cfgId: '101', dataId: '   ', group: 'DEFAULT_GROUP' },
        { cfgId: '102', dataId: 'billing.yaml', group: '' },
      ]),
    ).toBe(true);
  });
});
