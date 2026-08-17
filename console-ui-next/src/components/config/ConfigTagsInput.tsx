import { useMemo, useState, type KeyboardEvent } from 'react';
import { X } from 'lucide-react';

import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { parseConfigTags, serializeConfigTags } from './config-tags';

interface ConfigTagsInputProps {
  id?: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
}

export function ConfigTagsInput({
  id,
  value,
  onChange,
  placeholder,
}: ConfigTagsInputProps) {
  const [draft, setDraft] = useState('');
  const tags = useMemo(() => parseConfigTags(value), [value]);

  const addTags = (candidate: string) => {
    const nextTags = parseConfigTags(candidate);
    if (nextTags.length > 0) {
      onChange(serializeConfigTags([...tags, ...nextTags]));
    }
    setDraft('');
  };

  const removeTag = (tagToRemove: string) => {
    onChange(serializeConfigTags(tags.filter((tag) => tag !== tagToRemove)));
  };

  const handleDraftChange = (nextDraft: string) => {
    const segments = nextDraft.split(/[,，]/);
    if (segments.length === 1) {
      setDraft(nextDraft);
      return;
    }
    addTags(segments.slice(0, -1).join(','));
    setDraft(segments[segments.length - 1] || '');
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter' && draft.trim()) {
      event.preventDefault();
      addTags(draft);
      return;
    }
    if (event.key === 'Backspace' && !draft && tags.length > 0) {
      removeTag(tags[tags.length - 1]);
    }
  };

  return (
    <div className="flex min-h-10 flex-wrap items-center gap-1.5 rounded-md border border-input bg-background px-2 py-1 shadow-sm focus-within:ring-2 focus-within:ring-ring focus-within:ring-offset-2">
      {tags.map((tag) => (
        <Badge key={tag} variant="secondary" className="gap-1 py-1 pl-2.5 pr-1 text-sm font-normal">
          <span>{tag}</span>
          <button
            type="button"
            className="rounded-sm p-0.5 text-muted-foreground hover:bg-muted hover:text-foreground"
            aria-label={`${tag} remove`}
            onClick={() => removeTag(tag)}
          >
            <X className="h-3.5 w-3.5" />
          </button>
        </Badge>
      ))}
      <Input
        id={id}
        value={draft}
        onChange={(event) => handleDraftChange(event.target.value)}
        onKeyDown={handleKeyDown}
        onBlur={() => addTags(draft)}
        placeholder={tags.length === 0 ? placeholder : undefined}
        className="h-7 min-w-40 flex-1 border-0 bg-transparent px-1 shadow-none focus-visible:ring-0 focus-visible:ring-offset-0"
      />
    </div>
  );
}
