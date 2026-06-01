import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
  DialogDescription,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Separator } from '@/components/ui/separator';
import SchemaEditor from './SchemaEditor';
import type { JsonSchema } from './SchemaEditor';
import type { McpTool, McpToolAnnotations, McpToolMeta } from '@/types/mcp';

interface ToolEditorDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  tool?: McpTool | null;
  meta?: McpToolMeta | null;
  existingNames: string[];
  onSave: (tool: McpTool, meta: McpToolMeta) => void;
}

const createEmptySchema = (): JsonSchema => ({ type: 'object', properties: {}, required: [] });

const cloneSchema = (schema?: Record<string, unknown> | null): JsonSchema => {
  if (!schema) {
    return createEmptySchema();
  }
  return JSON.parse(JSON.stringify(schema)) as JsonSchema;
};

export default function ToolEditorDialog({
  open,
  onOpenChange,
  tool,
  meta,
  existingNames,
  onSave,
}: ToolEditorDialogProps) {
  const { t } = useTranslation();
  const isEdit = !!tool;

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [enabled, setEnabled] = useState(true);
  const [inputSchema, setInputSchema] = useState<JsonSchema>(createEmptySchema);
  const [outputSchema, setOutputSchema] = useState<JsonSchema>(createEmptySchema);

  // Annotations
  const [annotationsTitle, setAnnotationsTitle] = useState('');
  const [readOnlyHint, setReadOnlyHint] = useState(false);
  const [destructiveHint, setDestructiveHint] = useState(false);
  const [idempotentHint, setIdempotentHint] = useState(false);
  const [openWorldHint, setOpenWorldHint] = useState(false);

  // Advanced - templates as JSON text
  const [requestTemplateText, setRequestTemplateText] = useState('');
  const [responseTemplateText, setResponseTemplateText] = useState('');

  // Meta
  const [transparentAuth, setTransparentAuth] = useState(false);
  const [securitySchemeId, setSecuritySchemeId] = useState('');
  const [clientSecuritySchemeId, setClientSecuritySchemeId] = useState('');

  useEffect(() => {
    if (!open) return;
    if (tool) {
      setName(tool.name);
      setDescription(tool.description || '');
      setInputSchema(cloneSchema(tool.inputSchema));
      setOutputSchema(cloneSchema(tool.outputSchema));
      setAnnotationsTitle(tool.annotations?.title || '');
      setReadOnlyHint(tool.annotations?.readOnlyHint || false);
      setDestructiveHint(tool.annotations?.destructiveHint || false);
      setIdempotentHint(tool.annotations?.idempotentHint || false);
      setOpenWorldHint(tool.annotations?.openWorldHint || false);
    } else {
      setName('');
      setDescription('');
      setInputSchema(createEmptySchema());
      setOutputSchema(createEmptySchema());
      setAnnotationsTitle('');
      setReadOnlyHint(false);
      setDestructiveHint(false);
      setIdempotentHint(false);
      setOpenWorldHint(false);
    }
    if (meta) {
      setEnabled(meta.enabled !== false);
      setTransparentAuth(meta.transparentAuth || false);
      setSecuritySchemeId(meta.securitySchemeId || '');
      setClientSecuritySchemeId(meta.clientSecuritySchemeId || '');
      const tmpl = meta.templates?.['json-go-template'];
      setRequestTemplateText(tmpl?.requestTemplate ? JSON.stringify(tmpl.requestTemplate, null, 2) : '');
      setResponseTemplateText(tmpl?.responseTemplate ? JSON.stringify(tmpl.responseTemplate, null, 2) : '');
    } else {
      setEnabled(true);
      setTransparentAuth(false);
      setSecuritySchemeId('');
      setClientSecuritySchemeId('');
      setRequestTemplateText('');
      setResponseTemplateText('');
    }
  }, [open, tool, meta]);

  const handleSave = () => {
    const trimmedName = name.trim();
    if (!trimmedName) {
      toast.error(t('mcp.toolNameRequired'));
      return;
    }
    if (!isEdit && existingNames.includes(trimmedName)) {
      toast.error(t('mcp.toolNameExists'));
      return;
    }

    const annotations: McpToolAnnotations = {};
    if (annotationsTitle) annotations.title = annotationsTitle;
    if (readOnlyHint) annotations.readOnlyHint = true;
    if (destructiveHint) annotations.destructiveHint = true;
    if (idempotentHint) annotations.idempotentHint = true;
    if (openWorldHint) annotations.openWorldHint = true;

    const newTool: McpTool = {
      name: trimmedName,
      description: description.trim() || undefined,
      inputSchema: inputSchema as unknown as Record<string, unknown>,
      outputSchema:
        Object.keys(outputSchema.properties || {}).length > 0
          ? (outputSchema as unknown as Record<string, unknown>)
          : undefined,
      annotations: Object.keys(annotations).length > 0 ? annotations : undefined,
    };

    // Build meta
    let requestTemplate: Record<string, unknown> | undefined;
    let responseTemplate: Record<string, unknown> | undefined;
    try {
      if (requestTemplateText.trim()) {
        requestTemplate = JSON.parse(requestTemplateText);
      }
    } catch {
      toast.error(`${t('mcp.requestTemplate')}: ${t('mcp.invalidJson')}`);
      return;
    }
    try {
      if (responseTemplateText.trim()) {
        responseTemplate = JSON.parse(responseTemplateText);
      }
    } catch {
      toast.error(`${t('mcp.responseTemplate')}: ${t('mcp.invalidJson')}`);
      return;
    }

    const newMeta: McpToolMeta = { ...(meta || {}), enabled };
    const nextTemplates = { ...(meta?.templates || {}) };
    const jsonTemplate = { ...(nextTemplates['json-go-template'] || {}) };
    if (requestTemplate) {
      jsonTemplate.requestTemplate = requestTemplate;
    } else {
      delete jsonTemplate.requestTemplate;
    }
    if (responseTemplate) {
      jsonTemplate.responseTemplate = responseTemplate;
    } else {
      delete jsonTemplate.responseTemplate;
    }
    if (Object.keys(jsonTemplate).length > 0) {
      nextTemplates['json-go-template'] = jsonTemplate;
    } else {
      delete nextTemplates['json-go-template'];
    }
    if (Object.keys(nextTemplates).length > 0) {
      newMeta.templates = nextTemplates;
    } else {
      delete newMeta.templates;
    }
    if (transparentAuth) newMeta.transparentAuth = true;
    else delete newMeta.transparentAuth;
    if (securitySchemeId) newMeta.securitySchemeId = securitySchemeId;
    else delete newMeta.securitySchemeId;
    if (clientSecuritySchemeId) newMeta.clientSecuritySchemeId = clientSecuritySchemeId;
    else delete newMeta.clientSecuritySchemeId;

    onSave(newTool, newMeta);
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{isEdit ? t('mcp.editTool') : t('mcp.createTool')}</DialogTitle>
          <DialogDescription />
        </DialogHeader>

        <Tabs defaultValue="basic" className="w-full">
          <TabsList className="w-full">
            <TabsTrigger value="basic" className="flex-1 text-sm">
              {t('mcp.toolBasicInfo')}
            </TabsTrigger>
            <TabsTrigger value="input" className="flex-1 text-sm">
              {t('mcp.toolInputSchema')}
            </TabsTrigger>
            <TabsTrigger value="output" className="flex-1 text-sm">
              {t('mcp.toolOutputSchema')}
            </TabsTrigger>
            <TabsTrigger value="annotations" className="flex-1 text-sm">
              {t('mcp.toolAnnotations')}
            </TabsTrigger>
            <TabsTrigger value="advanced" className="flex-1 text-sm">
              {t('mcp.toolAdvancedConfig')}
            </TabsTrigger>
          </TabsList>

          {/* Basic Info */}
          <TabsContent value="basic" className="space-y-4">
            <div className="space-y-2">
              <Label>
                {t('mcp.toolName')} <span className="text-destructive">*</span>
              </Label>
              <Input
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder={t('mcp.toolNamePlaceholder')}
                disabled={isEdit}
              />
            </div>
            <div className="space-y-2">
              <Label>{t('mcp.toolDescription')}</Label>
              <Textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder={t('mcp.toolDescription')}
                rows={3}
              />
            </div>
            <div className="flex items-center gap-3">
              <Switch checked={enabled} onCheckedChange={setEnabled} />
              <Label>{t('mcp.toolEnabled')}</Label>
            </div>
          </TabsContent>

          {/* Input Schema */}
          <TabsContent value="input">
            <SchemaEditor value={inputSchema} onChange={setInputSchema} />
          </TabsContent>

          {/* Output Schema */}
          <TabsContent value="output">
            <SchemaEditor value={outputSchema} onChange={setOutputSchema} />
          </TabsContent>

          {/* Annotations */}
          <TabsContent value="annotations" className="space-y-4">
            <div className="space-y-2">
              <Label>{t('mcp.annotationsTitle')}</Label>
              <Input
                value={annotationsTitle}
                onChange={(e) => setAnnotationsTitle(e.target.value)}
                placeholder={t('mcp.annotationsTitle')}
              />
            </div>
            <Separator />
            {([
              ['readOnlyHint', readOnlyHint, setReadOnlyHint],
              ['destructiveHint', destructiveHint, setDestructiveHint],
              ['idempotentHint', idempotentHint, setIdempotentHint],
              ['openWorldHint', openWorldHint, setOpenWorldHint],
            ] as const).map(([key, val, setter]) => (
              <div key={key} className="flex items-center justify-between">
                <div>
                  <p className="text-sm">{t(`mcp.${key}`)}</p>
                  <p className="text-xs text-muted-foreground">{t(`mcp.${key}Desc`)}</p>
                </div>
                <Switch checked={val} onCheckedChange={setter} />
              </div>
            ))}
          </TabsContent>

          {/* Advanced */}
          <TabsContent value="advanced" className="space-y-4">
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <Label>{t('mcp.passthroughAuth')}</Label>
                <Switch checked={transparentAuth} onCheckedChange={setTransparentAuth} />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1">
                  <Label>{t('mcp.securitySchemeId')}</Label>
                  <Input
                    className="h-8 text-xs"
                    value={securitySchemeId}
                    onChange={(e) => setSecuritySchemeId(e.target.value)}
                    placeholder="securitySchemeId"
                  />
                </div>
                <div className="space-y-1">
                  <Label>Client Security Scheme</Label>
                  <Input
                    className="h-8 text-xs"
                    value={clientSecuritySchemeId}
                    onChange={(e) => setClientSecuritySchemeId(e.target.value)}
                    placeholder="clientSecuritySchemeId"
                  />
                </div>
              </div>
            </div>

            <Separator />

            <div className="space-y-2">
              <Label>{t('mcp.requestTemplate')}</Label>
              <Textarea
                value={requestTemplateText}
                onChange={(e) => setRequestTemplateText(e.target.value)}
                placeholder="{}"
                rows={6}
                className="font-mono text-xs"
              />
            </div>
            <div className="space-y-2">
              <Label>{t('mcp.responseTemplate')}</Label>
              <Textarea
                value={responseTemplateText}
                onChange={(e) => setResponseTemplateText(e.target.value)}
                placeholder="{}"
                rows={6}
                className="font-mono text-xs"
              />
            </div>
          </TabsContent>
        </Tabs>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('common.cancel')}
          </Button>
          <Button onClick={handleSave}>
            {isEdit ? t('common.confirm') : t('mcp.createTool')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
