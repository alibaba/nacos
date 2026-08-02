/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {
  Button,
  Card,
  ConfigProvider,
  Field,
  Form,
  Input,
  Message,
  Select,
  Step,
  Tab,
} from '@alifd/next';
import PageTitle from 'components/PageTitle';
import { getParams } from '@/globalLib';
import { agentApi } from '../agent-api';
import {
  DEFAULT_AGENT_CARD,
  buildDraftCreateData,
  buildDraftUpdateData,
  buildMetadataUpdateData,
  callInterfacesToFormValues,
  createStructuredProtocolEditor,
  metadataToFormValues,
  projectA2aAgentCard,
} from '../agent-console-model';
import './NewAgent.scss';

const MODES = ['create', 'metadata', 'draft-create', 'draft-edit'];

@ConfigProvider.config
class NewAgent extends React.Component {
  static displayName = 'NewAgent';

  static propTypes = {
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.field = new Field(this);
    const modeParam = getParams('mode');
    const mode = MODES.includes(modeParam) ? modeParam : 'create';
    this.state = {
      mode,
      createPath: mode === 'create' ? 'choose' : 'new',
      createStep: 0,
      contentMode: 'direct',
      importAgentCard: '',
      importVersion: '0.0.1',
      protocolEditorKind: 'a2a',
      declaredEndpoints: [{ uri: '', transport: 'HTTP' }],
      protocolEditors: [createStructuredProtocolEditor('a2a', DEFAULT_AGENT_CARD)],
      activeProtocolIndex: 0,
      loading: mode === 'metadata' || mode === 'draft-edit',
      saving: false,
    };
  }

  componentDidMount() {
    const { mode } = this.state;
    const agentName = getParams('name') || '';
    const version = getParams('version') || '';
    this.field.setValues({
      agentName,
      version,
      status: 'enable',
      protocolEditorKind: 'a2a',
      agentCard: DEFAULT_AGENT_CARD,
      customProtocol: '',
      customProtocolVersion: '',
      customDescriptorMediaType: 'application/json',
      customNativeDescriptor: '{}',
      endpointSourceMode: 'declared-runtime',
      callInterfaces: '',
    });
    if (mode === 'metadata') {
      agentApi
        .get({ namespaceId: this.namespaceId(), agentName })
        .then(overview => this.field.setValues(metadataToFormValues(overview.agent)))
        .catch(error => Message.error(error.message))
        .finally(() => this.setState({ loading: false }));
    } else if (mode === 'draft-edit') {
      agentApi
        .version({ namespaceId: this.namespaceId(), agentName, version })
        .then(detail => {
          const editor = callInterfacesToFormValues(detail.callInterfaces);
          this.field.setValues({
            ...editor,
            changeDescription: detail.changeDescription || '',
          });
          this.setState({
            protocolEditorKind: editor.protocolEditorKind,
            declaredEndpoints: editor.declaredEndpoints || [],
          });
        })
        .catch(error => Message.error(error.message))
        .finally(() => this.setState({ loading: false }));
    }
  }

  namespaceId = () => getParams('namespace') || 'public';

  handleGoBack = () => {
    this.props.history.goBack();
  };

  handleSubmit = () => {
    this.field.validate((errors, fieldValues) => {
      if (errors) {
        return;
      }
      const {
        mode,
        createPath,
        contentMode,
        declaredEndpoints,
        importAgentCard,
        importVersion,
        protocolEditors,
      } = this.state;
      const values = { ...fieldValues, declaredEndpoints };
      this.setState({ saving: true });
      let operation;
      try {
        if (mode === 'metadata') {
          operation = agentApi.update(buildMetadataUpdateData(this.namespaceId(), values));
        } else if (mode === 'draft-edit') {
          operation = agentApi.updateDraft(buildDraftUpdateData(this.namespaceId(), values));
        } else if (mode === 'create' && createPath === 'import') {
          const projection = projectA2aAgentCard(importAgentCard, importVersion);
          Object.assign(values, projection, {
            protocolEditorKind: 'a2a',
            agentCard: projection.protocolEditor.agentCard,
            changeDescription: 'Imported from A2A AgentCard',
          });
          operation = agentApi.createDraft(
            buildDraftCreateData(this.namespaceId(), values, true, 'direct', [
              projection.protocolEditor,
            ])
          );
        } else {
          operation = agentApi.createDraft(
            buildDraftCreateData(
              this.namespaceId(),
              values,
              mode === 'create',
              mode === 'create' ? 'direct' : contentMode,
              mode === 'create' && createPath === 'new' ? protocolEditors : undefined
            )
          );
        }
      } catch (error) {
        Message.error(error.message);
        this.setState({ saving: false });
        return;
      }
      operation
        .then(result => {
          Message.success(mode === 'metadata' ? 'Agent metadata updated' : 'Agent draft saved');
          const resultAgentName = result.agentName || values.agentName;
          const resultVersion = result.version || values.version;
          const params = new URLSearchParams({
            namespace: this.namespaceId(),
            name: resultAgentName,
          });
          if (mode !== 'metadata') {
            params.set('version', resultVersion);
          }
          this.props.history.push(`/agentDetail?${params.toString()}`);
        })
        .catch(error => Message.error(error.message))
        .finally(() => this.setState({ saving: false }));
    });
  };

