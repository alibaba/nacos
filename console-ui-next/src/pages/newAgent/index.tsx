import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  ArrowLeft,
  Bot,
  Check,
  ChevronLeft,
  ChevronRight,
  Download,
  FileJson,
  Layers3,
  Plus,
  Server,
  Trash2,
  Wand2,
} from 'lucide-react';
import { toast } from 'sonner';
import { agentApi } from '@/api/agent';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Textarea } from '@/components/ui/textarea';
import { cn } from '@/lib/utils';
import { useNamespaceStore } from '@/stores/namespace-store';
import type {
  A2aImportProjection,
  AgentEditorMode,
  AgentEditorValues,
  DraftContentMode,
  EndpointSourceMode,
  StructuredProtocolEditorKind,
  StructuredProtocolEditorValues,
} from './agent-console-model';
import {
  buildDraftCreateData,
  buildDraftUpdateData,
  buildMetadataUpdateData,
  callInterfacesToEditorValues,
  createStructuredProtocolEditor,
  metadataToEditorValues,
  projectA2aAgentCard,
} from './agent-console-model';

type CreatePath = 'choose' | 'import' | 'new';

const DEFAULT_AGENT_CARD = JSON.stringify({
  name: '',
  version: '1.0.0',
  description: '',
  supportedInterfaces: [
    {
      url: 'https://agent.example.com/a2a',
      protocolBinding: 'HTTP+JSON',
      protocolVersion: '0.3',
    },
  ],
  capabilities: {
    streaming: false,
    pushNotifications: false,
  },
  defaultInputModes: ['text/plain'],
  defaultOutputModes: ['text/plain'],
  skills: [],
}, null, 2);

function emptyValues(agentName = '', version = ''): AgentEditorValues {
  return {
    agentName,
    version,
    displayName: '',
    description: '',
    iconUrl: '',
    providerName: '',
    providerUrl: '',
    tags: '',
    extensions: '',
    status: 'enable',
    protocolEditorKind: 'a2a',
    agentCard: DEFAULT_AGENT_CARD,
    customProtocol: '',
    customProtocolVersion: '',
    customDescriptorMediaType: 'application/json',
    customNativeDescriptor: '{}',
    endpointSourceMode: 'declared-runtime',
    declaredEndpoints: [{ uri: '', transport: 'HTTP' }],
    callInterfaces: '',
    basedOnVersion: '',
    author: '',
    changeDescription: '',
  };
}

function resolveMode(value: string | null): AgentEditorMode {
  if (value === 'metadata' || value === 'draft-create' || value === 'draft-edit') {
    return value;
  }
  return 'create';
}

