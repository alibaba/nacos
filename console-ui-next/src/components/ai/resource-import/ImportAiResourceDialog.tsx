import { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { CheckSquare, X } from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group';
import { Checkbox } from '@/components/ui/checkbox';
import { Badge } from '@/components/ui/badge';
import { ScrollArea } from '@/components/ui/scroll-area';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { aiResourceImportApi } from '@/api/aiResourceImport';
import type {
  AiResourceImportCandidateItem,
  AiResourceImportItem,
  AiResourceImportSourceInfo,
  AiResourceImportValidationItem,
} from '@/types/aiResourceImport';

interface ImportAiResourceDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  namespaceId: string;
  resourceType: string;
  translationPrefix: string;
  onSuccess: () => void;
  metadataKeys?: string[];
  showMetadataLabels?: boolean;
  pageSize?: number;
}

const DEFAULT_PAGE_SIZE = 12;

function itemKey(item: AiResourceImportCandidateItem | AiResourceImportValidationItem): string {
  return `${item.externalId || item.name || 'unknown'}__${item.version || ''}`;
}

function toImportItem(item: AiResourceImportCandidateItem): AiResourceImportItem {
  return {
    externalId: item.externalId,
    name: item.name,
    version: item.version,
    metadata: item.metadata,
  };
}

function isImportable(item?: AiResourceImportValidationItem, overwriteExisting?: boolean): boolean {
  const status = item?.status;
  if (!status) return true;
  if (status === 'VALID' || status === 'WARNING') return true;
  if (status === 'CONFLICT') return !!overwriteExisting;
  return false;
}

function mergeValidationItems(
  previous: AiResourceImportValidationItem[] | null,
  nextItems: AiResourceImportValidationItem[]
): AiResourceImportValidationItem[] {
  const merged = new Map<string, AiResourceImportValidationItem>();
  previous?.forEach(item => merged.set(itemKey(item), item));
  nextItems.forEach(item => merged.set(itemKey(item), item));
  return Array.from(merged.values());
}

