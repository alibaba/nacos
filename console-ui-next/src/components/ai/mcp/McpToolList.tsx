import { useState, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import {
  ChevronDown,
  ChevronRight,
  Search,
  CheckCircle,
  XCircle,
  Braces,
  FileOutput,
  ShieldCheck,
  ArrowRightLeft,
  Send,
  Reply,
  Wrench,
} from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { cn } from '@/lib/utils';
import type { McpTool, McpToolMeta } from '@/types/mcp';

interface McpToolListProps {
  tools: McpTool[];
  toolsMeta?: Record<string, McpToolMeta>;
  className?: string;
}

// ===== Schema Tree Types =====

interface SchemaProperty {
  type?: string;
  description?: string;
  default?: unknown;
  enum?: unknown[];
  format?: string;
  properties?: Record<string, SchemaProperty>;
  items?: SchemaProperty;
  required?: string[];
}

// ===== Style constants =====

const TYPE_STYLES: Record<string, { badge: string; dot: string }> = {
  string: {
    badge: 'bg-blue-50 text-blue-600 ring-blue-500/20 dark:bg-blue-950/40 dark:text-blue-400 dark:ring-blue-400/20',
    dot: 'bg-blue-500',
  },
  number: {
    badge: 'bg-amber-50 text-amber-600 ring-amber-500/20 dark:bg-amber-950/40 dark:text-amber-400 dark:ring-amber-400/20',
    dot: 'bg-amber-500',
  },
  integer: {
    badge: 'bg-amber-50 text-amber-600 ring-amber-500/20 dark:bg-amber-950/40 dark:text-amber-400 dark:ring-amber-400/20',
    dot: 'bg-amber-500',
  },
  boolean: {
    badge: 'bg-emerald-50 text-emerald-600 ring-emerald-500/20 dark:bg-emerald-950/40 dark:text-emerald-400 dark:ring-emerald-400/20',
    dot: 'bg-emerald-500',
  },
  array: {
    badge: 'bg-purple-50 text-purple-600 ring-purple-500/20 dark:bg-purple-950/40 dark:text-purple-400 dark:ring-purple-400/20',
    dot: 'bg-purple-500',
  },
  object: {
    badge: 'bg-slate-50 text-slate-600 ring-slate-500/20 dark:bg-slate-900/40 dark:text-slate-400 dark:ring-slate-400/20',
    dot: 'bg-slate-500',
  },
};

const HTTP_METHOD_STYLES: Record<string, string> = {
  get: 'bg-emerald-500 text-white',
  post: 'bg-blue-500 text-white',
  put: 'bg-amber-500 text-white',
  delete: 'bg-red-500 text-white',
  patch: 'bg-orange-500 text-white',
};

// ===== Schema Tree Node (recursive) =====

function SchemaTreeNode({
  name,
  schema,
  isRequired,
  depth,
  isLast,
}: {
  name: string;
  schema: SchemaProperty;
  isRequired: boolean;
  depth: number;
  isLast: boolean;
}) {
  const [expanded, setExpanded] = useState(depth < 2);
  const type = schema.type || 'string';

  const childProperties =
    type === 'object'
      ? schema.properties
      : type === 'array' && schema.items?.type === 'object'
        ? schema.items?.properties
        : undefined;
  const childRequired =
    type === 'object'
      ? schema.required
      : type === 'array' && schema.items?.type === 'object'
        ? schema.items?.required
        : undefined;
  const hasChildren = childProperties && Object.keys(childProperties).length > 0;

  const arrayItemType =
    type === 'array' && schema.items && schema.items.type !== 'object'
      ? schema.items.type
      : undefined;

  const typeStyle = TYPE_STYLES[type] || {
    badge: 'bg-gray-50 text-gray-600 ring-gray-500/20 dark:bg-gray-900/40 dark:text-gray-400',
    dot: 'bg-gray-400',
  };

  const childEntries = childProperties ? Object.entries(childProperties) : [];

  return (
    <div className="relative">
      {/* Vertical connector line from parent */}
      {depth > 0 && !isLast && (
        <div className="absolute left-0 top-0 bottom-0 w-px bg-border/60" />
      )}
      {depth > 0 && isLast && (
        <div className="absolute left-0 top-0 h-[18px] w-px bg-border/60" />
      )}
      {/* Horizontal connector line */}
      {depth > 0 && (
        <div className="absolute left-0 top-[18px] w-3 h-px bg-border/60" />
      )}

      {/* Node row */}
      <div
        className={cn(
          'group flex items-center gap-1.5 py-[5px] rounded-md transition-colors hover:bg-accent/50',
          depth > 0 ? 'ml-3 pl-3' : 'pl-1',
        )}
      >
        {/* Expand toggle */}
        <div className="w-4 shrink-0 flex justify-center">
          {hasChildren ? (
            <button
              className="flex items-center justify-center h-4 w-4 rounded transition-colors hover:bg-muted"
              onClick={() => setExpanded(!expanded)}
            >
              {expanded ? (
                <ChevronDown className="h-3 w-3 text-muted-foreground" />
              ) : (
                <ChevronRight className="h-3 w-3 text-muted-foreground" />
              )}
            </button>
          ) : (
            <span className={cn('h-1.5 w-1.5 rounded-full', typeStyle.dot)} />
          )}
        </div>

        {/* Name */}
        <span className="text-[13px] font-mono font-semibold text-foreground shrink-0">
          {name}
        </span>

        {/* Type badge */}
        <span
          className={cn(
            'text-[10px] font-medium px-1.5 py-px rounded ring-1 ring-inset shrink-0 leading-4',
            typeStyle.badge,
          )}
        >
          {type}
          {arrayItemType && `<${arrayItemType}>`}
        </span>

        {/* Required badge */}
        {isRequired && (
          <span className="text-[10px] font-semibold text-red-500 dark:text-red-400 shrink-0">
            *
          </span>
        )}

        {/* Inline metadata */}
        <span className="flex items-center gap-1.5 text-[11px] text-muted-foreground min-w-0 truncate">
          {schema.default !== undefined && (
            <span className="shrink-0 font-mono opacity-70">
              = {JSON.stringify(schema.default)}
            </span>
          )}
          {schema.enum && (
            <span className="shrink-0 opacity-70">
              [{schema.enum.join(' | ')}]
            </span>
          )}
          {schema.format && (
            <span className="shrink-0 opacity-60 italic">{schema.format}</span>
          )}
          {schema.description && (
            <span className="truncate opacity-80" title={schema.description}>
              {schema.description}
            </span>
          )}
        </span>
      </div>

      {/* Children (object properties or array item properties) */}
      {expanded && hasChildren && (
        <div className={cn('relative', depth > 0 ? 'ml-6' : 'ml-3')}>
          {childEntries.map(([childName, childSchema], idx) => (
            <SchemaTreeNode
              key={childName}
              name={childName}
              schema={childSchema}
              isRequired={childRequired?.includes(childName) || false}
              depth={depth + 1}
              isLast={idx === childEntries.length - 1}
            />
          ))}
        </div>
      )}

      {/* Array items that are primitives */}
      {expanded && type === 'array' && schema.items && !hasChildren && schema.items.type && (
        <div className={cn('relative', depth > 0 ? 'ml-6' : 'ml-3')}>
          <div className="relative">
            <div className="absolute left-0 top-0 h-[18px] w-px bg-border/60" />
            <div className="absolute left-0 top-[18px] w-3 h-px bg-border/60" />
            <div className="ml-3 pl-3 py-[5px] flex items-center gap-1.5">
              <span className={cn('h-1.5 w-1.5 rounded-full', TYPE_STYLES[schema.items.type]?.dot || 'bg-gray-400')} />
              <span className="text-[11px] text-muted-foreground font-mono">items</span>
              <span className={cn(
                'text-[10px] font-medium px-1.5 py-px rounded ring-1 ring-inset leading-4',
                TYPE_STYLES[schema.items.type]?.badge || 'bg-gray-50 text-gray-600 ring-gray-500/20',
              )}>
                {schema.items.type}
              </span>
              {schema.items.description && (
                <span className="text-[11px] text-muted-foreground truncate opacity-80">
                  {schema.items.description}
                </span>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// ===== Schema Section =====

function SchemaSection({
  title,
  icon,
  properties,
  required,
}: {
  title: string;
  icon: React.ReactNode;
  properties: Record<string, SchemaProperty>;
  required: string[];
}) {
  const { t } = useTranslation();
  const count = Object.keys(properties).length;
  const entries = Object.entries(properties);

  return (
    <div>
      <div className="flex items-center gap-2 mb-2.5">
        {icon}
        <h4 className="text-sm font-semibold">{title}</h4>
        {count > 0 && (
          <span className="text-[11px] text-muted-foreground bg-muted/60 rounded-full px-2 py-0.5">
            {t('mcp.toolParams', { count })}
          </span>
        )}
      </div>
      {count > 0 ? (
        <div className="rounded-lg border bg-card p-3">
          {entries.map(([name, schema], idx) => (
            <SchemaTreeNode
              key={name}
              name={name}
              schema={schema}
              isRequired={required.includes(name)}
              depth={0}
              isLast={idx === entries.length - 1}
            />
          ))}
        </div>
      ) : (
        <div className="rounded-lg border border-dashed p-4 text-center">
          <p className="text-sm text-muted-foreground">{t('mcp.noParameters')}</p>
        </div>
      )}
    </div>
  );
}

// ===== Request Template Section =====

function RequestTemplateSection({ data }: { data: Record<string, unknown> }) {
  const { t } = useTranslation();
  const method = data.method as string | undefined;
  const url = data.url as string | undefined;
  const headers = data.headers as Record<string, unknown> | undefined;
  const body = data.body;
  const security = data.security as Record<string, unknown> | undefined;

  return (
    <div className="overflow-hidden">
      <div className="flex items-center gap-2 mb-2.5">
        <Send className="h-3.5 w-3.5 text-blue-500" />
        <h5 className="text-sm font-semibold">{t('mcp.requestTemplate')}</h5>
      </div>
      <div className="rounded-lg border border-blue-200/60 dark:border-blue-800/40 bg-blue-50/30 dark:bg-blue-950/10 overflow-hidden">
        {/* Method + URL bar */}
        {(method || url) && (
          <div className="flex items-center gap-2 px-3 py-2.5 border-b border-blue-200/40 dark:border-blue-800/30 bg-blue-50/50 dark:bg-blue-950/20">
            {method && (
              <span className={cn(
                'text-[10px] font-bold uppercase px-2 py-0.5 rounded-md shrink-0 tracking-wide',
                HTTP_METHOD_STYLES[String(method).toLowerCase()] || 'bg-gray-500 text-white',
              )}>
                {method}
              </span>
            )}
            {url && (
              <code className="text-xs font-mono text-blue-700 dark:text-blue-300 break-all">
                {url}
              </code>
            )}
          </div>
        )}

        <div className="p-3 space-y-2.5">
          {security && (
            <div className="flex items-center gap-2 text-xs">
              <span className="text-muted-foreground shrink-0">Security:</span>
              <code className="font-mono text-foreground/80">
                {(security.id as string) || JSON.stringify(security)}
              </code>
            </div>
          )}

          {headers && Object.keys(headers).length > 0 && (
            <div>
              <span className="text-[11px] font-medium text-muted-foreground uppercase tracking-wider">Headers</span>
              <div className="mt-1.5 rounded-md border bg-background/80 divide-y divide-border/50">
                {Object.entries(headers).map(([key, value]) => (
                  <div key={key} className="flex items-baseline gap-2 px-2.5 py-1.5 text-xs">
                    <span className="font-mono font-medium text-foreground shrink-0">{key}</span>
                    <span className="text-muted-foreground">:</span>
                    <span className="font-mono text-muted-foreground break-all">
                      {typeof value === 'object' ? JSON.stringify(value) : String(value)}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {body !== undefined && body !== null && (
            <div className="overflow-hidden">
              <span className="text-[11px] font-medium text-muted-foreground uppercase tracking-wider">Body</span>
              <pre className="mt-1.5 text-xs font-mono bg-background/80 rounded-md border p-2.5 overflow-x-auto max-w-full break-all whitespace-pre-wrap">
                {typeof body === 'object' ? JSON.stringify(body, null, 2) : String(body)}
              </pre>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// ===== Response Template Section =====

function ResponseTemplateSection({ data }: { data: Record<string, unknown> }) {
  const { t } = useTranslation();
  const body = data.body;
  const prependBody = data.prependBody;
  const appendBody = data.appendBody;
  const knownFields = ['body', 'prependBody', 'appendBody'];
  const otherFields = Object.entries(data).filter(([key]) => !knownFields.includes(key));

  const renderCodeBlock = (label: string, content: unknown) => (
    <div className="overflow-hidden">
      <span className="text-[11px] font-medium text-muted-foreground uppercase tracking-wider">{label}</span>
      <pre className="mt-1.5 text-xs font-mono bg-background/80 rounded-md border p-2.5 overflow-x-auto max-w-full break-all whitespace-pre-wrap">
        {typeof content === 'object' ? JSON.stringify(content, null, 2) : String(content)}
      </pre>
    </div>
  );

  return (
    <div className="overflow-hidden">
      <div className="flex items-center gap-2 mb-2.5">
        <Reply className="h-3.5 w-3.5 text-amber-500" />
        <h5 className="text-sm font-semibold">{t('mcp.responseTemplate')}</h5>
      </div>
      <div className="rounded-lg border border-amber-200/60 dark:border-amber-800/40 bg-amber-50/30 dark:bg-amber-950/10 p-3 space-y-2.5 overflow-hidden">
        {body !== undefined && body !== null && renderCodeBlock('body', body)}
        {prependBody !== undefined && prependBody !== null && renderCodeBlock('prependBody', prependBody)}
        {appendBody !== undefined && appendBody !== null && renderCodeBlock('appendBody', appendBody)}
        {otherFields.map(([key, value]) => (
          <div key={key}>{renderCodeBlock(key, value)}</div>
        ))}
      </div>
    </div>
  );
}

// ===== Main Component =====

export function McpToolList({ tools, toolsMeta, className }: McpToolListProps) {
  const { t } = useTranslation();
  const [activeToolIndex, setActiveToolIndex] = useState(0);
  const [searchKeyword, setSearchKeyword] = useState('');

  const filteredTools = useMemo(() => {
    if (!searchKeyword) return tools.map((tool, i) => ({ tool, originalIndex: i }));
    return tools
      .map((tool, i) => ({ tool, originalIndex: i }))
      .filter(({ tool }) => tool.name.toLowerCase().includes(searchKeyword.toLowerCase()));
  }, [tools, searchKeyword]);

  if (!tools || tools.length === 0) {
    return (
      <p className="text-sm text-muted-foreground py-4 text-center">{t('mcp.noTools')}</p>
    );
  }

  const activeTool = tools[activeToolIndex];
  const activeMeta = activeTool ? toolsMeta?.[activeTool.name] : undefined;
  const isEnabled = activeMeta?.enabled !== false;

  const inputSchema = activeTool?.inputSchema as SchemaProperty | undefined;
  const outputSchema = activeTool?.outputSchema as SchemaProperty | undefined;
  const inputProperties: Record<string, SchemaProperty> = inputSchema?.properties || {};
  const inputRequired = (inputSchema?.required as string[]) || [];
  const outputProperties: Record<string, SchemaProperty> = outputSchema?.properties || {};
  const outputRequired = (outputSchema?.required as string[]) || [];

  const annotations = activeTool?.annotations;
  const hasAnnotations =
    annotations &&
    (annotations.readOnlyHint !== undefined ||
      annotations.destructiveHint !== undefined ||
      annotations.idempotentHint !== undefined ||
      annotations.openWorldHint !== undefined);

  const tmpl = activeMeta?.templates?.['json-go-template'];
  const security = tmpl ? (tmpl as Record<string, unknown>).security : undefined;
  const hasRequestTemplate =
    tmpl?.requestTemplate && Object.keys(tmpl.requestTemplate).length > 0;
  const hasResponseTemplate =
    tmpl?.responseTemplate && Object.keys(tmpl.responseTemplate).length > 0;
  const hasTemplates = hasRequestTemplate || hasResponseTemplate || !!security;

  return (
    <div
      className={cn('flex rounded-lg border bg-card overflow-hidden', className)}
      style={{ height: 540 }}
    >
      {/* ===== Left sidebar: tool list ===== */}
      <div className="w-60 shrink-0 border-r flex flex-col bg-muted/20">
        {/* Search + count */}
        <div className="p-2.5 space-y-2 border-b bg-card">
          <div className="relative">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
            <Input
              className="h-8 text-sm pl-8 bg-transparent"
              placeholder={t('mcp.searchTools')}
              value={searchKeyword}
              onChange={(e) => setSearchKeyword(e.target.value)}
            />
          </div>
        </div>

        {/* Tool items */}
        <div className="flex-1 overflow-y-auto p-1.5">
          {filteredTools.map(({ tool, originalIndex }) => {
            const meta = toolsMeta?.[tool.name];
            const enabled = meta?.enabled !== false;
            const isActive = activeToolIndex === originalIndex;
            const paramCount = tool.inputSchema
              ? Object.keys(
                  ((tool.inputSchema as Record<string, unknown>).properties as Record<string, unknown>) || {},
                ).length
              : 0;

            return (
              <div
                key={tool.name}
                className={cn(
                  'px-2.5 py-2 rounded-md cursor-pointer transition-all mb-0.5',
                  isActive
                    ? 'bg-primary/10 shadow-sm ring-1 ring-primary/20'
                    : 'hover:bg-muted/60',
                )}
                onClick={() => setActiveToolIndex(originalIndex)}
              >
                <div className="flex items-center gap-2">
                  <div className={cn(
                    'flex items-center justify-center h-6 w-6 rounded-md shrink-0',
                    isActive ? 'bg-primary/15 text-primary' : 'bg-muted text-muted-foreground',
                  )}>
                    <Wrench className="h-3 w-3" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className={cn(
                      'text-[13px] font-medium truncate',
                      isActive && 'text-primary',
                    )}>
                      {tool.name}
                    </div>
                  </div>
                </div>
                <div className="flex items-center gap-1.5 mt-1.5 ml-8">
                  <span className={cn(
                    'inline-flex items-center gap-1 text-[10px] font-medium rounded-full px-1.5 py-px',
                    enabled
                      ? 'text-emerald-600 dark:text-emerald-400'
                      : 'text-muted-foreground',
                  )}>
                    <span className={cn(
                      'h-1.5 w-1.5 rounded-full',
                      enabled ? 'bg-emerald-500' : 'bg-gray-400 dark:bg-gray-600',
                    )} />
                    {enabled ? t('mcp.enabled') : t('mcp.disabled')}
                  </span>
                  {paramCount > 0 && (
                    <span className="text-[10px] text-muted-foreground">
                      {t('mcp.toolParams', { count: paramCount })}
                    </span>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* ===== Right panel: tool detail ===== */}
      <div className="flex-1 overflow-y-auto overflow-hidden">
        {activeTool ? (
          <div className="overflow-hidden">
            {/* Tool header */}
            <div className="sticky top-0 z-10 px-5 py-3.5 border-b bg-card/95 backdrop-blur-sm">
              <div className="flex items-center gap-2.5">
                <div className="flex items-center justify-center h-8 w-8 rounded-lg bg-primary/10 text-primary shrink-0">
                  <Wrench className="h-4 w-4" />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <h3 className="text-base font-semibold truncate">{activeTool.name}</h3>
                    <Badge
                      variant={isEnabled ? 'default' : 'secondary'}
                      className="text-[10px] h-[18px] px-1.5 shrink-0"
                    >
                      {isEnabled ? t('mcp.enabled') : t('mcp.disabled')}
                    </Badge>
                  </div>
                  {activeTool.description && (
                    <p className="text-xs text-muted-foreground mt-0.5 line-clamp-2">
                      {activeTool.description}
                    </p>
                  )}
                </div>
              </div>
            </div>

            {/* Detail content */}
            <div className="p-5 space-y-6 overflow-hidden">
              {/* Meta */}
              {activeTool._meta && Object.keys(activeTool._meta).length > 0 && (
                <div className="overflow-hidden">
                  <div className="flex items-center gap-2 mb-2.5">
                    <Braces className="h-3.5 w-3.5 text-muted-foreground" />
                    <h4 className="text-sm font-semibold">{t('mcp.toolMeta')}</h4>
                  </div>
                  <pre className="text-xs bg-muted/40 rounded-lg border p-3 overflow-x-auto max-w-full break-all whitespace-pre-wrap font-mono">
                    {JSON.stringify(activeTool._meta, null, 2)}
                  </pre>
                </div>
              )}

              {/* Annotations */}
              {hasAnnotations && (
                <div>
                  <div className="flex items-center gap-2 mb-2.5">
                    <ShieldCheck className="h-3.5 w-3.5 text-muted-foreground" />
                    <h4 className="text-sm font-semibold">{t('mcp.toolAnnotations')}</h4>
                  </div>
                  {annotations!.title && (
                    <p className="text-sm mb-2.5">
                      <span className="text-muted-foreground">Title: </span>
                      <span className="font-medium">{annotations!.title}</span>
                    </p>
                  )}
                  <div className="flex flex-wrap gap-2">
                    {(
                      [
                        ['readOnlyHint', annotations!.readOnlyHint],
                        ['destructiveHint', annotations!.destructiveHint],
                        ['idempotentHint', annotations!.idempotentHint],
                        ['openWorldHint', annotations!.openWorldHint],
                      ] as const
                    )
                      .filter(([, val]) => val !== undefined)
                      .map(([key, val]) => (
                        <div
                          key={key}
                          className={cn(
                            'inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs border',
                            val
                              ? 'bg-emerald-50 border-emerald-200 text-emerald-700 dark:bg-emerald-950/30 dark:border-emerald-800/50 dark:text-emerald-400'
                              : 'bg-muted/50 border-border text-muted-foreground',
                          )}
                        >
                          {val ? (
                            <CheckCircle className="h-3 w-3" />
                          ) : (
                            <XCircle className="h-3 w-3" />
                          )}
                          {t(`mcp.${key}`)}
                        </div>
                      ))}
                  </div>
                </div>
              )}

              {/* Input Schema */}
              <SchemaSection
                title={t('mcp.toolInputSchema')}
                icon={<Braces className="h-3.5 w-3.5 text-blue-500" />}
                properties={inputProperties}
                required={inputRequired}
              />

              {/* Output Schema */}
              {Object.keys(outputProperties).length > 0 && (
                <SchemaSection
                  title={t('mcp.toolOutputSchema')}
                  icon={<FileOutput className="h-3.5 w-3.5 text-emerald-500" />}
                  properties={outputProperties}
                  required={outputRequired}
                />
              )}

              {/* Protocol Conversion / Templates */}
              {hasTemplates && (
                <div className="overflow-hidden space-y-5">
                  <div className="flex items-center gap-2">
                    <ArrowRightLeft className="h-3.5 w-3.5 text-muted-foreground" />
                    <h4 className="text-sm font-semibold">{t('mcp.toolAdvancedConfig')}</h4>
                  </div>

                  {/* Transparent Auth */}
                  {!!security && (
                    <div>
                      <div className="flex items-center gap-2 mb-2.5">
                        <ShieldCheck className="h-3.5 w-3.5 text-violet-500" />
                        <h5 className="text-sm font-semibold">{t('mcp.passthroughAuth')}</h5>
                      </div>
                      <div className="rounded-lg border bg-card p-3 space-y-2">
                        <div className="flex items-center gap-2 text-sm">
                          <span className="text-muted-foreground">{t('mcp.status')}:</span>
                          <span
                            className={cn(
                              'font-medium',
                              (security as Record<string, unknown>).passthrough
                                ? 'text-emerald-600 dark:text-emerald-400'
                                : 'text-muted-foreground',
                            )}
                          >
                            {(security as Record<string, unknown>).passthrough
                              ? t('mcp.enabled')
                              : t('mcp.disabled')}
                          </span>
                        </div>
                        {!!(security as Record<string, unknown>).id && (
                          <div className="flex items-center gap-2 text-sm">
                            <span className="text-muted-foreground">ID:</span>
                            <code className="font-mono text-xs bg-muted/50 px-1.5 py-0.5 rounded">
                              {String((security as Record<string, unknown>).id)}
                            </code>
                          </div>
                        )}
                        {!!(security as Record<string, unknown>).type && (
                          <div className="flex items-center gap-2 text-sm">
                            <span className="text-muted-foreground">Type:</span>
                            <span className="font-medium">
                              {String((security as Record<string, unknown>).type)}
                            </span>
                          </div>
                        )}
                      </div>
                    </div>
                  )}

                  {/* Request Template */}
                  {hasRequestTemplate && (
                    <RequestTemplateSection
                      data={tmpl!.requestTemplate as Record<string, unknown>}
                    />
                  )}

                  {/* Response Template */}
                  {hasResponseTemplate && (
                    <ResponseTemplateSection
                      data={tmpl!.responseTemplate as Record<string, unknown>}
                    />
                  )}
                </div>
              )}
            </div>
          </div>
        ) : (
          <div className="flex flex-col items-center justify-center h-full gap-2 text-muted-foreground">
            <Wrench className="h-8 w-8 opacity-20" />
            <p className="text-sm">{t('mcp.selectToolToView')}</p>
          </div>
        )}
      </div>
    </div>
  );
}