export default function NewAgentPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { currentNamespace } = useNamespaceStore();
  const namespaceId = searchParams.get('namespaceId') || currentNamespace || 'public';
  const mode = resolveMode(searchParams.get('mode'));
  const queryAgentName = searchParams.get('name') || '';
  const queryVersion = searchParams.get('version') || '';
  const [values, setValues] = useState(() => emptyValues(queryAgentName, queryVersion));
  const [contentMode, setContentMode] = useState<DraftContentMode>('direct');
  const [createPath, setCreatePath] = useState<CreatePath>(
    mode === 'create' ? 'choose' : 'new',
  );
  const [createStep, setCreateStep] = useState(0);
  const [importAgentCard, setImportAgentCard] = useState('');
  const [importVersion, setImportVersion] = useState('0.0.1');
  const [protocolEditors, setProtocolEditors] = useState<StructuredProtocolEditorValues[]>([
    createStructuredProtocolEditor('a2a', DEFAULT_AGENT_CARD),
  ]);
  const [activeProtocolIndex, setActiveProtocolIndex] = useState(0);
  const [loading, setLoading] = useState(mode !== 'create');
  const [saving, setSaving] = useState(false);

  const initialDraft = mode === 'create';
  const guidedCreate = mode === 'create' && createPath === 'new';
  const metadataVisible = mode === 'metadata' || (guidedCreate && createStep === 0);
  const versionVisible = mode === 'draft-create'
    || mode === 'draft-edit'
    || (guidedCreate && createStep === 1);
  const protocolVisible = mode !== 'metadata'
    && contentMode === 'direct'
    && (mode !== 'create' || (guidedCreate && createStep === 2));
  const title = mode === 'metadata'
    ? t('agent.editMetadata')
    : mode === 'draft-edit'
      ? t('agent.editDraft')
      : mode === 'draft-create'
        ? t('agent.createDraft')
        : t('agent.createAgent');

  const importProjection = useMemo(() => {
    if (mode !== 'create' || createPath !== 'import' || !importAgentCard.trim()) {
      return null;
    }
    try {
      return { value: projectA2aAgentCard(importAgentCard, importVersion), error: '' };
    } catch (error) {
      return {
        value: null,
        error: error instanceof Error ? error.message : t('agent.jsonFormatError'),
      };
    }
  }, [createPath, importAgentCard, importVersion, mode, t]);

  useEffect(() => {
    let active = true;
    async function load() {
      if (mode === 'create') {
        return;
      }
      if (!queryAgentName) {
        toast.error(t('agent.nameRequired'));
        setLoading(false);
        return;
      }
      try {
        if (mode === 'metadata') {
          const response = await agentApi.getAgent({
            namespaceId,
            agentName: queryAgentName,
          });
          if (active) {
            setValues(metadataToEditorValues(response.data.agent));
          }
        } else if (mode === 'draft-edit') {
          if (!queryVersion) {
            throw new Error(t('agent.versionRequired'));
          }
          const response = await agentApi.getVersion({
            namespaceId,
            agentName: queryAgentName,
            version: queryVersion,
          });
          if (active) {
            setValues((current) => ({
              ...current,
              agentName: queryAgentName,
              version: queryVersion,
              ...callInterfacesToEditorValues(response.data.callInterfaces),
              changeDescription: response.data.changeDescription || '',
            }));
          }
        }
      } catch (error) {
        toast.error(error instanceof Error ? error.message : t('agent.loadFailed'));
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }
    load();
    return () => {
      active = false;
    };
  }, [mode, namespaceId, queryAgentName, queryVersion, t]);

  const setValue = <K extends keyof AgentEditorValues>(
    key: K,
    value: AgentEditorValues[K],
  ) => {
    setValues((current) => ({ ...current, [key]: value }));
  };

  const goToDetail = (agentName: string, version?: string) => {
    const params = new URLSearchParams({ namespaceId, name: agentName });
    if (version) {
      params.set('version', version);
    }
    navigate(`/agentDetail?${params.toString()}`);
  };

  const advanceCreateStep = () => {
    if (createStep === 0 && !values.agentName.trim()) {
      toast.error(t('agent.nameRequired'));
      return;
    }
    if (createStep === 1 && !values.version.trim()) {
      toast.error(t('agent.versionRequired'));
      return;
    }
    setCreateStep((current) => Math.min(current + 1, 2));
  };

  const handleSubmit = async () => {
    setSaving(true);
    try {
      if (mode === 'metadata') {
        const response = await agentApi.updateAgent(buildMetadataUpdateData(namespaceId, values));
        toast.success(t('agent.updateSuccess'));
        goToDetail(response.data.agentName);
        return;
      }
      if (mode === 'draft-edit') {
        const response = await agentApi.updateDraft(buildDraftUpdateData(namespaceId, values));
        toast.success(t('agent.updateSuccess'));
        goToDetail(response.data.agentName, response.data.version);
        return;
      }
      if (mode === 'create' && createPath === 'import') {
        const projection = projectA2aAgentCard(importAgentCard, importVersion);
        const importedValues: AgentEditorValues = {
          ...values,
          ...projection,
          protocolEditorKind: 'a2a',
          agentCard: projection.protocolEditor.agentCard,
          changeDescription: t('agent.importChangeDescription'),
        };
        const response = await agentApi.createDraft(buildDraftCreateData(
          namespaceId,
          importedValues,
          true,
          'direct',
          [projection.protocolEditor],
        ));
        toast.success(t('agent.createDraftSuccess'));
        goToDetail(response.data.agentName, response.data.version);
        return;
      }
      const response = await agentApi.createDraft(
        buildDraftCreateData(
          namespaceId,
          values,
          initialDraft,
          initialDraft ? 'direct' : contentMode,
          guidedCreate ? protocolEditors : undefined,
        ),
      );
      toast.success(t('agent.createDraftSuccess'));
      goToDetail(response.data.agentName, response.data.version);
    } catch (error) {
      if (error instanceof Error && !('response' in error)) {
        toast.error(error.message);
      }
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="space-y-5">
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-[520px] w-full rounded-xl" />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-5xl space-y-5">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="sm" onClick={() => navigate(-1)}>
            <ArrowLeft className="mr-1.5 h-3.5 w-3.5" />
            {t('common.back')}
          </Button>
          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-violet-500 to-fuchsia-500">
            <Bot className="h-4.5 w-4.5 text-white" />
          </div>
          <div>
            <h1 className="text-lg font-bold">{title}</h1>
            <p className="text-xs text-muted-foreground">{namespaceId}</p>
          </div>
        </div>
      </div>

      {mode === 'create' && createPath === 'choose' && (
        <CreatePathSelection onSelect={setCreatePath} />
      )}

      {mode === 'create' && createPath === 'import' && (
        <KnownProtocolImport
          agentCard={importAgentCard}
          fallbackVersion={importVersion}
          projection={importProjection}
          onAgentCardChange={setImportAgentCard}
          onFallbackVersionChange={setImportVersion}
        />
      )}

      {guidedCreate && <CreateSteps current={createStep} />}

      {metadataVisible && (
        <MetadataEditor
          values={values}
          editableName={mode === 'create'}
          editableStatus={mode === 'metadata'}
          setValue={setValue}
        />
      )}

      {versionVisible && (
        <VersionEditor
          mode={mode}
          contentMode={contentMode}
          values={values}
          setContentMode={setContentMode}
          setValue={setValue}
        />
      )}

      {protocolVisible && (
        guidedCreate ? (
          <MultiProtocolEditor
            editors={protocolEditors}
            activeIndex={activeProtocolIndex}
            setEditors={setProtocolEditors}
            setActiveIndex={setActiveProtocolIndex}
          />
        ) : <ProtocolEditor values={values} setValue={setValue} />
      )}

      <div className="flex justify-between gap-3 pb-5">
        <div>
          {guidedCreate && createStep > 0 && (
            <Button
              variant="outline"
              disabled={saving}
              onClick={() => setCreateStep((current) => current - 1)}
            >
              <ChevronLeft className="mr-1.5 h-3.5 w-3.5" />
              {t('common.previous')}
            </Button>
          )}
          {mode === 'create' && createPath !== 'choose'
            && (createPath === 'import' || createStep === 0) && (
            <Button
              variant="outline"
              disabled={saving}
              onClick={() => {
                setCreatePath('choose');
                setCreateStep(0);
              }}
            >
              <ChevronLeft className="mr-1.5 h-3.5 w-3.5" />
              {t('agent.backToCreateMode')}
            </Button>
          )}
        </div>
        <div className="flex gap-3">
          <Button variant="outline" disabled={saving} onClick={() => navigate(-1)}>
            {t('common.cancel')}
          </Button>
          {mode === 'create' && createPath === 'choose' ? null : guidedCreate && createStep < 2 ? (
            <Button disabled={saving} onClick={advanceCreateStep}>
              {t('common.next')}
              <ChevronRight className="ml-1.5 h-3.5 w-3.5" />
            </Button>
          ) : (
            <Button
              disabled={saving || (createPath === 'import' && !importProjection?.value)}
              onClick={handleSubmit}
            >
              {saving
                ? t('common.loading')
                : createPath === 'import'
                  ? t('agent.importAndCreate')
                  : t('common.save')}
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}

function CreatePathSelection({ onSelect }: { onSelect: (value: CreatePath) => void }) {
  const { t } = useTranslation();
  return (
    <div className="space-y-3">
      <div>
        <h2 className="text-base font-semibold">{t('agent.chooseCreateMode')}</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          {t('agent.chooseCreateModeHelp')}
        </p>
      </div>
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <button
          type="button"
          className="group rounded-xl border bg-card p-5 text-left transition hover:border-primary/50 hover:shadow-sm"
          onClick={() => onSelect('import')}
        >
          <div className="mb-4 flex h-10 w-10 items-center justify-center rounded-xl bg-blue-500/10 text-blue-600">
            <Download className="h-5 w-5" />
          </div>
          <h3 className="font-semibold">{t('agent.importKnownProtocol')}</h3>
          <p className="mt-2 text-sm leading-6 text-muted-foreground">
            {t('agent.importKnownProtocolHelp')}
          </p>
          <span className="mt-4 inline-flex items-center text-sm font-medium text-primary">
            {t('agent.startImport')}
            <ChevronRight className="ml-1 h-4 w-4 transition-transform group-hover:translate-x-0.5" />
          </span>
        </button>
        <button
          type="button"
          className="group rounded-xl border bg-card p-5 text-left transition hover:border-primary/50 hover:shadow-sm"
          onClick={() => onSelect('new')}
        >
          <div className="mb-4 flex h-10 w-10 items-center justify-center rounded-xl bg-violet-500/10 text-violet-600">
            <Wand2 className="h-5 w-5" />
          </div>
          <h3 className="font-semibold">{t('agent.createFromScratch')}</h3>
          <p className="mt-2 text-sm leading-6 text-muted-foreground">
            {t('agent.createFromScratchHelp')}
          </p>
          <span className="mt-4 inline-flex items-center text-sm font-medium text-primary">
            {t('agent.startCreate')}
            <ChevronRight className="ml-1 h-4 w-4 transition-transform group-hover:translate-x-0.5" />
          </span>
        </button>
      </div>
    </div>
  );
}

function KnownProtocolImport({
  agentCard,
  fallbackVersion,
  projection,
  onAgentCardChange,
  onFallbackVersionChange,
}: {
  agentCard: string;
  fallbackVersion: string;
  projection: { value: A2aImportProjection | null; error: string } | null;
  onAgentCardChange: (value: string) => void;
  onFallbackVersionChange: (value: string) => void;
}) {
  const { t } = useTranslation();
  return (
    <EditorCard icon={<Download className="h-4 w-4" />} title={t('agent.importKnownProtocol')}>
      <div className="space-y-5">
        <SelectField
          label={t('agent.knownProtocol')}
          value="a2a"
          options={[{ value: 'a2a', label: 'A2A' }]}
          onChange={() => undefined}
        />
        <div className="space-y-2">
          <Label>
            {t('agent.initialVersion')} <span className="text-destructive">*</span>
          </Label>
          <Input
            value={fallbackVersion}
            placeholder="0.0.1"
            onChange={(event) => onFallbackVersionChange(event.target.value)}
          />
          <p className="text-xs leading-5 text-muted-foreground">
            {t('agent.importVersionHelp')}
          </p>
        </div>
        <div className="space-y-2">
          <Label>
            {t('agent.agentCard')} <span className="text-destructive">*</span>
          </Label>
          <Textarea
            value={agentCard}
            rows={24}
            className="font-mono text-xs"
            placeholder={t('agent.agentCardPlaceholder')}
            onChange={(event) => onAgentCardChange(event.target.value)}
          />
          <p className="text-xs leading-5 text-muted-foreground">
            {t('agent.importAgentCardHelp')}
          </p>
        </div>
        {projection?.error && (
          <div className="rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
            {projection.error}
          </div>
        )}
        {projection?.value && (
          <div className="rounded-xl border bg-muted/20 p-4">
            <div className="mb-3 flex items-center gap-2">
              <Check className="h-4 w-4 text-emerald-600" />
              <h3 className="text-sm font-semibold">{t('agent.importPreview')}</h3>
            </div>
            <div className="grid grid-cols-1 gap-3 text-sm sm:grid-cols-2 lg:grid-cols-4">
              <PreviewField label={t('agent.agentName')} value={projection.value.agentName} />
              <PreviewField label={t('agent.version')} value={projection.value.version} />
              <PreviewField label={t('agent.protocol')} value="A2A" />
              <PreviewField
                label={t('agent.declaredEndpoints')}
                value={String(
                  JSON.parse(projection.value.protocolEditor.agentCard).supportedInterfaces.length,
                )}
              />
            </div>
          </div>
        )}
      </div>
    </EditorCard>
  );
}

function PreviewField({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="mt-1 break-words font-medium">{value}</p>
    </div>
  );
}

function CreateSteps({ current }: { current: number }) {
  const { t } = useTranslation();
  const steps = [t('agent.metadata'), t('agent.initialVersion'), t('agent.protocolConfig')];
  return (
    <div className="rounded-xl border bg-card px-5 py-4">
      <div className="grid grid-cols-3 gap-3">
        {steps.map((label, index) => (
          <div key={label} className="flex items-center gap-2">
            <div
              className={cn(
                'flex h-7 w-7 shrink-0 items-center justify-center rounded-full border text-xs font-semibold',
                index < current && 'border-primary bg-primary text-primary-foreground',
                index === current && 'border-primary text-primary ring-4 ring-primary/10',
                index > current && 'border-muted-foreground/30 text-muted-foreground',
              )}
            >
              {index < current ? <Check className="h-3.5 w-3.5" /> : index + 1}
            </div>
            <span
              className={cn(
                'hidden text-sm sm:block',
                index === current ? 'font-semibold' : 'text-muted-foreground',
              )}
            >
              {label}
            </span>
            {index < steps.length - 1 && <div className="h-px flex-1 bg-border" />}
          </div>
        ))}
      </div>
    </div>
  );
}

function MetadataEditor({
  values,
  editableName,
  editableStatus,
  setValue,
}: {
  values: AgentEditorValues;
  editableName: boolean;
  editableStatus: boolean;
  setValue: <K extends keyof AgentEditorValues>(
    key: K,
    value: AgentEditorValues[K],
  ) => void;
}) {
  const { t } = useTranslation();
  return (
    <EditorCard icon={<Server className="h-4 w-4" />} title={t('agent.metadata')}>
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <Field
          label={t('agent.agentName')}
          required
          value={values.agentName}
          disabled={!editableName}
          onChange={(value) => setValue('agentName', value)}
        />
        <Field
          label={t('agent.displayName')}
          value={values.displayName}
          onChange={(value) => setValue('displayName', value)}
        />
        <Field
          label={t('agent.iconUrl')}
          value={values.iconUrl}
          onChange={(value) => setValue('iconUrl', value)}
        />
        <Field
          label={t('agent.tags')}
          value={values.tags}
          placeholder={t('agent.tagsPlaceholder')}
          onChange={(value) => setValue('tags', value)}
        />
        <Field
          label={t('agent.providerName')}
          value={values.providerName}
          onChange={(value) => setValue('providerName', value)}
        />
        <Field
          label={t('agent.providerUrl')}
          value={values.providerUrl}
          onChange={(value) => setValue('providerUrl', value)}
        />
        {editableStatus && (
          <SelectField
            label={t('agent.status')}
            value={values.status}
            options={[
              { value: 'enable', label: t('agent.enabled') },
              { value: 'disable', label: t('agent.disabled') },
            ]}
            onChange={(value) => setValue('status', value as AgentEditorValues['status'])}
          />
        )}
        <div className="space-y-2 md:col-span-2">
          <Label>{t('agent.description')}</Label>
          <Textarea
            value={values.description}
            rows={3}
            onChange={(event) => setValue('description', event.target.value)}
          />
        </div>
        <div className="space-y-2 md:col-span-2">
          <Label>{t('agent.extensions')}</Label>
          <Textarea
            value={values.extensions}
            rows={5}
            className="font-mono text-xs"
            placeholder="{}"
            onChange={(event) => setValue('extensions', event.target.value)}
          />
        </div>
      </div>
    </EditorCard>
  );
}

function VersionEditor({
  mode,
  contentMode,
  values,
  setContentMode,
  setValue,
}: {
  mode: AgentEditorMode;
  contentMode: DraftContentMode;
  values: AgentEditorValues;
  setContentMode: (value: DraftContentMode) => void;
  setValue: <K extends keyof AgentEditorValues>(
    key: K,
    value: AgentEditorValues[K],
  ) => void;
}) {
  const { t } = useTranslation();
  return (
    <EditorCard icon={<FileJson className="h-4 w-4" />} title={t('agent.initialVersion')}>
      <div className="space-y-4">
        {mode !== 'create' && (
          <Field
            label={t('agent.agentName')}
            required
            value={values.agentName}
            disabled
            onChange={(value) => setValue('agentName', value)}
          />
        )}
        <Field
          label={t('agent.version')}
          required
          value={values.version}
          disabled={mode === 'draft-edit'}
          onChange={(value) => setValue('version', value)}
        />
        {mode === 'draft-create' && (
          <div className="space-y-2">
            <Label>{t('agent.draftContentMode')}</Label>
            <RadioGroup
              value={contentMode}
              onValueChange={(value) => setContentMode(value as DraftContentMode)}
              className="flex gap-6"
            >
              <label className="flex items-center gap-2 text-sm">
                <RadioGroupItem value="direct" />
                {t('agent.directContent')}
              </label>
              <label className="flex items-center gap-2 text-sm">
                <RadioGroupItem value="copy" />
                {t('agent.copyVersion')}
              </label>
            </RadioGroup>
          </div>
        )}
        {mode === 'draft-create' && contentMode === 'copy' && (
          <Field
            label={t('agent.basedOnVersion')}
            required
            value={values.basedOnVersion}
            onChange={(value) => setValue('basedOnVersion', value)}
          />
        )}
        {mode !== 'draft-edit' && (
          <Field
            label={t('agent.author')}
            value={values.author}
            onChange={(value) => setValue('author', value)}
          />
        )}
        <div className="space-y-2">
          <Label>{t('agent.changeDescription')}</Label>
          <Textarea
            value={values.changeDescription}
            rows={3}
            onChange={(event) => setValue('changeDescription', event.target.value)}
          />
        </div>
      </div>
    </EditorCard>
  );
}

function MultiProtocolEditor({
  editors,
  activeIndex,
  setEditors,
  setActiveIndex,
}: {
  editors: StructuredProtocolEditorValues[];
  activeIndex: number;
  setEditors: (value: StructuredProtocolEditorValues[]) => void;
  setActiveIndex: (value: number) => void;
}) {
  const { t } = useTranslation();
  const selectedEditor = editors[activeIndex] || editors[0];
  const updateSelected = (value: StructuredProtocolEditorValues) => {
    setEditors(editors.map((editor, index) => index === activeIndex ? value : editor));
  };
  const removeSelected = () => {
    if (editors.length <= 1) {
      return;
    }
    const next = editors.filter((_, index) => index !== activeIndex);
    setEditors(next);
    setActiveIndex(Math.min(activeIndex, next.length - 1));
  };
  const moveSelected = (offset: number) => {
    const target = activeIndex + offset;
    if (target < 0 || target >= editors.length) {
      return;
    }
    const next = [...editors];
    [next[activeIndex], next[target]] = [next[target], next[activeIndex]];
    setEditors(next);
    setActiveIndex(target);
  };

  return (
    <EditorCard icon={<Layers3 className="h-4 w-4" />} title={t('agent.protocolConfig')}>
      <div className="space-y-5">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="text-sm font-medium">{t('agent.supportedProtocols')}</p>
            <p className="mt-1 text-xs text-muted-foreground">
              {t('agent.protocolOrderHelp')}
            </p>
          </div>
          <Button
            type="button"
            size="sm"
            variant="outline"
            onClick={() => {
              setEditors([...editors, createStructuredProtocolEditor('custom')]);
              setActiveIndex(editors.length);
            }}
          >
            <Plus className="mr-1.5 h-3.5 w-3.5" />
            {t('agent.addProtocol')}
          </Button>
        </div>
        <Tabs value={String(activeIndex)} onValueChange={(value) => setActiveIndex(Number(value))}>
          <div className="overflow-x-auto pb-1">
            <TabsList className="w-max min-w-full justify-start">
              {editors.map((editor, index) => (
                <TabsTrigger key={`${editor.protocolEditorKind}-${index}`} value={String(index)}>
                  {editor.protocolEditorKind === 'a2a'
                    ? 'A2A'
                    : editor.customProtocol.trim() || t('agent.protocolNumber', { number: index + 1 })}
                </TabsTrigger>
              ))}
            </TabsList>
          </div>
          {selectedEditor && (
            <TabsContent value={String(activeIndex)} className="mt-4 space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border bg-muted/20 px-3 py-2">
                <span className="text-xs text-muted-foreground">
                  {t('agent.protocolPriority', { number: activeIndex + 1 })}
                </span>
                <div className="flex items-center gap-1">
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    disabled={activeIndex === 0}
                    onClick={() => moveSelected(-1)}
                  >
                    <ChevronLeft className="mr-1 h-3.5 w-3.5" />
                    {t('agent.moveForward')}
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    disabled={activeIndex === editors.length - 1}
                    onClick={() => moveSelected(1)}
                  >
                    {t('agent.moveBackward')}
                    <ChevronRight className="ml-1 h-3.5 w-3.5" />
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    className="text-destructive hover:text-destructive"
                    disabled={editors.length === 1}
                    onClick={removeSelected}
                  >
                    <Trash2 className="mr-1 h-3.5 w-3.5" />
                    {t('agent.removeProtocol')}
                  </Button>
                </div>
              </div>
              <StructuredProtocolFields editor={selectedEditor} onChange={updateSelected} />
            </TabsContent>
          )}
        </Tabs>
      </div>
    </EditorCard>
  );
}

function ProtocolEditor({
  values,
  setValue,
}: {
  values: AgentEditorValues;
  setValue: <K extends keyof AgentEditorValues>(
    key: K,
    value: AgentEditorValues[K],
  ) => void;
}) {
  const { t } = useTranslation();
  if (values.protocolEditorKind === 'raw') {
    return (
      <EditorCard icon={<FileJson className="h-4 w-4" />} title={t('agent.protocolConfig')}>
        <div className="space-y-4">
          <SelectField
            label={t('agent.protocolType')}
            value="raw"
            options={[{ value: 'raw', label: t('agent.rawCallInterfaces') }]}
            onChange={() => undefined}
          />
          <div className="space-y-2">
            <Label>{t('agent.rawCallInterfaces')}</Label>
            <Textarea
              value={values.callInterfaces}
              rows={22}
              className="font-mono text-xs"
              onChange={(event) => setValue('callInterfaces', event.target.value)}
            />
            <p className="text-xs text-muted-foreground">
              {t('agent.rawCallInterfacesHelp')}
            </p>
          </div>
        </div>
      </EditorCard>
    );
  }
  const editor: StructuredProtocolEditorValues = {
    protocolEditorKind: values.protocolEditorKind,
    agentCard: values.agentCard,
    customProtocol: values.customProtocol,
    customProtocolVersion: values.customProtocolVersion,
    customDescriptorMediaType: values.customDescriptorMediaType,
    customNativeDescriptor: values.customNativeDescriptor,
    endpointSourceMode: values.endpointSourceMode,
    declaredEndpoints: values.declaredEndpoints,
  };
  const updateEditor = (updated: StructuredProtocolEditorValues) => {
    setValue('protocolEditorKind', updated.protocolEditorKind);
    setValue('agentCard', updated.agentCard);
    setValue('customProtocol', updated.customProtocol);
    setValue('customProtocolVersion', updated.customProtocolVersion);
    setValue('customDescriptorMediaType', updated.customDescriptorMediaType);
    setValue('customNativeDescriptor', updated.customNativeDescriptor);
    setValue('endpointSourceMode', updated.endpointSourceMode);
    setValue('declaredEndpoints', updated.declaredEndpoints);
  };
  return (
    <EditorCard icon={<FileJson className="h-4 w-4" />} title={t('agent.protocolConfig')}>
      <StructuredProtocolFields editor={editor} onChange={updateEditor} />
    </EditorCard>
  );
}

function StructuredProtocolFields({
  editor,
  onChange,
}: {
  editor: StructuredProtocolEditorValues;
  onChange: (value: StructuredProtocolEditorValues) => void;
}) {
  const { t } = useTranslation();
  const setEditorValue = <K extends keyof StructuredProtocolEditorValues>(
    key: K,
    value: StructuredProtocolEditorValues[K],
  ) => onChange({ ...editor, [key]: value });
  const updateEndpoint = (index: number, field: 'uri' | 'transport', value: string) => {
    setEditorValue('declaredEndpoints', editor.declaredEndpoints.map((endpoint, itemIndex) => (
      itemIndex === index ? { ...endpoint, [field]: value } : endpoint
    )));
  };

  return (
    <div className="space-y-5">
      <SelectField
        label={t('agent.protocolType')}
        value={editor.protocolEditorKind}
        options={[
          { value: 'a2a', label: 'A2A' },
          { value: 'custom', label: t('agent.customProtocol') },
        ]}
        onChange={(value) => setEditorValue(
          'protocolEditorKind',
          value as StructuredProtocolEditorKind,
        )}
      />
      {editor.protocolEditorKind === 'a2a' && (
        <div className="space-y-2">
          <Label>
            {t('agent.agentCard')} <span className="text-destructive">*</span>
          </Label>
          <Textarea
            value={editor.agentCard}
            rows={22}
            className="font-mono text-xs"
            placeholder={t('agent.agentCardPlaceholder')}
            onChange={(event) => setEditorValue('agentCard', event.target.value)}
          />
          <p className="text-xs leading-5 text-muted-foreground">
            {t('agent.agentCardHelp')}
          </p>
        </div>
      )}
      {editor.protocolEditorKind === 'custom' && (
        <div className="space-y-5">
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <Field
              label={t('agent.protocol')}
              required
              value={editor.customProtocol}
              onChange={(value) => setEditorValue('customProtocol', value)}
            />
            <Field
              label={t('agent.protocolVersion')}
              value={editor.customProtocolVersion}
              onChange={(value) => setEditorValue('customProtocolVersion', value)}
            />
            <Field
              label={t('agent.descriptorMediaType')}
              required
              value={editor.customDescriptorMediaType}
              onChange={(value) => setEditorValue('customDescriptorMediaType', value)}
            />
            <SelectField
              label={t('agent.endpointSourceOrder')}
              value={editor.endpointSourceMode}
              options={[
                { value: 'declared-runtime', label: 'DECLARED → RUNTIME' },
                { value: 'runtime-declared', label: 'RUNTIME → DECLARED' },
                { value: 'declared-only', label: 'DECLARED' },
                { value: 'runtime-only', label: 'RUNTIME' },
              ]}
              onChange={(value) => setEditorValue(
                'endpointSourceMode',
                value as EndpointSourceMode,
              )}
            />
          </div>
          <div className="space-y-2">
            <Label>
              {t('agent.nativeDescriptor')} <span className="text-destructive">*</span>
            </Label>
            <Textarea
              value={editor.customNativeDescriptor}
              rows={12}
              className="font-mono text-xs"
              onChange={(event) => setEditorValue('customNativeDescriptor', event.target.value)}
            />
          </div>
          <div className="space-y-3">
            <div className="flex items-center justify-between gap-3">
              <div>
                <Label>{t('agent.declaredEndpoints')}</Label>
                <p className="mt-1 text-xs text-muted-foreground">
                  {t('agent.declaredEndpointsHelp')}
                </p>
              </div>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => setEditorValue('declaredEndpoints', [
                  ...editor.declaredEndpoints,
                  { uri: '', transport: 'HTTP' },
                ])}
              >
                <Plus className="mr-1.5 h-3.5 w-3.5" />
                {t('agent.addEndpoint')}
              </Button>
            </div>
            {editor.declaredEndpoints.length === 0 ? (
              <div className="rounded-lg border border-dashed p-5 text-center text-sm text-muted-foreground">
                {t('agent.noDeclaredEndpoints')}
              </div>
            ) : editor.declaredEndpoints.map((endpoint, index) => (
              <div
                key={index}
                className="grid grid-cols-1 items-end gap-3 rounded-lg border bg-muted/10 p-3 md:grid-cols-[minmax(0,1fr)_220px_auto]"
              >
                <Field
                  label={t('agent.endpointUri')}
                  value={endpoint.uri}
                  placeholder="https://agent.example.com/api"
                  onChange={(value) => updateEndpoint(index, 'uri', value)}
                />
                <Field
                  label={t('agent.transport')}
                  value={endpoint.transport}
                  placeholder="HTTP"
                  onChange={(value) => updateEndpoint(index, 'transport', value)}
                />
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  className="text-destructive"
                  aria-label={t('common.delete')}
                  onClick={() => setEditorValue(
                    'declaredEndpoints',
                    editor.declaredEndpoints.filter((_, itemIndex) => itemIndex !== index),
                  )}
                >
                  <Trash2 className="h-4 w-4" />
                </Button>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function EditorCard({
  icon,
  title,
  children,
}: {
  icon: React.ReactNode;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <Card className="gap-0 overflow-hidden py-0">
      <div className="flex items-center gap-2 border-b bg-muted/30 px-5 py-3.5">
        {icon}
        <h2 className="text-sm font-semibold">{title}</h2>
      </div>
      <CardContent className="p-5">{children}</CardContent>
    </Card>
  );
}

function SelectField({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: string;
  options: Array<{ value: string; label: string }>;
  onChange: (value: string) => void;
}) {
  return (
    <div className="space-y-2">
      <Label>{label}</Label>
      <Select value={value} onValueChange={onChange}>
        <SelectTrigger><SelectValue /></SelectTrigger>
        <SelectContent>
          {options.map((option) => (
            <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}

function Field({
  label,
  value,
  onChange,
  required,
  disabled,
  placeholder,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  required?: boolean;
  disabled?: boolean;
  placeholder?: string;
}) {
  return (
    <div className="space-y-2">
      <Label>
        {label}
        {required && <span className="ml-1 text-destructive">*</span>}
      </Label>
      <Input
        value={value}
        disabled={disabled}
        placeholder={placeholder}
        onChange={(event) => onChange(event.target.value)}
      />
    </div>
  );
}
