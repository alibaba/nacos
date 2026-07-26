import { useState, useCallback, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { ChevronRight, Plus, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Checkbox } from '@/components/ui/checkbox';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@/components/ui/collapsible';
import { cn } from '@/lib/utils';

const SCHEMA_TYPES = ['string', 'number', 'integer', 'boolean', 'array', 'object'] as const;
type SchemaType = (typeof SCHEMA_TYPES)[number];
const MAX_DEPTH = 5;

export interface JsonSchemaProperty {
  type?: string;
  description?: string;
  properties?: Record<string, JsonSchemaProperty>;
  items?: JsonSchemaProperty;
  required?: string[];
  enum?: unknown[];
  [key: string]: unknown;
}

export interface JsonSchema {
  type: 'object';
  properties?: Record<string, JsonSchemaProperty>;
  required?: string[];
}

interface SchemaEditorProps {
  value: JsonSchema;
  onChange: (schema: JsonSchema) => void;
  readOnly?: boolean;
}

interface PropertyRowProps {
  name: string;
  schema: JsonSchemaProperty;
  required: boolean;
  depth: number;
  readOnly: boolean;
  onNameChange: (oldName: string, newName: string) => boolean;
  onSchemaChange: (name: string, schema: JsonSchemaProperty) => void;
  onRequiredChange: (name: string, required: boolean) => void;
  onDelete: (name: string) => void;
}

function PropertyRow({
  name,
  schema,
  required,
  depth,
  readOnly,
  onNameChange,
  onSchemaChange,
  onRequiredChange,
  onDelete,
}: PropertyRowProps) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [draftName, setDraftName] = useState(name);
  const skipCommitRef = useRef(false);
  const type = (schema.type || 'string') as SchemaType;
  const hasChildren = type === 'object' || type === 'array';
  const canExpand = hasChildren && depth < MAX_DEPTH;

  const childProperties = type === 'object' ? schema.properties : type === 'array' ? schema.items?.properties : undefined;
  const childRequired = type === 'object' ? schema.required : type === 'array' ? schema.items?.required : undefined;

  useEffect(() => {
    setDraftName(name);
  }, [name]);

  const commitName = useCallback(() => {
    if (skipCommitRef.current) {
      skipCommitRef.current = false;
      setDraftName(name);
      return;
    }
    const nextName = draftName.trim();
    if (nextName === name) {
      setDraftName(name);
      return;
    }
    if (!nextName || !onNameChange(name, nextName)) {
      setDraftName(name);
    }
  }, [draftName, name, onNameChange]);

  const handleTypeChange = (newType: string) => {
    const updated: JsonSchemaProperty = { ...schema, type: newType };
    if (newType === 'object') {
      updated.properties = updated.properties || {};
      delete updated.items;
    } else if (newType === 'array') {
      updated.items = updated.items || { type: 'object', properties: {} };
      delete updated.properties;
      delete updated.required;
    } else {
      delete updated.properties;
      delete updated.items;
      delete updated.required;
    }
    onSchemaChange(name, updated);
  };

  const handleChildAdd = () => {
    const newName = `param_${Date.now()}`;
    if (type === 'object') {
      const props = { ...(schema.properties || {}) };
      props[newName] = { type: 'string', description: '' };
      onSchemaChange(name, { ...schema, properties: props });
    } else if (type === 'array') {
      const items = { ...(schema.items || { type: 'object' }) };
      const props = { ...(items.properties || {}) };
      props[newName] = { type: 'string', description: '' };
      onSchemaChange(name, { ...schema, items: { ...items, properties: props } });
    }
  };

  const handleChildNameChange = (oldN: string, newN: string) => {
    if (oldN === newN) return true;
    const target = type === 'object' ? schema : schema.items;
    if (!target?.properties) return false;
    if (!newN || (newN !== oldN && Object.prototype.hasOwnProperty.call(target.properties, newN))) {
      return false;
    }
    const props: Record<string, JsonSchemaProperty> = {};
    const reqArr = [...(target.required || [])];
    for (const key of Object.keys(target.properties)) {
      if (key === oldN) {
        props[newN] = target.properties[key];
        const reqIdx = reqArr.indexOf(oldN);
        if (reqIdx >= 0) reqArr[reqIdx] = newN;
      } else {
        props[key] = target.properties[key];
      }
    }
    if (type === 'object') {
      onSchemaChange(name, { ...schema, properties: props, required: reqArr });
    } else {
      onSchemaChange(name, { ...schema, items: { ...schema.items, properties: props, required: reqArr } });
    }
    return true;
  };

  const handleChildSchemaChange = (childName: string, childSchema: JsonSchemaProperty) => {
    if (type === 'object') {
      const props = { ...(schema.properties || {}) };
      props[childName] = childSchema;
      onSchemaChange(name, { ...schema, properties: props });
    } else if (type === 'array') {
      const items = { ...(schema.items || { type: 'object' }) };
      const props = { ...(items.properties || {}) };
      props[childName] = childSchema;
      onSchemaChange(name, { ...schema, items: { ...items, properties: props } });
    }
  };

  const handleChildRequiredChange = (childName: string, isReq: boolean) => {
    const target = type === 'object' ? schema : schema.items;
    const reqArr = [...(target?.required || [])];
    if (isReq && !reqArr.includes(childName)) {
      reqArr.push(childName);
    } else if (!isReq) {
      const idx = reqArr.indexOf(childName);
      if (idx >= 0) reqArr.splice(idx, 1);
    }
    if (type === 'object') {
      onSchemaChange(name, { ...schema, required: reqArr });
    } else {
      onSchemaChange(name, { ...schema, items: { ...schema.items, required: reqArr } });
    }
  };

  const handleChildDelete = (childName: string) => {
    if (type === 'object') {
      const props = { ...(schema.properties || {}) };
      delete props[childName];
      const reqArr = (schema.required || []).filter((r) => r !== childName);
      onSchemaChange(name, { ...schema, properties: props, required: reqArr });
    } else if (type === 'array') {
      const items = { ...(schema.items || { type: 'object' }) };
      const props = { ...(items.properties || {}) };
      delete props[childName];
      const reqArr = (items.required || []).filter((r) => r !== childName);
      onSchemaChange(name, { ...schema, items: { ...items, properties: props, required: reqArr } });
    }
  };

  const childEntries = childProperties ? Object.entries(childProperties) : [];

  return (
    <Collapsible open={open} onOpenChange={canExpand ? setOpen : undefined}>
      <div
        className={cn(
          'flex items-center gap-2 py-1.5 px-1 rounded-sm group transition-colors hover:bg-muted/30',
          depth > 0 && 'ml-4'
        )}
      >
        {/* Expand trigger */}
        <div className="w-5 shrink-0 flex justify-center">
          {canExpand ? (
            <CollapsibleTrigger asChild>
              <button className="p-0.5 rounded hover:bg-muted">
                <ChevronRight
                  className={cn('h-3.5 w-3.5 transition-transform', open && 'rotate-90')}
                />
              </button>
            </CollapsibleTrigger>
          ) : null}
        </div>

        {/* Name */}
        <Input
          className="h-8 text-sm w-36 shrink-0"
          value={draftName}
          onChange={(e) => setDraftName(e.target.value)}
          onBlur={commitName}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.currentTarget.blur();
            } else if (e.key === 'Escape') {
              skipCommitRef.current = true;
              setDraftName(name);
              e.currentTarget.blur();
            }
          }}
          disabled={readOnly}
          placeholder="name"
        />

        {/* Type */}
        <Select value={type} onValueChange={handleTypeChange} disabled={readOnly}>
          <SelectTrigger className="h-8 text-sm w-28 shrink-0">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {SCHEMA_TYPES.map((st) => (
              <SelectItem key={st} value={st} className="text-sm">
                {t(`mcp.type${st.charAt(0).toUpperCase() + st.slice(1)}`)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        {/* Required */}
        <Checkbox
          checked={required}
          onCheckedChange={(v) => onRequiredChange(name, !!v)}
          disabled={readOnly}
          className="shrink-0"
        />
        <span className="text-xs text-muted-foreground shrink-0 select-none">req</span>

        {/* Description */}
        <Input
          className="h-8 text-sm flex-1 min-w-0"
          value={schema.description || ''}
          onChange={(e) => onSchemaChange(name, { ...schema, description: e.target.value })}
          disabled={readOnly}
          placeholder="description"
        />

        {/* Delete */}
        {!readOnly && (
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7 text-destructive opacity-0 group-hover:opacity-100 shrink-0"
            onClick={() => onDelete(name)}
          >
            <Trash2 className="h-3.5 w-3.5" />
          </Button>
        )}
      </div>

      {/* Children */}
      {canExpand && (
        <CollapsibleContent>
          <div className={cn('border-l border-muted', depth > 0 ? 'ml-6' : 'ml-4')}>
            {childEntries.map(([childName, childSchema]) => (
              <PropertyRow
                key={childName}
                name={childName}
                schema={childSchema}
                required={childRequired?.includes(childName) || false}
                depth={depth + 1}
                readOnly={readOnly}
                onNameChange={handleChildNameChange}
                onSchemaChange={handleChildSchemaChange}
                onRequiredChange={handleChildRequiredChange}
                onDelete={handleChildDelete}
              />
            ))}
            {!readOnly && (
              <Button
                variant="ghost"
                size="sm"
                className="ml-9 h-7 text-sm mt-0.5"
                onClick={handleChildAdd}
              >
                <Plus className="h-3 w-3 mr-1" />
                {t('mcp.addProperty')}
              </Button>
            )}
          </div>
        </CollapsibleContent>
      )}
    </Collapsible>
  );
}

export default function SchemaEditor({ value, onChange, readOnly = false }: SchemaEditorProps) {
  const { t } = useTranslation();
  const properties = value.properties || {};
  const requiredArr = value.required || [];

  const handleAdd = useCallback(() => {
    const newName = `param_${Date.now()}`;
    const props = { ...properties };
    props[newName] = { type: 'string', description: '' };
    onChange({ ...value, properties: props });
  }, [properties, value, onChange]);

  const handleNameChange = useCallback(
    (oldName: string, newName: string) => {
      if (oldName === newName) return true;
      if (!newName || (newName !== oldName && Object.prototype.hasOwnProperty.call(properties, newName))) {
        return false;
      }
      const props: Record<string, JsonSchemaProperty> = {};
      const reqArr = [...requiredArr];
      for (const key of Object.keys(properties)) {
        if (key === oldName) {
          props[newName] = properties[key];
          const reqIdx = reqArr.indexOf(oldName);
          if (reqIdx >= 0) reqArr[reqIdx] = newName;
        } else {
          props[key] = properties[key];
        }
      }
      onChange({ ...value, properties: props, required: reqArr });
      return true;
    },
    [properties, requiredArr, value, onChange]
  );

  const handleSchemaChange = useCallback(
    (name: string, schema: JsonSchemaProperty) => {
      const props = { ...properties };
      props[name] = schema;
      onChange({ ...value, properties: props });
    },
    [properties, value, onChange]
  );

  const handleRequiredChange = useCallback(
    (name: string, isReq: boolean) => {
      const reqArr = [...requiredArr];
      if (isReq && !reqArr.includes(name)) {
        reqArr.push(name);
      } else if (!isReq) {
        const idx = reqArr.indexOf(name);
        if (idx >= 0) reqArr.splice(idx, 1);
      }
      onChange({ ...value, required: reqArr });
    },
    [requiredArr, value, onChange]
  );

  const handleDelete = useCallback(
    (name: string) => {
      const props = { ...properties };
      delete props[name];
      const reqArr = requiredArr.filter((r) => r !== name);
      onChange({ ...value, properties: props, required: reqArr });
    },
    [properties, requiredArr, value, onChange]
  );

  const entries = Object.entries(properties);

  return (
    <div className="space-y-0.5">
      {/* Header */}
      <div className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground px-1 pb-1 border-b border-border/40 mb-1">
        <div className="w-5 shrink-0" />
        <div className="w-36 shrink-0">Name</div>
        <div className="w-28 shrink-0">Type</div>
        <div className="w-12 shrink-0 text-center">Req</div>
        <div className="flex-1">Description</div>
      </div>

      {entries.map(([name, schema]) => (
        <PropertyRow
          key={name}
          name={name}
          schema={schema}
          required={requiredArr.includes(name)}
          depth={0}
          readOnly={readOnly}
          onNameChange={handleNameChange}
          onSchemaChange={handleSchemaChange}
          onRequiredChange={handleRequiredChange}
          onDelete={handleDelete}
        />
      ))}

      {!readOnly && (
        <Button variant="outline" size="sm" className="h-8 text-sm mt-1" onClick={handleAdd}>
          <Plus className="h-3 w-3 mr-1" />
          {t('mcp.addParameter')}
        </Button>
      )}
    </div>
  );
}