  required = (rule, value, callback) => {
    if (!value || !String(value).trim()) {
      callback('This field is required');
    } else {
      callback();
    }
  };

  advanceCreateStep = () => {
    const { createStep } = this.state;
    const values = this.field.getValues();
    if (createStep === 0 && !String(values.agentName || '').trim()) {
      Message.error('Agent Name is required');
      return;
    }
    if (createStep === 1 && !String(values.version || '').trim()) {
      Message.error('Version is required');
      return;
    }
    this.setState({ createStep: Math.min(createStep + 1, 2) });
  };

  renderCreatePathSelection() {
    return (
      <div className="agent-create-paths">
        <button
          type="button"
          className="agent-create-path-card"
          onClick={() => this.setState({ createPath: 'import' })}
        >
          <span className="agent-create-path-icon">⇩</span>
          <strong>Import a Known Protocol</strong>
          <span>
            Paste a complete A2A Agent Card. Agent metadata, the initial version, Call Interface,
            and declared endpoints are derived automatically.
          </span>
        </button>
        <button
          type="button"
          className="agent-create-path-card"
          onClick={() => this.setState({ createPath: 'new' })}
        >
          <span className="agent-create-path-icon">＋</span>
          <strong>Create from Scratch</strong>
          <span>
            Configure metadata, the initial version, and one or more supported protocols step by
            step.
          </span>
        </button>
      </div>
    );
  }

  renderKnownProtocolImport() {
    const { importAgentCard, importVersion } = this.state;
    let projection;
    let error;
    if (importAgentCard.trim()) {
      try {
        projection = projectA2aAgentCard(importAgentCard, importVersion);
      } catch (e) {
        error = e.message;
      }
    }
    return (
      <Card title="Import a Known Protocol" contentHeight="auto" className="agent-editor-card">
        <div className="agent-import-protocol-label">Known Protocol</div>
        <Select
          value="a2a"
          dataSource={[{ value: 'a2a', label: 'A2A' }]}
          style={{ width: '100%', marginBottom: 16 }}
        />
        <div className="agent-import-protocol-label">Initial Version</div>
        <Input
          value={importVersion}
          placeholder="0.0.1"
          onChange={value => this.setState({ importVersion: value })}
        />
        <p className="agent-import-help">
          Used only when the Agent Card omits version. An explicit Card version takes precedence.
        </p>
        <div className="agent-import-protocol-label">Complete Agent Card</div>
        <Input.TextArea
          value={importAgentCard}
          rows={24}
          placeholder="Paste the complete A2A Agent Card JSON"
          onChange={value => this.setState({ importAgentCard: value })}
        />
        <p className="agent-import-help">
          The Agent Card must contain name and callable interfaces. A missing version uses the
          initial version above. Trailing commas are accepted and normalized before submission.
        </p>
        {error && <div className="agent-import-error">{error}</div>}
        {projection && (
          <div className="agent-import-preview">
            <strong>Import Preview</strong>
            <span>Agent: {projection.agentName}</span>
            <span>Version: {projection.version}</span>
            <span>Protocol: A2A</span>
          </div>
        )}
      </Card>
    );
  }

