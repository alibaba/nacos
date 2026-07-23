import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  CircleAlert,
  Cloud,
  HardDrive,
  Lock,
  RotateCcw,
  Save,
  Server,
} from 'lucide-react';
import { toast } from 'sonner';

import { pluginApi } from '@/api/plugin';
import type {
  PluginConfigItemDefinition,
  PluginDetail,
  PluginInfo,
} from '@/api/plugin';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Switch } from '@/components/ui/switch';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';

import {
  buildSourceUpdate,
  createEffectiveDraft,
  getRuntimeDefinitions,
  getSourceLabelKey,
  getSourceSnapshot,
  hasLocalOnlyOverrides,
  validatePluginConfigValue,
} from './plugin-config-state';
import type {
  PluginConfigValidationError,
  UpdatablePluginConfigSource,
} from './plugin-config-state';

interface PluginDetailDialogProps {
  open: boolean;
  plugin: PluginInfo | null;
  onOpenChange: (open: boolean) => void;
  onUpdated: () => void;
}

export function PluginDetailDialog({
  open,
  plugin,
  onOpenChange,
  onUpdated,
}: PluginDetailDialogProps) {
  const { t } = useTranslation();
  const [detail, setDetail] = useState<PluginDetail | null>(null);
  const [availability, setAvailability] = useState<Record<string, boolean>>({});
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [clearConfirmOpen, setClearConfirmOpen] = useState(false);
  const [targetSource, setTargetSource] =
    useState<UpdatablePluginConfigSource>('RUNTIME_PERSISTED');
  const [draft, setDraft] = useState<Record<string, string>>({});
  const [dirtyKeys, setDirtyKeys] = useState<Set<string>>(new Set());
  const [resetKeys, setResetKeys] = useState<Set<string>>(new Set());
  const [errors, setErrors] =
    useState<Record<string, PluginConfigValidationError>>({});
  const loadRequestId = useRef(0);

  const loadDetail = useCallback(async () => {
    if (!plugin) {
      return;
    }
    const requestId = ++loadRequestId.current;
    setLoading(true);
    try {
      const detailResponse = await pluginApi.detail(plugin.pluginType, plugin.pluginName);
      if (requestId !== loadRequestId.current) {
        return;
      }
      if (detailResponse.code !== 0) {
        toast.error(detailResponse.message);
        return;
      }
      const nextDetail = detailResponse.data;
      setDetail(nextDetail);
      setDraft(createEffectiveDraft(nextDetail));
      setDirtyKeys(new Set());
      setResetKeys(new Set());
      setErrors({});

      try {
        const availabilityResponse = await pluginApi.availability(
          plugin.pluginType,
          plugin.pluginName,
        );
        if (requestId !== loadRequestId.current) {
          return;
        }
        setAvailability(
          availabilityResponse.code === 0 ? availabilityResponse.data || {} : {},
        );
      } catch {
        if (requestId === loadRequestId.current) {
          setAvailability({});
        }
      }
    } catch {
      if (requestId === loadRequestId.current) {
        setDetail(null);
        setAvailability({});
      }
    } finally {
      if (requestId === loadRequestId.current) {
        setLoading(false);
      }
    }
  }, [plugin]);

  useEffect(() => {
    if (open) {
      setTargetSource('RUNTIME_PERSISTED');
      loadDetail();
    } else {
      setDetail(null);
      setAvailability({});
    }
    return () => {
      loadRequestId.current += 1;
    };
  }, [loadDetail, open]);

  const runtimeDefinitions = useMemo(
    () => (detail ? getRuntimeDefinitions(detail) : []),
    [detail],
  );
  const sourceSnapshot = useMemo(
    () => (detail ? getSourceSnapshot(detail, targetSource) : {}),
    [detail, targetSource],
  );
  const localOnlyActive = detail ? hasLocalOnlyOverrides(detail) : false;
  const clusterUpdateBlocked =
    targetSource === 'RUNTIME_PERSISTED' && localOnlyActive;

  const resetEditingState = (source: UpdatablePluginConfigSource) => {
    setTargetSource(source);
    if (detail) {
      setDraft(createEffectiveDraft(detail));
    }
    setDirtyKeys(new Set());
    setResetKeys(new Set());
    setErrors({});
  };

  const updateDraft = (key: string, value: string) => {
    setDraft(current => ({ ...current, [key]: value }));
    setDirtyKeys(current => new Set(current).add(key));
    setResetKeys(current => {
      const next = new Set(current);
      next.delete(key);
      return next;
    });
    setErrors(current => {
      const next = { ...current };
      delete next[key];
      return next;
    });
  };

  const toggleReset = (key: string) => {
    if (!detail) {
      return;
    }
    if (resetKeys.has(key)) {
      setResetKeys(current => {
        const next = new Set(current);
        next.delete(key);
        return next;
      });
      return;
    }

    if (Object.prototype.hasOwnProperty.call(sourceSnapshot, key)) {
      setResetKeys(current => new Set(current).add(key));
      setDirtyKeys(current => {
        const next = new Set(current);
        next.delete(key);
        return next;
      });
      return;
    }

    setDirtyKeys(current => {
      const next = new Set(current);
      next.delete(key);
      return next;
    });
    setDraft(current => ({
      ...current,
      [key]: detail.config?.[key] ?? '',
    }));
  };

  const validateDirtyValues = () => {
    if (!detail) {
      return false;
    }
    const nextErrors: Record<string, PluginConfigValidationError> = {};
    for (const definition of runtimeDefinitions) {
      if (!dirtyKeys.has(definition.key) || resetKeys.has(definition.key)) {
        continue;
      }
      const error = validatePluginConfigValue(
        definition,
        draft[definition.key] ?? '',
      );
      if (error) {
        nextErrors[definition.key] = error;
      }
    }
    setErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  };

  const saveConfig = async () => {
    if (!detail || clusterUpdateBlocked || !validateDirtyValues()) {
      return;
    }
    const config = buildSourceUpdate(
      detail,
      targetSource,
      draft,
      dirtyKeys,
      resetKeys,
    );
    setSaving(true);
    try {
      const response = await pluginApi.updateConfig({
        pluginType: detail.pluginType,
        pluginName: detail.pluginName,
        config,
        localOnly: targetSource === 'LOCAL_ONLY',
      });
      if (response.code !== 0) {
        toast.error(response.message);
        return;
      }
      toast.success(
        targetSource === 'LOCAL_ONLY'
          ? t('plugin.localConfigUpdated')
          : t('plugin.configUpdated'),
      );
      await loadDetail();
      onUpdated();
    } catch {
      // Error toast is handled by the shared API interceptor.
    } finally {
      setSaving(false);
    }
  };

  const clearLocalOnlyConfig = async () => {
    if (!detail) {
      return;
    }
    setSaving(true);
    try {
      const response = await pluginApi.updateConfig({
        pluginType: detail.pluginType,
        pluginName: detail.pluginName,
        config: {},
        localOnly: true,
      });
      if (response.code !== 0) {
        toast.error(response.message);
        return;
      }
      toast.success(t('plugin.localConfigCleared'));
      setClearConfirmOpen(false);
      await loadDetail();
      onUpdated();
    } catch {
      // Error toast is handled by the shared API interceptor.
    } finally {
      setSaving(false);
    }
  };

  const renderInput = (definition: PluginConfigItemDefinition) => {
    const value = draft[definition.key] ?? '';
    const resetPending = resetKeys.has(definition.key);
    const disabled = definition.effectMode !== 'RUNTIME' || resetPending;

    if (definition.type === 'BOOLEAN') {
      return (
        <div className="flex h-9 items-center">
          <Switch
            aria-label={definition.name || definition.key}
            checked={value.toLowerCase() === 'true'}
            disabled={disabled}
            onCheckedChange={checked => updateDraft(definition.key, String(checked))}
          />
        </div>
      );
    }
    if (definition.type === 'ENUM' && definition.enumValues?.length) {
      return (
        <Select
          value={value}
          disabled={disabled}
          onValueChange={nextValue => updateDraft(definition.key, nextValue)}
        >
          <SelectTrigger aria-label={definition.name || definition.key}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {definition.enumValues.map(option => (
              <SelectItem key={option} value={option}>
                {option}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      );
    }
    return (
      <Input
        aria-label={definition.name || definition.key}
        type={definition.type === 'NUMBER' ? 'number' : 'text'}
        value={value}
        disabled={disabled}
        autoComplete={definition.sensitive ? 'new-password' : undefined}
        onChange={event => updateDraft(definition.key, event.target.value)}
      />
    );
  };

  const renderDefinition = (definition: PluginConfigItemDefinition) => {
    const meta = detail?.configValueMetas?.[definition.key];
    const resetPending = resetKeys.has(definition.key);
    const canReset =
      definition.effectMode === 'RUNTIME'
      && (Object.prototype.hasOwnProperty.call(sourceSnapshot, definition.key)
        || dirtyKeys.has(definition.key));
    const error = errors[definition.key];

    return (
      <div
        key={definition.key}
        className="grid gap-3 border-b py-4 last:border-b-0 md:grid-cols-[minmax(0,1fr)_minmax(260px,1.2fr)]"
      >
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-sm font-medium">
              {definition.name || definition.key}
              {definition.required ? ' *' : ''}
            </span>
            <Badge variant="outline" className="font-normal">
              {t(getSourceLabelKey(meta?.source))}
            </Badge>
            <Badge
              variant="outline"
              className={
                definition.effectMode === 'RUNTIME'
                  ? 'border-emerald-200 text-emerald-700'
                  : 'border-amber-200 text-amber-700'
              }
            >
              {definition.effectMode === 'RUNTIME'
                ? t('plugin.runtime')
                : t('plugin.restart')}
            </Badge>
            {meta?.overridden ? (
              <Badge variant="secondary">{t('plugin.overridden')}</Badge>
            ) : null}
            {definition.sensitive ? (
              <Tooltip>
                <TooltipTrigger asChild>
                  <Lock className="h-4 w-4 text-muted-foreground" />
                </TooltipTrigger>
                <TooltipContent>{t('plugin.sensitive')}</TooltipContent>
              </Tooltip>
            ) : null}
          </div>
          <div className="mt-1 truncate font-mono text-xs text-muted-foreground">
            {definition.key}
          </div>
          {definition.description ? (
            <p className="mt-2 text-xs leading-5 text-muted-foreground">
              {definition.description}
            </p>
          ) : null}
        </div>
        <div className="flex min-w-0 items-start gap-2">
          <div className="min-w-0 flex-1">
            {definition.effectMode === 'RESTART' ? (
              <Tooltip>
                <TooltipTrigger asChild>
                  <div>{renderInput(definition)}</div>
                </TooltipTrigger>
                <TooltipContent>{t('plugin.restartHint')}</TooltipContent>
              </Tooltip>
            ) : (
              renderInput(definition)
            )}
            {resetPending ? (
              <p className="mt-1 text-xs text-amber-700">
                {t('plugin.resetPending')}
              </p>
            ) : null}
            {error ? (
              <p className="mt-1 text-xs text-destructive">
                {t(`plugin.validation.${error}`)}
              </p>
            ) : null}
          </div>
          {canReset || resetPending ? (
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  aria-label={
                    resetPending
                      ? t('plugin.undoReset')
                      : t('plugin.resetOverride')
                  }
                  onClick={() => toggleReset(definition.key)}
                >
                  {resetPending ? (
                    <RotateCcw className="h-4 w-4 -scale-x-100" />
                  ) : (
                    <RotateCcw className="h-4 w-4" />
                  )}
                </Button>
              </TooltipTrigger>
              <TooltipContent>
                {resetPending
                  ? t('plugin.undoReset')
                  : t('plugin.resetOverride')}
              </TooltipContent>
            </Tooltip>
          ) : (
            <div className="h-9 w-9 shrink-0" />
          )}
        </div>
      </div>
    );
  };

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="max-h-[88vh] overflow-y-auto sm:max-w-[820px]">
          <DialogHeader>
            <DialogTitle>{t('plugin.detail')}</DialogTitle>
            <DialogDescription>
              {detail?.pluginId || plugin?.pluginId || ''}
            </DialogDescription>
          </DialogHeader>

          {loading && !detail ? (
            <div className="space-y-3 py-2">
              <Skeleton className="h-16 w-full" />
              <Skeleton className="h-28 w-full" />
              <Skeleton className="h-28 w-full" />
            </div>
          ) : null}

          {detail ? (
            <div className="space-y-5">
              <div className="grid grid-cols-2 gap-x-6 gap-y-3 border-b pb-5 text-sm md:grid-cols-4">
                <div>
                  <div className="text-xs text-muted-foreground">
                    {t('plugin.status')}
                  </div>
                  <div className={detail.enabled ? 'mt-1 text-emerald-600' : 'mt-1'}>
                    {detail.enabled ? t('plugin.enabled') : t('plugin.disabled')}
                  </div>
                </div>
                <div>
                  <div className="text-xs text-muted-foreground">
                    {t('plugin.executionMode')}
                  </div>
                  <div className="mt-1">{detail.executionMode}</div>
                </div>
                <div>
                  <div className="text-xs text-muted-foreground">
                    {t('plugin.critical')}
                  </div>
                  <div className="mt-1">
                    {detail.critical ? t('plugin.yes') : t('plugin.no')}
                  </div>
                </div>
                <div>
                  <div className="text-xs text-muted-foreground">
                    {t('plugin.configurable')}
                  </div>
                  <div className="mt-1">
                    {detail.configurable ? t('plugin.yes') : t('plugin.no')}
                  </div>
                </div>
              </div>

              {detail.configDefinitions?.length ? (
                <section>
                  {runtimeDefinitions.length ? (
                    <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
                      <Tabs
                        value={targetSource}
                        onValueChange={value =>
                          resetEditingState(value as UpdatablePluginConfigSource)
                        }
                      >
                        <TabsList>
                          <TabsTrigger value="RUNTIME_PERSISTED" className="gap-2">
                            <Cloud className="h-4 w-4" />
                            {t('plugin.clusterConfig')}
                          </TabsTrigger>
                          <TabsTrigger value="LOCAL_ONLY" className="gap-2">
                            <HardDrive className="h-4 w-4" />
                            {t('plugin.localOnlyConfig')}
                          </TabsTrigger>
                        </TabsList>
                      </Tabs>
                      {localOnlyActive ? (
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          onClick={() => setClearConfirmOpen(true)}
                        >
                          <RotateCcw className="mr-2 h-4 w-4" />
                          {t('plugin.clearLocalOnly')}
                        </Button>
                      ) : null}
                    </div>
                  ) : null}

                  {targetSource === 'LOCAL_ONLY' ? (
                    <div className="mb-2 flex gap-2 border-l-2 border-amber-500 bg-amber-500/5 px-3 py-2 text-sm text-amber-800">
                      <CircleAlert className="mt-0.5 h-4 w-4 shrink-0" />
                      <span>{t('plugin.localOnlyWarning')}</span>
                    </div>
                  ) : null}
                  {clusterUpdateBlocked ? (
                    <div className="mb-2 flex gap-2 border-l-2 border-amber-500 bg-amber-500/5 px-3 py-2 text-sm text-amber-800">
                      <CircleAlert className="mt-0.5 h-4 w-4 shrink-0" />
                      <span>{t('plugin.localOnlyBlocksCluster')}</span>
                    </div>
                  ) : null}

                  <div>{detail.configDefinitions.map(renderDefinition)}</div>
                </section>
              ) : (
                <div className="py-8 text-center text-sm text-muted-foreground">
                  {t('plugin.noConfig')}
                </div>
              )}

              <section className="border-t pt-4">
                <div className="mb-3 flex items-center gap-2 text-sm font-medium">
                  <Server className="h-4 w-4" />
                  {t('plugin.nodeAvailability')}
                </div>
                {Object.keys(availability).length ? (
                  <div className="grid gap-2 sm:grid-cols-2">
                    {Object.entries(availability).map(([address, available]) => (
                      <div
                        key={address}
                        className="flex items-center justify-between border-b py-2 text-sm"
                      >
                        <span className="truncate font-mono text-xs">{address}</span>
                        <span className={available ? 'text-emerald-600' : 'text-destructive'}>
                          {available
                            ? t('plugin.available')
                            : t('plugin.unavailable')}
                        </span>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="text-sm text-muted-foreground">
                    {detail.availableNodeCount} / {detail.totalNodeCount}
                  </div>
                )}
              </section>
            </div>
          ) : null}

          <DialogFooter>
            <Button variant="outline" onClick={() => onOpenChange(false)}>
              {t('common.cancel')}
            </Button>
            {detail?.configurable && runtimeDefinitions.length ? (
              <Button
                onClick={saveConfig}
                disabled={saving || clusterUpdateBlocked}
              >
                <Save className="mr-2 h-4 w-4" />
                {t('plugin.saveConfig')}
              </Button>
            ) : null}
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={clearConfirmOpen} onOpenChange={setClearConfirmOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('plugin.clearLocalOnly')}</DialogTitle>
            <DialogDescription>
              {t('plugin.clearLocalOnlyConfirm')}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setClearConfirmOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={clearLocalOnlyConfig} disabled={saving}>
              {t('common.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
