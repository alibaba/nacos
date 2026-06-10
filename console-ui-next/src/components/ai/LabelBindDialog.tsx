import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Search, Plus, Tag } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Checkbox } from '@/components/ui/checkbox';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import {
  isReservedLabelKey,
  isValidLabelKey,
} from '@/pages/agentSpecManagement/components/label-utils';

interface LabelBindDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** The version being edited */
  version: string;
  /** All label->version mappings */
  allLabels: Record<string, string>;
  onSave: (labels: Record<string, string>) => Promise<void>;
}

export function LabelBindDialog({
  open,
  onOpenChange,
  version,
  allLabels,
  onSave,
}: LabelBindDialogProps) {
  const { t } = useTranslation();
  const [saving, setSaving] = useState(false);
  const [searchText, setSearchText] = useState('');
  const [error, setError] = useState('');

  // Draft: labels that should point to this version
  const [checkedLabels, setCheckedLabels] = useState<Set<string>>(new Set());
  // Newly created labels in this session
  const [newLabels, setNewLabels] = useState<string[]>([]);

  useEffect(() => {
    if (!open) {
      return;
    }
    const checked = new Set<string>();
    for (const [key, val] of Object.entries(allLabels)) {
      if (val === version) {
        checked.add(key);
      }
    }
    setCheckedLabels(checked);
    setNewLabels([]);
    setSearchText('');
    setError('');
  }, [open, version, allLabels]);

  // Initialize state when dialog opens
  const handleOpenChange = (nextOpen: boolean) => {
    onOpenChange(nextOpen);
  };

  // All known label names (existing + newly created)
  const allLabelNames = useMemo(() => {
    const names = new Set(Object.keys(allLabels));
    for (const n of newLabels) {
      if (!isReservedLabelKey(n)) {
        names.add(n);
      }
    }
    return Array.from(names).sort();
  }, [allLabels, newLabels]);

  // Filtered by search
  const filteredLabels = useMemo(() => {
    const q = searchText.trim().toLowerCase();
    if (!q) return allLabelNames;
    return allLabelNames.filter((name) => name.toLowerCase().includes(q));
  }, [allLabelNames, searchText]);
  const selectableFilteredLabels = useMemo(
    () => filteredLabels.filter((name) => !isReservedLabelKey(name)),
    [filteredLabels],
  );

  // Whether search text matches an existing label exactly
  const exactMatch = allLabelNames.some(
    (n) => n.toLowerCase() === searchText.trim().toLowerCase(),
  );

  const isSearchingReservedLabel = isReservedLabelKey(searchText.trim());
  const canCreate = searchText.trim()
    && !isSearchingReservedLabel
    && !exactMatch
    && isValidLabelKey(searchText.trim(), []);

  const handleToggle = (label: string, checked: boolean) => {
    if (isReservedLabelKey(label)) {
      return;
    }
    const next = new Set(checkedLabels);
    if (checked) {
      next.add(label);
    } else {
      next.delete(label);
    }
    setCheckedLabels(next);
  };

  const handleCreate = () => {
    const trimmed = searchText.trim();
    if (!trimmed) return;
    if (!isValidLabelKey(trimmed, allLabelNames)) {
      if (allLabelNames.includes(trimmed)) {
        setError(t('common.versionLabels.keyDuplicate'));
      } else {
        setError(t('common.versionLabels.keyInvalid'));
      }
      return;
    }
    setNewLabels((prev) => [...prev, trimmed]);
    setCheckedLabels((prev) => new Set([...prev, trimmed]));
    setSearchText('');
    setError('');
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && canCreate) {
      e.preventDefault();
      handleCreate();
    }
  };

  const handleSelectAll = () => {
    const next = new Set(checkedLabels);
    for (const name of selectableFilteredLabels) {
      next.add(name);
    }
    setCheckedLabels(next);
  };

  const handleClearAll = () => {
    const next = new Set(checkedLabels);
    for (const name of selectableFilteredLabels) {
      next.delete(name);
    }
    setCheckedLabels(next);
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      // Build the new label mapping
      const result: Record<string, string> = {};

      // Keep labels that are NOT being modified (pointing to other versions)
      for (const [key, val] of Object.entries(allLabels)) {
        if (isReservedLabelKey(key)) {
          continue;
        }
        if (val !== version) {
          // If this label is now checked for this version, override it
          if (checkedLabels.has(key)) {
            result[key] = version;
          } else {
            result[key] = val;
          }
        } else {
          // Was pointing to this version - keep only if still checked
          if (checkedLabels.has(key)) {
            result[key] = version;
          }
          // else: removed
        }
      }

      // Add newly created labels that are checked
      for (const name of newLabels) {
        if (!isReservedLabelKey(name) && checkedLabels.has(name) && !(name in result)) {
          result[name] = version;
        }
      }

      await onSave(result);
      onOpenChange(false);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Tag className="h-4 w-4" />
            {t('common.versionLabels.labelManagement')}
          </DialogTitle>
          <DialogDescription>
            {t('common.versionLabels.labelBindDesc', { version })}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {/* Search / Create input */}
          <div className="relative">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
            <Input
              value={searchText}
              onChange={(e) => {
                setSearchText(e.target.value);
                setError('');
              }}
              onKeyDown={handleKeyDown}
              placeholder={t('common.versionLabels.searchOrCreateLabel')}
              className="pl-8 text-sm"
            />
          </div>

          {error && (
            <p className="text-xs text-destructive">{error}</p>
          )}

          {isSearchingReservedLabel && (
            <p className="text-xs text-muted-foreground">
              {t('common.versionLabels.reservedLatest')}
            </p>
          )}

          {/* Create hint */}
          {canCreate && (
            <button
              type="button"
              className="flex items-center gap-2 w-full px-3 py-2 text-sm rounded-md hover:bg-muted/60 transition-colors text-primary"
              onClick={handleCreate}
            >
              <Plus className="h-3.5 w-3.5" />
              {t('common.versionLabels.createLabel', { name: searchText.trim() })}
            </button>
          )}

          {/* Label list */}
          {filteredLabels.length > 0 && (
            <div className="space-y-1">
              {/* Select all / Clear */}
              {selectableFilteredLabels.length > 0 && (
                <div className="flex items-center gap-2 px-1 pb-1 text-xs text-muted-foreground">
                  <button
                    type="button"
                    className="hover:text-foreground transition-colors"
                    onClick={handleSelectAll}
                  >
                    {t('common.versionLabels.selectAll', {
                      count: selectableFilteredLabels.length,
                    })}
                  </button>
                  <span>·</span>
                  <button
                    type="button"
                    className="hover:text-foreground transition-colors"
                    onClick={handleClearAll}
                  >
                    {t('common.versionLabels.clearAll')}
                  </button>
                </div>
              )}

              <div className="max-h-[240px] overflow-y-auto space-y-0.5">
                {filteredLabels.map((name) => {
                  const isChecked = checkedLabels.has(name);
                  const boundVersion = allLabels[name];
                  const isBoundToOther = boundVersion && boundVersion !== version;
                  const isReserved = isReservedLabelKey(name);

                  return (
                    <label
                      key={name}
                      className={`flex items-center gap-3 px-3 py-2 rounded-md hover:bg-muted/60 transition-colors ${
                        isReserved ? 'cursor-not-allowed opacity-70' : 'cursor-pointer'
                      }`}
                      title={isReserved ? t('common.versionLabels.reservedLatest') : undefined}
                    >
                      <Checkbox
                        checked={isChecked}
                        disabled={isReserved}
                        onCheckedChange={(checked) =>
                          handleToggle(name, checked === true)
                        }
                      />
                      <span className="flex-1 text-sm font-mono">{name}</span>
                      {isChecked ? (
                        <Badge variant="secondary" className="text-[10px] px-1.5 py-0 h-4 font-mono">
                          → {version}
                        </Badge>
                      ) : isBoundToOther ? (
                        <span className="text-[10px] text-muted-foreground font-mono">
                          → {boundVersion}
                        </span>
                      ) : null}
                    </label>
                  );
                })}
              </div>
            </div>
          )}

          {filteredLabels.length === 0 && !canCreate && (
            <p className="text-sm text-muted-foreground text-center py-4">
              {t('common.versionLabels.noLabels')}
            </p>
          )}
        </div>

        <DialogFooter>
          <Button
            onClick={handleSave}
            disabled={saving}
            className="w-full"
          >
            {saving ? t('common.loading') : t('common.versionLabels.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