  renderMetadataForm() {
    const { mode } = this.state;
    return (
      <Card title="Agent Metadata" contentHeight="auto" className="agent-editor-card">
        <div className="agent-form-grid">
          <Form.Item label="Agent Name" required validator={this.required}>
            <Input name="agentName" disabled={mode !== 'create'} />
          </Form.Item>
          <Form.Item label="Display Name">
            <Input name="displayName" />
          </Form.Item>
          <Form.Item label="Icon URL">
            <Input name="iconUrl" />
          </Form.Item>
          <Form.Item label="Tags" help="Comma-separated values">
            <Input name="tags" />
          </Form.Item>
          <Form.Item label="Provider Name">
            <Input name="providerName" />
          </Form.Item>
          <Form.Item label="Provider URL">
            <Input name="providerUrl" />
          </Form.Item>
        </div>
        <Form.Item label="Description">
          <Input.TextArea name="description" rows={3} />
        </Form.Item>
        <Form.Item label="Extensions" help="JSON object">
          <Input.TextArea name="extensions" rows={5} />
        </Form.Item>
        {mode === 'metadata' && (
          <Form.Item label="Status">
            <Select
              name="status"
              dataSource={[
                { value: 'enable', label: 'Enabled' },
                { value: 'disable', label: 'Disabled' },
              ]}
            />
          </Form.Item>
        )}
      </Card>
    );
  }

  renderVersionForm() {
    const { mode, contentMode } = this.state;
    return (
      <Card title="Agent Version" contentHeight="auto" className="agent-editor-card">
        {mode !== 'create' && (
          <Form.Item label="Agent Name" required>
            <Input name="agentName" disabled />
          </Form.Item>
        )}
        <Form.Item label="Version" required validator={this.required}>
          <Input name="version" disabled={mode === 'draft-edit'} />
        </Form.Item>
        {mode === 'draft-create' && (
          <Form.Item label="Content Source">
            <Select
              value={contentMode}
              onChange={value => this.setState({ contentMode: value })}
              dataSource={[
                { value: 'direct', label: 'Configure Protocol' },
                { value: 'copy', label: 'Copy Existing Version' },
              ]}
            />
          </Form.Item>
        )}
        {mode === 'draft-create' && contentMode === 'copy' && (
          <Form.Item label="Based-on Version" required validator={this.required}>
            <Input name="basedOnVersion" />
          </Form.Item>
        )}
        {mode !== 'draft-edit' && (
          <Form.Item label="Author">
            <Input name="author" />
          </Form.Item>
        )}
        <Form.Item label="Change Description">
          <Input.TextArea name="changeDescription" rows={3} />
        </Form.Item>
      </Card>
    );
  }

  updateEndpoint = (index, field, value) => {
    this.setState(({ declaredEndpoints }) => ({
      declaredEndpoints: declaredEndpoints.map((endpoint, itemIndex) =>
        itemIndex === index ? { ...endpoint, [field]: value } : endpoint
      ),
    }));
  };

  updateProtocolEditor = (index, field, value) => {
    this.setState(({ protocolEditors }) => ({
      protocolEditors: protocolEditors.map((editor, itemIndex) =>
        itemIndex === index ? { ...editor, [field]: value } : editor
      ),
    }));
  };

  updateProtocolEndpoint = (editorIndex, endpointIndex, field, value) => {
    this.setState(({ protocolEditors }) => ({
      protocolEditors: protocolEditors.map((editor, itemIndex) => {
        if (itemIndex !== editorIndex) {
          return editor;
        }
        return {
          ...editor,
          declaredEndpoints: editor.declaredEndpoints.map((endpoint, currentEndpointIndex) =>
            currentEndpointIndex === endpointIndex ? { ...endpoint, [field]: value } : endpoint
          ),
        };
      }),
    }));
  };