export function ImportAiResourceDialog({
  open,
  onOpenChange,
  namespaceId,
  resourceType,
  translationPrefix,
  onSuccess,
  metadataKeys,
  showMetadataLabels = true,
  pageSize = DEFAULT_PAGE_SIZE,
}: ImportAiResourceDialogProps) {
  const { t } = useTranslation();
  const [sources, setSources] = useState<AiResourceImportSourceInfo[]>([]);
  const [sourceId, setSourceId] = useState('');
  const [query, setQuery] = useState('');
  const [overwriteExisting, setOverwriteExisting] = useState(false);
  const [skipInvalid, setSkipInvalid] = useState(true);
  const [loading, setLoading] = useState(false);
  const [executing, setExecuting] = useState(false);
  const [candidates, setCandidates] = useState<AiResourceImportCandidateItem[]>([]);
  const [nextCursor, setNextCursor] = useState('');
  const [hasMore, setHasMore] = useState(false);
  const [validationItems, setValidationItems] = useState<AiResourceImportValidationItem[] | null>(
    null
  );
  const [validationToken, setValidationToken] = useState('');
  const [validatedImportItems, setValidatedImportItems] = useState<
    Map<string, AiResourceImportItem>
  >(new Map());
  const [selectedKeys, setSelectedKeys] = useState<Set<string>>(new Set());

  const text = useCallback(
    (key: string, defaultValue: string, options?: Record<string, unknown>) =>
      t(`${translationPrefix}.${key}`, { defaultValue, ...options }),
    [t, translationPrefix]
  );

  const selectedSource = useMemo(
    () => sources.find(source => source.sourceId === sourceId),
    [sources, sourceId]
  );

  const validationMap = useMemo(() => {
    const result = new Map<string, AiResourceImportValidationItem>();
    validationItems?.forEach(item => result.set(itemKey(item), item));
    return result;
  }, [validationItems]);

  const isSelectableCandidate = useCallback(
    (candidate: AiResourceImportCandidateItem) => {
      if (!validationItems) return true;
      const validation = validationMap.get(itemKey(candidate));
      return !validation || isImportable(validation, overwriteExisting);
    },
    [overwriteExisting, validationItems, validationMap]
  );

  const selectableCandidateKeys = useMemo(
    () => candidates.filter(isSelectableCandidate).map(itemKey),
    [candidates, isSelectableCandidate]
  );

  const allSelectableSelected = useMemo(
    () =>
      selectableCandidateKeys.length > 0 &&
      selectableCandidateKeys.every(key => selectedKeys.has(key)),
    [selectableCandidateKeys, selectedKeys]
  );

  const importableValidatedItems = useMemo(
    () =>
      Array.from(validatedImportItems.entries())
        .filter(([key]) => validationMap.has(key))
        .filter(([key]) => isImportable(validationMap.get(key), overwriteExisting))
        .map(([, item]) => item),
    [overwriteExisting, validatedImportItems, validationMap]
  );

  const resetForm = useCallback(() => {
    setSources([]);
    setSourceId('');
    setQuery('');
    setOverwriteExisting(false);
    setSkipInvalid(true);
    setCandidates([]);
    setNextCursor('');
    setHasMore(false);
    setValidationItems(null);
    setValidationToken('');
    setValidatedImportItems(new Map());
    setSelectedKeys(new Set());
  }, []);

  const searchCandidates = useCallback(
    async (targetSourceId: string, append = false, cursor = '', targetQuery = '') => {
      if (!targetSourceId) return;
      setLoading(true);
      try {
        const response = await aiResourceImportApi.search({
          namespaceId,
          resourceType,
          sourceId: targetSourceId,
          query: targetQuery.trim() || undefined,
          cursor: cursor || undefined,
          limit: pageSize,
        });
        const items = response.data?.items || [];
        setCandidates(prev => {
          const next = append ? [...prev] : [];
          const keys = new Set(next.map(itemKey));
          items.forEach(item => {
            const key = itemKey(item);
            if (!keys.has(key)) {
              keys.add(key);
              next.push(item);
            }
          });
          return next;
        });
        if (!append) {
          setSelectedKeys(new Set());
        }
        setNextCursor(response.data?.nextCursor || '');
        setHasMore(!!response.data?.hasMore);
      } catch {
        // Error handled by interceptor
      } finally {
        setLoading(false);
      }
    },
    [namespaceId, pageSize, resourceType]
  );

  const loadSources = useCallback(async () => {
    setLoading(true);
    try {
      const response = await aiResourceImportApi.listSources({ resourceType });
      const enabledSources = (response.data || []).filter(source => source.enabled !== false);
      setSources(enabledSources);
      const firstSourceId = enabledSources[0]?.sourceId || '';
      setSourceId(firstSourceId);
      if (firstSourceId) {
        await searchCandidates(firstSourceId, false, '', '');
      }
    } catch {
      // Error handled by interceptor
    } finally {
      setLoading(false);
    }
  }, [resourceType, searchCandidates]);

  const handleClose = useCallback(
    (nextOpen: boolean) => {
      if (!nextOpen) resetForm();
      onOpenChange(nextOpen);
    },
    [onOpenChange, resetForm]
  );

  useEffect(() => {
    if (open) {
      resetForm();
      loadSources();
    }
  }, [open, loadSources, resetForm]);

  const handleOpenChange = useCallback(
    (nextOpen: boolean) => {
      if (!nextOpen) {
        handleClose(false);
        return;
      }
      onOpenChange(true);
    },
    [handleClose, onOpenChange]
  );

  const handleSourceChange = (value: string) => {
    setSourceId(value);
    setCandidates([]);
    setSelectedKeys(new Set());
    setValidationItems(null);
    setValidationToken('');
    setValidatedImportItems(new Map());
    setNextCursor('');
    setHasMore(false);
    searchCandidates(value, false, '', query);
  };

  const handleSearch = () => {
    setCandidates([]);
    setSelectedKeys(new Set());
    setNextCursor('');
    setHasMore(false);
    searchCandidates(sourceId, false, '', query);
  };

  const selectedImportItems = (onlyImportable = false) =>
    candidates
      .filter(candidate => selectedKeys.has(itemKey(candidate)))
      .filter(candidate => {
        if (!onlyImportable) return true;
        const validation = validationMap.get(itemKey(candidate));
        return !!validation && isImportable(validation, overwriteExisting);
      })
      .map(toImportItem);

  const validateSelected = async () => {
    const items = selectedImportItems();
    if (!items.length) return;
    setLoading(true);
    try {
      const response = await aiResourceImportApi.validate({
        namespaceId,
        resourceType,
        sourceId,
        selectedItems: JSON.stringify(items),
        overwriteExisting,
      });
      const nextValidationItems = response.data?.items || [];
      const nextValidationMap = new Map(nextValidationItems.map(item => [itemKey(item), item]));
      setValidationItems(prev => mergeValidationItems(prev, nextValidationItems));
      setValidationToken(response.data?.validationToken || '');
      setValidatedImportItems(prev => {
        const next = new Map(prev);
        items.forEach(item => next.set(itemKey(item), item));
        return next;
      });
      setSelectedKeys(prev => {
        const next = new Set<string>();
        prev.forEach(key => {
          const validation = nextValidationMap.get(key);
          if (!validation || isImportable(validation, overwriteExisting)) next.add(key);
        });
        return next;
      });
    } catch {
      // Error handled by interceptor
    } finally {
      setLoading(false);
    }
  };

  const executeImport = async (allImportable = false) => {
    if (!validationItems) return;
    const items = allImportable
      ? importableValidatedItems
      : selectedImportItems(true);
    if (!items.length) return;
    setExecuting(true);
    try {
      const response = await aiResourceImportApi.execute({
        namespaceId,
        resourceType,
        sourceId,
        selectedItems: JSON.stringify(items),
        overwriteExisting,
        skipInvalid,
        validationToken,
      });
      const data = response.data || {};
      if (data.failedCount) {
        toast.error(
          text('importResult', 'Import result: {{success}} succeeded, {{failed}} failed, {{skipped}} skipped', {
            success: data.successCount || 0,
            failed: data.failedCount || 0,
            skipped: data.skippedCount || 0,
          })
        );
        await searchCandidates(sourceId, false, '', query);
        return;
      }
      toast.success(text('importSuccess', 'Import successful'));
      handleClose(false);
      onSuccess();
    } catch {
      // Error handled by interceptor
    } finally {
      setExecuting(false);
    }
  };

  const toggleCandidate = (candidate: AiResourceImportCandidateItem) => {
    const key = itemKey(candidate);
    const validation = validationMap.get(key);
    if (validationItems && validation && !isImportable(validation, overwriteExisting)) return;
    setSelectedKeys(prev => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  };

  const selectAllCandidates = () => {
    setSelectedKeys(new Set(selectableCandidateKeys));
  };

  const clearSelection = () => {
    setSelectedKeys(new Set());
  };

  const statusBadge = (item?: AiResourceImportValidationItem) => {
    if (!item?.status) return null;
    if (item.status === 'VALID') {
      return (
        <Badge className="bg-emerald-100 text-emerald-700 border-0 text-[10px]">
          {text('importStatusValid', 'valid')}
        </Badge>
      );
    }
    if (item.status === 'WARNING') {
      return (
        <Badge className="bg-amber-100 text-amber-700 border-0 text-[10px]">
          {text('importStatusWarning', 'warning')}
        </Badge>
      );
    }
    if (item.status === 'CONFLICT') {
      return <Badge variant="secondary" className="text-[10px]">{text('importStatusConflict', 'conflict')}</Badge>;
    }
    return <Badge variant="destructive" className="text-[10px]">{text('importStatusInvalid', 'invalid')}</Badge>;
  };

  const metadataEntries = (candidate: AiResourceImportCandidateItem) => {
    const metadata = candidate.metadata || {};
    const entries = metadataKeys?.length
      ? metadataKeys.map(key => [key, metadata[key]] as [string, string | undefined])
      : Object.entries(metadata);
    return entries.filter(([, value]) => value).slice(0, 4);
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-w-4xl">
        <DialogHeader>
          <DialogTitle>{text('importFromRegistry', 'Import from Registry')}</DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          <div className="grid grid-cols-[minmax(14rem,18rem)_1fr_auto] gap-3 items-end max-sm:grid-cols-1">
            <div className="space-y-2">
              <Label>{text('importSource', 'Source')}</Label>
              <Select value={sourceId} onValueChange={handleSourceChange} disabled={loading || !sources.length}>
                <SelectTrigger>
                  <SelectValue placeholder={text('importSource', 'Source')} />
                </SelectTrigger>
                <SelectContent>
                  {sources.map(source => (
                    <SelectItem key={source.sourceId} value={source.sourceId}>
                      {source.displayName || source.sourceId}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>{t('common.search')}</Label>
              <Input
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') handleSearch();
                }}
                placeholder={text('importSearchPlaceholder', t('common.search'))}
              />
            </div>
            <Button onClick={handleSearch} disabled={!sourceId || loading}>
              {loading ? t('common.loading') : t('common.search')}
            </Button>
          </div>

          {selectedSource && (
            <div className="flex flex-wrap gap-2 text-xs text-muted-foreground">
              <span>{selectedSource.description || selectedSource.displayName || selectedSource.sourceId}</span>
              {selectedSource.pluginName && <Badge variant="secondary">{selectedSource.pluginName}</Badge>}
              {(selectedSource.capabilities || []).map(capability => (
                <Badge key={capability} variant="outline">{capability}</Badge>
              ))}
            </div>
          )}

          <div className="flex flex-wrap gap-6">
            <div className="flex items-center gap-2">
              <Label className="text-sm font-normal">{text('importConflictPolicy', 'Conflict Policy')}</Label>
              <ToggleGroup
                type="single"
                value={overwriteExisting ? 'overwrite' : 'skip'}
                onValueChange={(value) => {
                  if (!value) return;
                  setOverwriteExisting(value === 'overwrite');
                  setValidationItems(null);
                  setValidatedImportItems(new Map());
                  setValidationToken('');
                }}
                className="rounded-md border bg-background p-0.5"
              >
                <ToggleGroupItem value="skip" size="sm" className="h-7 px-3">
                  {text('importConflictSkip', 'Skip')}
                </ToggleGroupItem>
                <ToggleGroupItem value="overwrite" size="sm" className="h-7 px-3">
                  {text('importConflictOverwrite', 'Overwrite')}
                </ToggleGroupItem>
              </ToggleGroup>
            </div>
            <div className="flex items-center gap-2">
              <Switch checked={skipInvalid} onCheckedChange={setSkipInvalid} />
              <Label className="text-sm font-normal">{text('importSkipInvalid', 'Skip Invalid')}</Label>
            </div>
          </div>

          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className="flex flex-wrap gap-2 text-xs text-muted-foreground">
              <span>{text('importSelectedCount', '{{count}} selected', { count: selectedKeys.size })}</span>
              <span>
                {text('importValidatedCount', '{{count}} valid after validation', {
                  count: importableValidatedItems.length,
                })}
              </span>
            </div>
            <div className="flex items-center gap-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={selectAllCandidates}
                disabled={!selectableCandidateKeys.length || allSelectableSelected || loading}
              >
                <CheckSquare className="h-3.5 w-3.5" />
                {text('importSelectAll', 'Select all')}
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={clearSelection}
                disabled={!selectedKeys.size || loading}
              >
                <X className="h-3.5 w-3.5" />
                {text('importClearSelection', 'Clear')}
              </Button>
            </div>
          </div>

          <ScrollArea className="h-[24rem] border rounded-md p-3">
            {!sources.length && !loading ? (
              <div className="h-72 flex items-center justify-center text-sm text-muted-foreground">
                {text('noImportSource', 'No import source available')}
              </div>
            ) : candidates.length ? (
              <div className="grid grid-cols-2 gap-3 max-sm:grid-cols-1">
                {candidates.map(candidate => {
                  const key = itemKey(candidate);
                  const validation = validationMap.get(key);
                  const disabled = validationItems && !isSelectableCandidate(candidate);
                  const checked = selectedKeys.has(key);
                  return (
                    <button
                      key={key}
                      type="button"
                      disabled={!!disabled}
                      onClick={() => toggleCandidate(candidate)}
                      className={`text-left border rounded-md p-3 transition-colors ${
                        checked ? 'border-primary bg-primary/5' : 'border-border hover:bg-muted/50'
                      } ${disabled ? 'opacity-60 cursor-not-allowed' : ''}`}
                    >
                      <div className="flex items-start gap-3">
                        <Checkbox checked={checked} disabled={!!disabled} className="mt-1" />
                        <div className="min-w-0 flex-1">
                          <div className="flex items-center gap-2 min-w-0">
                            <span className="text-sm font-medium truncate">
                              {candidate.name || candidate.externalId || text('importUnnamed', 'Unnamed')}
                            </span>
                            {candidate.version && (
                              <Badge variant="outline" className="text-[10px] shrink-0">
                                v{candidate.version}
                              </Badge>
                            )}
                            {statusBadge(validation)}
                          </div>
                          <p className="text-xs text-muted-foreground line-clamp-2 mt-1">
                            {candidate.description || '--'}
                          </p>
                          <div className="flex flex-wrap items-center gap-2 mt-2 text-[11px] text-muted-foreground">
                            {metadataEntries(candidate).map(([key, value]) => {
                              const metadataLabel = text(`importMetadata.${key}`, key);
                              return (
                                <Badge key={key} variant="secondary" className="text-[10px]">
                                  {showMetadataLabels ? `${metadataLabel}: ${value}` : value}
                                </Badge>
                              );
                            })}
                            {validation?.errors?.length ? (
                              <span className="text-destructive truncate">{validation.errors.join('; ')}</span>
                            ) : validation?.warnings?.length ? (
                              <span className="text-amber-600 truncate">{validation.warnings.join('; ')}</span>
                            ) : null}
                          </div>
                        </div>
                      </div>
                    </button>
                  );
                })}
              </div>
            ) : (
              <div className="h-72 flex items-center justify-center text-sm text-muted-foreground">
                {loading ? t('common.loading') : t('common.noData')}
              </div>
            )}
          </ScrollArea>

          {hasMore && (
            <div className="flex justify-center">
              <Button
                variant="outline"
                onClick={() => searchCandidates(sourceId, true, nextCursor, query)}
                disabled={loading}
              >
                {loading ? t('common.loading') : text('importLoadMore', 'Load more')}
              </Button>
            </div>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => handleClose(false)} disabled={loading || executing}>
            {t('common.cancel')}
          </Button>
          <Button onClick={validateSelected} disabled={!selectedKeys.size || loading || executing}>
            {loading ? t('common.loading') : text('importValidate', 'Validate')}
          </Button>
          <Button
            onClick={() => executeImport(false)}
            disabled={
              !validationItems ||
              !selectedKeys.size ||
              selectedImportItems(true).length !== selectedKeys.size ||
              loading ||
              executing
            }
          >
            {executing ? t('common.loading') : text('importExecute', 'Execute Import')}
          </Button>
          <Button
            variant="secondary"
            onClick={() => executeImport(true)}
            disabled={!validationItems || !importableValidatedItems.length || loading || executing}
          >
            {text('importAll', 'Import all valid')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
