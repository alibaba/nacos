import { useState, useId } from 'react';
import { useTranslation } from 'react-i18next';
import { Plus, Save, X } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  isReservedLabelKey,
  isValidLabelKey,
} from '@/pages/agentSpecManagement/components/label-utils';

interface VersionLabelEditorProps {
  labels: Record<string, string>;
  availableVersions?: string[];
  onSave: (labels: Record<string, string>) => Promise<void> | void;
  isSaving?: boolean;
  onChange?: (labels: Record<string, string>) => void;
  showSaveButton?: boolean;
}

export function VersionLabelEditor({
  labels,
  availableVersions,
  onSave,
  isSaving = false,
  onChange,
  showSaveButton = true,
}: VersionLabelEditorProps) {
  const { t } = useTranslation();
  const listId = useId();
  const labelsKey = JSON.stringify(labels);
  const [draftState, setDraftState] = useState<{
    labelsKey: string;
    draft: Record<string, string>;
  }>(() => ({
    labelsKey,
    draft: { ...labels },
  }));
  const [newKey, setNewKey] = useState('');
  const [newValue, setNewValue] = useState('');
  const [error, setError] = useState('');

  const draft = draftState.labelsKey === labelsKey ? draftState.draft : { ...labels };
  const dirty = JSON.stringify(draft) !== labelsKey;

  const updateDraft = (nextDraft: Record<string, string>) => {
    setDraftState({ labelsKey, draft: nextDraft });
    onChange?.(nextDraft);
  };

  const handleAdd = () => {
    const trimmedKey = newKey.trim();
    const trimmedValue = newValue.trim();

    if (!trimmedKey) {
      setError(t('common.versionLabels.keyRequired'));
      return;
    }

    if (isReservedLabelKey(trimmedKey)) {
      setError(t('common.versionLabels.reservedLatest'));
      return;
    }

    if (!isValidLabelKey(trimmedKey, Object.keys(draft))) {
      const existingKeys = Object.keys(draft);
      if (existingKeys.includes(trimmedKey)) {
        setError(t('common.versionLabels.keyDuplicate'));
      } else {
        setError(t('common.versionLabels.keyInvalid'));
      }
      return;
    }

    updateDraft({ ...draft, [trimmedKey]: trimmedValue });
    setNewKey('');
    setNewValue('');
    setError('');
  };

  const handleDelete = (key: string) => {
    if (isReservedLabelKey(key)) {
      return;
    }
    const next = { ...draft };
    delete next[key];
    updateDraft(next);
  };

  const handleSave = () => {
    onSave(omitReservedLabels(draft));
  };

  const entries = Object.entries(draft);

  return (
    <div className="space-y-3">
      {/* Existing labels */}
      {entries.length > 0 ? (
        <div className="flex flex-wrap gap-2">
          {entries.map(([key, value]) => {
            const isReserved = isReservedLabelKey(key);
            return (
              <Badge
                key={key}
                variant="secondary"
                className="max-w-full gap-1 rounded-md px-2 py-1 text-[11px] font-mono"
                title={isReserved ? t('common.versionLabels.reservedLatest') : undefined}
              >
                <span className="truncate">{key}</span>
                <span className="text-muted-foreground">=</span>
                <span className="truncate">{value || '-'}</span>
                {!isReserved && (
                  <button
                    type="button"
                    className="ml-1 inline-flex h-4 w-4 items-center justify-center rounded-sm text-muted-foreground transition-colors hover:bg-black/5 hover:text-foreground dark:hover:bg-white/10"
                    onClick={() => handleDelete(key)}
                  >
                    <X className="h-3 w-3" />
                  </button>
                )}
              </Badge>
            );
          })}
        </div>
      ) : (
        <p className="text-xs text-muted-foreground">{t('common.versionLabels.noLabels')}</p>
      )}

      {/* Add new label */}
      <div className="flex items-center gap-2">
        <Input
          value={newKey}
          onChange={(e) => {
            setNewKey(e.target.value);
            setError('');
          }}
          placeholder={t('common.versionLabels.keyPlaceholder')}
          className="flex-1 font-mono text-xs"
        />
        <Input
          value={newValue}
          onChange={(e) => setNewValue(e.target.value)}
          placeholder={t('common.versionLabels.valuePlaceholder')}
          className="flex-1 font-mono text-xs"
          list={availableVersions ? listId : undefined}
        />
        {availableVersions && (
          <datalist id={listId}>
            {availableVersions.map((v) => (
              <option key={v} value={v} />
            ))}
          </datalist>
        )}
        <Button
          variant="outline"
          size="icon"
          className="h-8 w-8 shrink-0"
          onClick={handleAdd}
        >
          <Plus className="h-3.5 w-3.5" />
        </Button>
      </div>

      {/* Validation error */}
      {error && (
        <p className="text-xs text-destructive">{error}</p>
      )}

      {/* Save button */}
      {showSaveButton && dirty && (
        <Button size="sm" onClick={handleSave} disabled={isSaving} className="gap-1.5">
          <Save className="h-3.5 w-3.5" />
          {t('common.versionLabels.save')}
        </Button>
      )}
    </div>
  );
}

function omitReservedLabels(labels: Record<string, string>): Record<string, string> {
  const result: Record<string, string> = {};
  for (const [key, value] of Object.entries(labels || {})) {
    if (!isReservedLabelKey(key)) {
      result[key] = value;
    }
  }
  return result;
}