  renderStructuredProtocolFields(editor, editorIndex) {
    return (
      <div className="agent-structured-protocol-fields">
        <div className="agent-import-protocol-label">Protocol Type</div>
        <Select
          value={editor.protocolEditorKind}
          dataSource={[
            { value: 'a2a', label: 'A2A' },
            { value: 'custom', label: 'Custom Protocol' },
          ]}
          style={{ width: '100%', marginBottom: 16 }}
          onChange={value => this.updateProtocolEditor(editorIndex, 'protocolEditorKind', value)}
        />
        {editor.protocolEditorKind === 'a2a' ? (
          <React.Fragment>
            <div className="agent-import-protocol-label">Complete Agent Card</div>
            <Input.TextArea
              value={editor.agentCard}
              rows={22}
              onChange={value => this.updateProtocolEditor(editorIndex, 'agentCard', value)}
            />
            <p className="agent-import-help">
              The complete Agent Card is normalized into the A2A descriptor and declared endpoints.
              Its name and version are aligned with the Agent draft.
            </p>
          </React.Fragment>
        ) : (
          <React.Fragment>
            <div className="agent-form-grid">
              <div>
                <div className="agent-import-protocol-label">Protocol</div>
                <Input
                  value={editor.customProtocol}
                  onChange={value =>
                    this.updateProtocolEditor(editorIndex, 'customProtocol', value)
                  }
                />
              </div>
              <div>
                <div className="agent-import-protocol-label">Protocol Version</div>
                <Input
                  value={editor.customProtocolVersion}
                  onChange={value =>
                    this.updateProtocolEditor(editorIndex, 'customProtocolVersion', value)
                  }
                />
              </div>
              <div>
                <div className="agent-import-protocol-label">Descriptor Media Type</div>
                <Input
                  value={editor.customDescriptorMediaType}
                  onChange={value =>
                    this.updateProtocolEditor(editorIndex, 'customDescriptorMediaType', value)
                  }
                />
              </div>
              <div>
                <div className="agent-import-protocol-label">Endpoint Source Order</div>
                <Select
                  value={editor.endpointSourceMode}
                  dataSource={[
                    { value: 'declared-runtime', label: 'DECLARED → RUNTIME' },
                    { value: 'runtime-declared', label: 'RUNTIME → DECLARED' },
                    { value: 'declared-only', label: 'DECLARED' },
                    { value: 'runtime-only', label: 'RUNTIME' },
                  ]}
                  style={{ width: '100%' }}
                  onChange={value =>
                    this.updateProtocolEditor(editorIndex, 'endpointSourceMode', value)
                  }
                />
              </div>
            </div>
            <div className="agent-import-protocol-label agent-field-spacing">Native Descriptor</div>
            <Input.TextArea
              value={editor.customNativeDescriptor}
              rows={12}
              onChange={value =>
                this.updateProtocolEditor(editorIndex, 'customNativeDescriptor', value)
              }
            />
            <div className="declared-endpoint-title">
              <span>Declared Endpoints</span>
              <Button
                size="small"
                onClick={() =>
                  this.updateProtocolEditor(editorIndex, 'declaredEndpoints', [
                    ...editor.declaredEndpoints,
                    { uri: '', transport: 'HTTP' },
                  ])
                }
              >
                Add Endpoint
              </Button>
            </div>
            {editor.declaredEndpoints.map((endpoint, endpointIndex) => (
              <div className="declared-endpoint-row" key={endpointIndex}>
                <Input
                  value={endpoint.uri}
                  placeholder="Endpoint URI"
                  onChange={value =>
                    this.updateProtocolEndpoint(editorIndex, endpointIndex, 'uri', value)
                  }
                />
                <Input
                  value={endpoint.transport}
                  placeholder="HTTP"
                  onChange={value =>
                    this.updateProtocolEndpoint(editorIndex, endpointIndex, 'transport', value)
                  }
                />
                <Button
                  warning
                  text
                  onClick={() =>
                    this.updateProtocolEditor(
                      editorIndex,
                      'declaredEndpoints',
                      editor.declaredEndpoints.filter(
                        (item, currentIndex) => currentIndex !== endpointIndex
                      )
                    )
                  }
                >
                  Delete
                </Button>
              </div>
            ))}
          </React.Fragment>
        )}
      </div>
    );
  }

