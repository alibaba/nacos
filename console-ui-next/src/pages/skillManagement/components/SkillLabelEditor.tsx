import { useEffect, useState, useId } from 'react';
import { useTranslation } from 'react-i18next';
import { Plus, Save, X } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { isValidLabelKey } from '@/pages/agentSpecManagement/components/label-utils';

interface SkillLabelEditorProps {
  labels: Record<string, string>;
  availableVersions: string[];
  onSave: (labels: Record<string, string>) => void;
  isSaving?: boolean;
}

export function SkillLabelEditor({
  labels,
  availableVersions,
  onSave,
  isSaving = false,
}: SkillLabelEditorProps) {
  const { t } = useTranslation();
  const listId = useId();
  const [draft, setDraft] = useState<Record<string, string>>({ ...labels });
  const [newKey, setNewKey] = useState('');
  const [newValue, setNewValue] = useState('');
  const [error, setError] = useState('');

  const dirty = JSON.stringify(draft) !== JSON.stringify(labels);

  useEffect(() => {
    setDraft({ ...labels });
  }, [labels]);

  const handleAdd = () => {
    const trimmedKey = newKey.trim();
    const trimmedValue = newValue.trim();

    if (!trimmedKey) {
      setError(t('skill.labelKeyRequired'));
      return;
    }

    if (!isValidLabelKey(trimmedKey, Object.keys(draft))) {
      const existingKeys = Object.keys(draft);
      if (existingKeys.includes(trimmedKey)) {
        setError(t('skill.labelKeyDuplicate'));
      } else {
        setError(t('skill.labelKeyInvalid'));
      }
      return;
    }

    setDraft({ ...draft, [trimmedKey]: trimmedValue });
    setNewKey('');
    setNewValue('');
    setError('');
  };

  const handleDelete = (key: string) => {
    const next = { ...draft };
    delete next[key];
    setDraft(next);
  };

  const handleSave = () => {
    onSave(draft);
  };

  const entries = Object.entries(draft);

  return (
    <div className="space-y-3">
      {/* Existing labels */}
      {entries.length > 0 ? (
        <div className="flex flex-wrap gap-2">
          {entries.map(([key, value]) => (
            <Badge
              key={key}
              variant="secondary"
              className="max-w-full gap-1 rounded-md px-2 py-1 text-[11px] font-mono"
            >
              <span className="truncate">{key}</span>
              <span className="text-muted-foreground">=</span>
              <span className="truncate">{value || '-'}</span>
              <button
                type="button"
                className="ml-1 inline-flex h-4 w-4 items-center justify-center rounded-sm text-muted-foreground transition-colors hover:bg-black/5 hover:text-foreground dark:hover:bg-white/10"
                onClick={() => handleDelete(key)}
              >
                <X className="h-3 w-3" />
              </button>
            </Badge>
          ))}
        </div>
      ) : (
        <p className="text-xs text-muted-foreground">{t('skill.noLabels')}</p>
      )}

      {/* Add new label */}
      <div className="flex items-center gap-2">
        <Input
          value={newKey}
          onChange={(e) => {
            setNewKey(e.target.value);
            setError('');
          }}
          placeholder={t('skill.labelKey')}
          className="flex-1 font-mono text-xs"
        />
        <Input
          value={newValue}
          onChange={(e) => setNewValue(e.target.value)}
          placeholder={t('skill.labelValue')}
          className="flex-1 font-mono text-xs"
          list={listId}
        />
        <datalist id={listId}>
          {availableVersions.map((v) => (
            <option key={v} value={v} />
          ))}
        </datalist>
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
      {dirty && (
        <Button size="sm" onClick={handleSave} disabled={isSaving} className="gap-1.5">
          <Save className="h-3.5 w-3.5" />
          {t('skill.saveLabels')}
        </Button>
      )}
    </div>
  );
}
