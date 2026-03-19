import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Plus, Trash2, Save } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { isValidLabelKey } from './label-utils';

interface LabelEditorProps {
  labels: Record<string, string>;
  onSave: (labels: Record<string, string>) => void;
}

export function LabelEditor({ labels, onSave }: LabelEditorProps) {
  const { t } = useTranslation();
  const [draft, setDraft] = useState<Record<string, string>>({ ...labels });
  const [newKey, setNewKey] = useState('');
  const [newValue, setNewValue] = useState('');
  const [error, setError] = useState('');

  const dirty =
    JSON.stringify(draft) !== JSON.stringify(labels);

  const handleAdd = () => {
    const trimmedKey = newKey.trim();
    const trimmedValue = newValue.trim();

    if (!trimmedKey) {
      setError(t('agentSpec.labelKeyRequired'));
      return;
    }

    if (!isValidLabelKey(trimmedKey, Object.keys(draft))) {
      const existingKeys = Object.keys(draft);
      if (existingKeys.includes(trimmedKey)) {
        setError(t('agentSpec.labelKeyDuplicate'));
      } else {
        setError(t('agentSpec.labelKeyInvalid'));
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

  const handleValueChange = (key: string, value: string) => {
    setDraft({ ...draft, [key]: value });
  };

  const handleSave = () => {
    onSave(draft);
  };

  const entries = Object.entries(draft);

  return (
    <div className="space-y-3">
      {/* Existing labels */}
      {entries.length > 0 && (
        <div className="space-y-2">
          {entries.map(([key, value]) => (
            <div key={key} className="flex items-center gap-2">
              <Input
                value={key}
                disabled
                className="flex-1 font-mono text-xs"
              />
              <Input
                value={value}
                onChange={(e) => handleValueChange(key, e.target.value)}
                className="flex-1 font-mono text-xs"
                placeholder={t('agentSpec.labelValue')}
              />
              <Button
                variant="ghost"
                size="icon"
                className="h-8 w-8 shrink-0 text-destructive hover:text-destructive"
                onClick={() => handleDelete(key)}
              >
                <Trash2 className="h-3.5 w-3.5" />
              </Button>
            </div>
          ))}
        </div>
      )}

      {/* Add new label */}
      <div className="flex items-center gap-2">
        <Input
          value={newKey}
          onChange={(e) => {
            setNewKey(e.target.value);
            setError('');
          }}
          placeholder={t('agentSpec.labelKey')}
          className="flex-1 font-mono text-xs"
        />
        <Input
          value={newValue}
          onChange={(e) => setNewValue(e.target.value)}
          placeholder={t('agentSpec.labelValue')}
          className="flex-1 font-mono text-xs"
        />
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
        <Button size="sm" onClick={handleSave} className="gap-1.5">
          <Save className="h-3.5 w-3.5" />
          {t('agentSpec.saveLabels')}
        </Button>
      )}
    </div>
  );
}