  renderMultiProtocolForm() {
    const { protocolEditors, activeProtocolIndex } = this.state;
    const moveProtocol = offset => {
      const targetIndex = activeProtocolIndex + offset;
      if (targetIndex < 0 || targetIndex >= protocolEditors.length) {
        return;
      }
      const next = [...protocolEditors];
      [next[activeProtocolIndex], next[targetIndex]] = [
        next[targetIndex],
        next[activeProtocolIndex],
      ];
      this.setState({ protocolEditors: next, activeProtocolIndex: targetIndex });
    };
    return (
      <Card title="Protocol Configuration" contentHeight="auto" className="agent-editor-card">
        <div className="agent-protocol-heading">
          <div>
            <strong>Supported Protocols</strong>
            <p>
              Protocol order is significant. The first compatible protocol is the default preference
              used by discovery clients.
            </p>
          </div>
          <Button
            size="small"
            onClick={() =>
              this.setState({
                protocolEditors: [...protocolEditors, createStructuredProtocolEditor('custom')],
                activeProtocolIndex: protocolEditors.length,
              })
            }
          >
            Add Protocol
          </Button>
        </div>
        <Tab
          shape="wrapped"
          activeKey={String(activeProtocolIndex)}
          onChange={key => this.setState({ activeProtocolIndex: Number(key) })}
        >
          {protocolEditors.map((editor, index) => (
            <Tab.Item
              key={String(index)}
              title={
                editor.protocolEditorKind === 'a2a'
                  ? 'A2A'
                  : editor.customProtocol || `Protocol ${index + 1}`
              }
            >
              <div className="agent-protocol-order">
                <span>Preference {index + 1}</span>
                <div>
                  <Button text disabled={index === 0} onClick={() => moveProtocol(-1)}>
                    Move Forward
                  </Button>
                  <Button
                    text
                    disabled={index === protocolEditors.length - 1}
                    onClick={() => moveProtocol(1)}
                  >
                    Move Backward
                  </Button>
                  <Button
                    text
                    warning
                    disabled={protocolEditors.length === 1}
                    onClick={() => {
                      const next = protocolEditors.filter((item, itemIndex) => itemIndex !== index);
                      this.setState({
                        protocolEditors: next,
                        activeProtocolIndex: Math.min(index, next.length - 1),
                      });
                    }}
                  >
                    Remove Protocol
                  </Button>
                </div>
              </div>
              {this.renderStructuredProtocolFields(editor, index)}
            </Tab.Item>
          ))}
        </Tab>
      </Card>
    );
  }

  renderProtocolForm() {
    const { protocolEditorKind, declaredEndpoints } = this.state;
    const protocolOptions = [
      { value: 'a2a', label: 'A2A' },
      { value: 'custom', label: 'Custom Protocol' },
    ];
    if (protocolEditorKind === 'raw') {
      protocolOptions.push({ value: 'raw', label: 'Advanced Raw Configuration' });
    }
    return (
      <Card title="Protocol Configuration" contentHeight="auto" className="agent-editor-card">
        <Form.Item label="Protocol Type">
          <Select
            name="protocolEditorKind"
            dataSource={protocolOptions}
            onChange={value => this.setState({ protocolEditorKind: value })}
          />
        </Form.Item>
        {protocolEditorKind === 'a2a' && (
          <Form.Item
            label="Complete Agent Card"
            required
            validator={this.required}
            help="Agent name/version and declared endpoints are derived automatically."
          >
            <Input.TextArea name="agentCard" rows={22} />
          </Form.Item>
        )}
        {protocolEditorKind === 'custom' && (
          <React.Fragment>
            <div className="agent-form-grid">
              <Form.Item label="Protocol" required validator={this.required}>
                <Input name="customProtocol" />
              </Form.Item>
              <Form.Item label="Protocol Version">
                <Input name="customProtocolVersion" />
              </Form.Item>
              <Form.Item label="Descriptor Media Type" required validator={this.required}>
                <Input name="customDescriptorMediaType" />
              </Form.Item>
              <Form.Item label="Endpoint Source Order">
                <Select
                  name="endpointSourceMode"
                  dataSource={[
                    { value: 'declared-runtime', label: 'DECLARED → RUNTIME' },
                    { value: 'runtime-declared', label: 'RUNTIME → DECLARED' },
                    { value: 'declared-only', label: 'DECLARED' },
                    { value: 'runtime-only', label: 'RUNTIME' },
                  ]}
                />
              </Form.Item>
            </div>
            <Form.Item label="Native Descriptor" required validator={this.required}>
              <Input.TextArea name="customNativeDescriptor" rows={12} />
            </Form.Item>
            <div className="declared-endpoint-title">
              <span>Declared Endpoints</span>
              <Button
                size="small"
                onClick={() =>
                  this.setState({
                    declaredEndpoints: [...declaredEndpoints, { uri: '', transport: 'HTTP' }],
                  })
                }
              >
                Add Endpoint
              </Button>
            </div>
            {declaredEndpoints.map((endpoint, index) => (
              <div className="declared-endpoint-row" key={index}>
                <Input
                  value={endpoint.uri}
                  placeholder="Endpoint URI"
                  onChange={value => this.updateEndpoint(index, 'uri', value)}
                />
                <Input
                  value={endpoint.transport}
                  placeholder="HTTP"
                  onChange={value => this.updateEndpoint(index, 'transport', value)}
                />
                <Button
                  warning
                  text
                  onClick={() =>
                    this.setState({
                      declaredEndpoints: declaredEndpoints.filter(
                        (item, itemIndex) => itemIndex !== index
                      ),
                    })
                  }
                >
                  Delete
                </Button>
              </div>
            ))}
          </React.Fragment>
        )}
        {protocolEditorKind === 'raw' && (
          <Form.Item label="Call Interfaces" required validator={this.required}>
            <Input.TextArea name="callInterfaces" rows={22} />
          </Form.Item>
        )}
      </Card>
    );
  }

