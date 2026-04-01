import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Plus, Tag } from 'lucide-react';

import { DetailTagChip } from '@/components/ai/DetailTagChip';
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

interface BizTagEditDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  tags: string[];
  placeholder: string;
  emptyText: string;
  onSave: (tags: string[]) => Promise<void>;
}

export function BizTagEditDialog({
  open,
  onOpenChange,
  tags,
  placeholder,
  emptyText,
  onSave,
}: BizTagEditDialogProps) {
  const { t } = useTranslation();
  const [draftTags, setDraftTags] = useState<string[]>([]);
  const [inputValue, setInputValue] = useState('');
  const [saving, setSaving] = useState(false);

  const normalizedDraftTags = useMemo(
    () => Array.from(new Set(draftTags.map((tag) => tag.trim()).filter(Boolean))),
    [draftTags]
  );

  useEffect(() => {
    if (!open) {
      return;
    }
    setDraftTags(tags);
    setInputValue('');
  }, [open, tags]);

  const handleOpenChange = (nextOpen: boolean) => {
    onOpenChange(nextOpen);
  };

  const handleAddTag = () => {
    const nextTag = inputValue.trim();
    if (!nextTag || normalizedDraftTags.includes(nextTag)) {
      setInputValue('');
      return;
    }
    setDraftTags((prev) => [...prev, nextTag]);
    setInputValue('');
  };

  const handleDeleteTag = (tag: string) => {
    setDraftTags((prev) => prev.filter((item) => item !== tag));
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      await onSave(normalizedDraftTags);
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
            {t('common.bizTagEditor.title')}
          </DialogTitle>
          <DialogDescription>
            {t('common.bizTagEditor.description')}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div className="flex items-center gap-2">
            <Input
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault();
                  handleAddTag();
                }
              }}
              placeholder={placeholder}
              className="h-9"
            />
            <Button
              variant="outline"
              size="icon"
              className="h-9 w-9 shrink-0"
              onClick={handleAddTag}
            >
              <Plus className="h-3.5 w-3.5" />
            </Button>
          </div>

          {normalizedDraftTags.length > 0 ? (
            <div className="flex flex-wrap gap-1.5">
              {normalizedDraftTags.map((tag) => (
                <DetailTagChip
                  key={tag}
                  label={tag}
                  onRemove={() => handleDeleteTag(tag)}
                />
              ))}
            </div>
          ) : (
            <p className="text-xs text-muted-foreground">{emptyText}</p>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={saving}>
            {t('common.cancel')}
          </Button>
          <Button onClick={handleSave} disabled={saving}>
            {t('common.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
