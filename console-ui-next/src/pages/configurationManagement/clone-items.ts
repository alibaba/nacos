import type { Config, ConfigCloneItem } from '@/types/config';

export type CloneItemField = 'dataId' | 'group';

export const buildCloneItems = (
  configs: Config[],
  selectedIds: Set<string>,
): ConfigCloneItem[] =>
  configs
    .filter((config) => config.id && selectedIds.has(config.id))
    .map((config) => ({
      cfgId: config.id!,
      dataId: config.dataId,
      group: config.groupName,
    }));

export const updateCloneItemField = (
  items: ConfigCloneItem[],
  cfgId: string,
  field: CloneItemField,
  value: string,
): ConfigCloneItem[] =>
  items.map((item) =>
    item.cfgId === cfgId
      ? {
          ...item,
          [field]: value,
        }
      : item,
  );

export const normalizeCloneItems = (items: ConfigCloneItem[]): ConfigCloneItem[] =>
  items.map((item) => ({
    ...item,
    dataId: item.dataId.trim(),
    group: item.group.trim(),
  }));

export const hasInvalidCloneItems = (items: ConfigCloneItem[]): boolean =>
  items.some((item) => !item.dataId.trim() || !item.group.trim());