  renderActions() {
    const { mode, createPath, createStep, saving } = this.state;
    const guidedCreate = mode === 'create' && createPath === 'new';
    let primaryAction = null;
    if (!(mode === 'create' && createPath === 'choose')) {
      primaryAction =
        guidedCreate && createStep < 2 ? (
          <Button type="primary" onClick={this.advanceCreateStep}>
            Next
          </Button>
        ) : (
          <Button type="primary" loading={saving} onClick={this.handleSubmit}>
            {createPath === 'import' ? 'Import and Create' : 'Save'}
          </Button>
        );
    }
    return (
      <div className="agent-editor-actions">
        <div>
          {guidedCreate && createStep > 0 && (
            <Button onClick={() => this.setState({ createStep: createStep - 1 })}>Previous</Button>
          )}
          {mode === 'create' &&
            createPath !== 'choose' &&
            (createPath === 'import' || createStep === 0) && (
              <Button onClick={() => this.setState({ createPath: 'choose', createStep: 0 })}>
                Back to Creation Method
              </Button>
            )}
        </div>
        <div>
          <Button onClick={this.handleGoBack}>Cancel</Button>
          {primaryAction}
        </div>
      </div>
    );
  }

  render() {
    const { mode, createPath, loading, createStep, contentMode } = this.state;
    if (loading) {
      return <div style={{ padding: 40 }}>Loading...</div>;
    }
    const titles = {
      create: 'Create Agent',
      metadata: 'Edit Agent Metadata',
      'draft-create': 'Create Agent Draft',
      'draft-edit': 'Edit Agent Draft',
    };
    const guidedCreate = mode === 'create' && createPath === 'new';
    const directProtocolVisible = mode !== 'metadata' && contentMode === 'direct';

    return (
      <div className="new-agent-container">
        <PageTitle title={titles[mode]} />
        <span className="agent-editor-namespace">{this.namespaceId()}</span>
        {mode === 'create' && createPath === 'choose' && this.renderCreatePathSelection()}
        {mode === 'create' && createPath === 'import' && this.renderKnownProtocolImport()}
        {guidedCreate && (
          <Step current={createStep} shape="circle" className="agent-create-steps">
            <Step.Item title="Agent Metadata" />
            <Step.Item title="Initial Version" />
            <Step.Item title="Protocol Configuration" />
          </Step>
        )}
        {createPath !== 'choose' && createPath !== 'import' && (
          <Form field={this.field} labelCol={{ span: 5 }} wrapperCol={{ span: 17 }}>
            {(mode === 'metadata' || (guidedCreate && createStep === 0)) &&
              this.renderMetadataForm()}
            {(mode === 'draft-create' ||
              mode === 'draft-edit' ||
              (guidedCreate && createStep === 1)) &&
              this.renderVersionForm()}
            {directProtocolVisible && mode !== 'create' && this.renderProtocolForm()}
            {guidedCreate && createStep === 2 && this.renderMultiProtocolForm()}
          </Form>
        )}
        {this.renderActions()}
      </div>
    );
  }
}

export default NewAgent;
