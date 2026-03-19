import { useTranslation } from 'react-i18next';
import { Pencil, Trash2, CheckCircle, XCircle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Separator } from '@/components/ui/separator';
import SchemaEditor from './SchemaEditor';
import type { JsonSchema } from './SchemaEditor';
import type { McpTool, McpToolMeta } from '@/types/mcp';

interface ToolDetailProps {
  tool: McpTool;
  meta?: McpToolMeta;
  onEdit: () => void;
  onDelete: () => void;
}

const EMPTY_SCHEMA: JsonSchema = { type: 'object', properties: {}, required: [] };

export default function ToolDetail({ tool, meta, onEdit, onDelete }: ToolDetailProps) {
  const { t } = useTranslation();
  const isEnabled = meta?.enabled !== false;

  const inputSchema: JsonSchema = (tool.inputSchema as unknown as JsonSchema) || EMPTY_SCHEMA;
  const outputSchema: JsonSchema = (tool.outputSchema as unknown as JsonSchema) || EMPTY_SCHEMA;
  const inputParamCount = Object.keys(inputSchema.properties || {}).length;
  const outputParamCount = Object.keys(outputSchema.properties || {}).length;

  const annotations = tool.annotations;
  const hasAnnotations =
    annotations &&
    (annotations.readOnlyHint !== undefined ||
      annotations.destructiveHint !== undefined ||
      annotations.idempotentHint !== undefined ||
      annotations.openWorldHint !== undefined);

  const tmpl = meta?.templates?.['json-go-template'];
  const hasTemplates = tmpl && (tmpl.requestTemplate || tmpl.responseTemplate);

  return (
    <div className="space-y-5 p-4 overflow-hidden">
      {/* Header */}
      <div className="flex items-start justify-between gap-2">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <h3 className="text-lg font-semibold truncate">{tool.name}</h3>
            <Badge variant={isEnabled ? 'default' : 'secondary'} className="text-xs h-5 px-1.5 shrink-0">
              {isEnabled ? 'ON' : 'OFF'}
            </Badge>
          </div>
          {tool.description && (
            <p className="text-sm text-muted-foreground mt-1">{tool.description}</p>
          )}
        </div>
        <div className="flex items-center gap-1.5 shrink-0">
          <Button variant="outline" size="sm" className="h-8" onClick={onEdit}>
            <Pencil className="h-3.5 w-3.5 mr-1" />
            {t('common.edit')}
          </Button>
          <Button
            variant="ghost"
            size="icon"
            className="h-8 w-8 text-destructive hover:text-destructive hover:bg-destructive/10"
            onClick={onDelete}
          >
            <Trash2 className="h-4 w-4" />
          </Button>
        </div>
      </div>

      <Separator />

      {/* Input Schema */}
      <div>
        <h4 className="text-sm font-medium mb-2">
          {t('mcp.toolInputSchema')}
          {inputParamCount > 0 && (
            <span className="text-muted-foreground font-normal ml-1">
              ({t('mcp.toolParams', { count: inputParamCount })})
            </span>
          )}
        </h4>
        {inputParamCount > 0 ? (
          <SchemaEditor value={inputSchema} onChange={() => {}} readOnly />
        ) : (
          <p className="text-sm text-muted-foreground italic">{t('mcp.noTools')}</p>
        )}
      </div>

      {/* Output Schema */}
      {outputParamCount > 0 && (
        <div>
          <h4 className="text-sm font-medium mb-2">
            {t('mcp.toolOutputSchema')}
            <span className="text-muted-foreground font-normal ml-1">
              ({t('mcp.toolParams', { count: outputParamCount })})
            </span>
          </h4>
          <SchemaEditor value={outputSchema} onChange={() => {}} readOnly />
        </div>
      )}

      {/* Annotations */}
      {hasAnnotations && (
        <>
          <Separator />
          <div>
            <h4 className="text-sm font-medium mb-2">{t('mcp.toolAnnotations')}</h4>
            <div className="grid grid-cols-2 gap-2">
              {([
                ['readOnlyHint', annotations!.readOnlyHint],
                ['destructiveHint', annotations!.destructiveHint],
                ['idempotentHint', annotations!.idempotentHint],
                ['openWorldHint', annotations!.openWorldHint],
              ] as const)
                .filter(([, val]) => val !== undefined)
                .map(([key, val]) => (
                  <div key={key} className="flex items-center gap-1.5 text-sm">
                    {val ? (
                      <CheckCircle className="h-3.5 w-3.5 text-green-500 shrink-0" />
                    ) : (
                      <XCircle className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
                    )}
                    <span className="text-muted-foreground">{t(`mcp.${key}`)}</span>
                  </div>
                ))}
            </div>
          </div>
        </>
      )}

      {/* Templates preview */}
      {hasTemplates && (
        <>
          <Separator />
          <div>
            <h4 className="text-sm font-medium mb-2">{t('mcp.toolAdvancedConfig')}</h4>
            {tmpl!.requestTemplate && (
              <div className="mb-2">
                <span className="text-xs text-muted-foreground">{t('mcp.requestTemplate')}</span>
                <pre className="text-xs bg-muted/50 rounded p-2 mt-1 overflow-x-auto max-h-40 max-w-full break-all whitespace-pre-wrap">
                  {JSON.stringify(tmpl!.requestTemplate, null, 2)}
                </pre>
              </div>
            )}
            {tmpl!.responseTemplate && Object.keys(tmpl!.responseTemplate).length > 0 && (
              <div>
                <span className="text-xs text-muted-foreground">{t('mcp.responseTemplate')}</span>
                <pre className="text-xs bg-muted/50 rounded p-2 mt-1 overflow-x-auto max-h-40 max-w-full break-all whitespace-pre-wrap">
                  {JSON.stringify(tmpl!.responseTemplate, null, 2)}
                </pre>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}
