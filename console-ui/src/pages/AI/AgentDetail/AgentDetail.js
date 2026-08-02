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
  Input,
  Loading,
  Message,
  Pagination,
  Select,
  Tab,
  Table,
  Tag,
} from '@alifd/next';
import { getParams } from '@/globalLib';
import { agentApi } from '../agent-api';
import {
  getProtocols,
  getVersionActions,
  namingDetailPath,
  runtimeCacheKey,
  usesRuntimeSource,
} from '../agent-console-model';
import '../NewAgent/NewAgent.scss';

const VERSION_STATUSES = ['draft', 'reviewing', 'reviewed', 'online', 'offline'];

const ACTION_LABELS = {
  submit: 'Submit for Review',
  publish: 'Publish',
  forcePublish: 'Force Publish',
  redraft: 'Return to Draft',
  online: 'Bring Online',
  offline: 'Take Offline',
};

@ConfigProvider.config
class AgentDetail extends React.Component {
  static displayName = 'AgentDetail';

  static propTypes = {
    history: PropTypes.object,
    location: PropTypes.object,
  };

  state = {
    loading: true,
    actionLoading: false,
    runtimeLoading: false,
    overview: null,
    versionPage: null,
    versionDetail: null,
    selectedVersion: getParams('version') || '',
    selectedProtocol: '',
    runtimeCache: {},
    versionStatus: '',
    versionPageNo: 1,
    labelsText: '{}',
  };

  componentDidMount() {
    this.loadOverview();
    this.loadVersions();
  }

  componentDidUpdate(prevProps) {
    if (prevProps.location && prevProps.location.search !== this.props.location.search) {
      // Route identity changed; clear Version-scoped state before loading the new Agent.
      // eslint-disable-next-line react/no-did-update-set-state
      this.setState(
        {
          selectedVersion: getParams('version') || '',
          selectedProtocol: '',
          runtimeCache: {},
        },
        () => {
          this.loadOverview();
          this.loadVersions();
        }
      );
    }
  }

  namespaceId = () => getParams('namespace') || 'public';

  agentName = () => getParams('name') || '';

  loadOverview = () => {
    const agentName = this.agentName();
    if (!agentName) {
      Message.error('Agent name is required');
      this.setState({ loading: false });
      return Promise.resolve(null);
    }
    this.setState({ loading: true });
    return agentApi
      .get({ namespaceId: this.namespaceId(), agentName })
      .then(overview => {
        const labels = { ...((overview.agent.versionInfo || {}).labels || {}) };
        delete labels.latest;
        const selectedVersion =
          this.state.selectedVersion ||
          (overview.agent.versionCatalog && overview.agent.versionCatalog.latestVersion) ||
          (overview.agent.versionInfo && overview.agent.versionInfo.editingVersion) ||
          (overview.agent.versionInfo && overview.agent.versionInfo.reviewingVersion) ||
          ((overview.versionPage.pageItems || [])[0] || {}).version ||
          '';
        this.setState({
          overview,
          labelsText: JSON.stringify(labels, null, 2),
          selectedVersion,
          loading: false,
        });
        if (selectedVersion) {
          return this.loadVersion(selectedVersion);
        }
        return overview;
      })
      .catch(error => {
        this.setState({ overview: null, loading: false });
        Message.error(error.message);
        return null;
      });
  };

  loadVersions = (pageNo = this.state.versionPageNo) => {
    return agentApi
      .versions({
        namespaceId: this.namespaceId(),
        agentName: this.agentName(),
        status: this.state.versionStatus || undefined,
        pageNo,
        pageSize: 10,
      })
      .then(versionPage => this.setState({ versionPage, versionPageNo: pageNo }))
      .catch(error => Message.error(error.message));
  };

  loadVersion = version => {
    this.setState({
      loading: true,
      selectedVersion: version,
      selectedProtocol: '',
      runtimeCache: {},
    });
    return agentApi
      .version({
        namespaceId: this.namespaceId(),
        agentName: this.agentName(),
        version,
      })
      .then(versionDetail => {
        const protocols = getProtocols(versionDetail.callInterfaces);
        const selectedProtocol = protocols[0] || '';
        this.setState({ versionDetail, selectedProtocol, loading: false });
        if (selectedProtocol) {
          this.loadRuntime(version, selectedProtocol);
        }
        return versionDetail;
      })
      .catch(error => {
        this.setState({ versionDetail: null, loading: false });
        Message.error(error.message);
      });
  };

  loadRuntime = (version, protocol, force = false) => {
    const key = runtimeCacheKey(version, protocol);
    if (!force && this.state.runtimeCache[key]) {
      return Promise.resolve(this.state.runtimeCache[key]);
    }
    this.setState({ runtimeLoading: true });
    return agentApi
      .runtime({
        namespaceId: this.namespaceId(),
        agentName: this.agentName(),
        version,
        protocol,
      })
      .then(view => {
        this.setState(state => ({
          runtimeCache: { ...state.runtimeCache, [key]: view },
          runtimeLoading: false,
        }));
        return view;
      })
      .catch(error => {
        this.setState({ runtimeLoading: false });
        Message.error(error.message);
      });
  };

  selectProtocol = protocol => {
    const { versionDetail } = this.state;
    this.setState({ selectedProtocol: protocol });
    if (versionDetail) {
      this.loadRuntime(versionDetail.version, protocol);
    }
  };

  editPath = (mode, version) => {
    const params = new URLSearchParams({
      namespace: this.namespaceId(),
      name: this.agentName(),
      mode,
    });
    if (version) {
      params.set('version', version);
    }
    this.props.history.push(`/newAgent?${params.toString()}`);
  };

  runAction = action => {
    const { versionDetail } = this.state;
    if (!versionDetail) {
      return;
    }
    const data = {
      namespaceId: this.namespaceId(),
      agentName: this.agentName(),
      version: versionDetail.version,
    };
    this.setState({ actionLoading: true });
    agentApi[action](data)
      .then(() => {
        Message.success('Version action completed');
        return this.loadOverview();
      })
      .then(() => this.loadVersions(this.state.versionPageNo))
      .catch(error => Message.error(error.message))
      .finally(() => this.setState({ actionLoading: false }));
  };

  deleteDraft = () => {
    const { versionDetail } = this.state;
    if (!versionDetail) {
      return;
    }
    this.setState({ actionLoading: true });
    agentApi
      .deleteDraft({
        namespaceId: this.namespaceId(),
        agentName: this.agentName(),
        version: versionDetail.version,
      })
      .then(() => {
        Message.success('Draft deleted');
        this.setState({ selectedVersion: '', versionDetail: null }, () => {
          this.loadOverview();
          this.loadVersions(1);
        });
      })
      .catch(error => Message.error(error.message))
      .finally(() => this.setState({ actionLoading: false }));
  };

  updateLabels = () => {
    let labels;
    try {
      labels = JSON.parse(this.state.labelsText);
      if (!labels || Array.isArray(labels) || typeof labels !== 'object') {
        throw new Error('Labels must be a JSON object');
      }
      if (Object.prototype.hasOwnProperty.call(labels, 'latest')) {
        throw new Error('The latest label is managed by the server');
      }
    } catch (error) {
      Message.error(error.message);
      return;
    }
    agentApi
      .updateLabels({
        namespaceId: this.namespaceId(),
        agentName: this.agentName(),
        labels: JSON.stringify(labels),
      })
      .then(() => {
        Message.success('Labels updated');
        this.loadOverview();
      })
      .catch(error => Message.error(error.message));
  };

  renderRuntime() {
    const { versionDetail, selectedProtocol, runtimeCache, runtimeLoading } = this.state;
    if (!versionDetail || !selectedProtocol) {
      return <div>No Call Interface selected</div>;
    }
    const callInterface = (versionDetail.callInterfaces || []).find(
      item => item.protocol === selectedProtocol
    );
    const view = runtimeCache[runtimeCacheKey(versionDetail.version, selectedProtocol)];
    const items = ((view && view.runtimeEndpointSnapshot.items) || []).map(item => ({
      ...item,
      runtimeKey: `${item.endpoint.uri}@@${item.endpoint.transport}`,
    }));
    return (
      <Card title="Runtime Endpoints (read-only)" contentHeight="auto" style={{ marginTop: 16 }}>
        {!usesRuntimeSource(callInterface) && (
          <div
            style={{
              marginBottom: 12,
              padding: 12,
              color: '#ad6800',
              background: '#fffbe6',
              border: '1px solid #ffe58f',
            }}
          >
            This version does not enable RUNTIME. Registered endpoints will not enter its Discover
            result.
          </div>
        )}
        <Button
          size="small"
          loading={runtimeLoading}
          onClick={() => this.loadRuntime(versionDetail.version, selectedProtocol, true)}
          style={{ marginBottom: 12 }}
        >
          Refresh
        </Button>
        <Table dataSource={items} size="small" primaryKey="runtimeKey">
          <Table.Column title="URI" cell={(value, index, item) => item.endpoint.uri} />
          <Table.Column title="Transport" cell={(value, index, item) => item.endpoint.transport} />
          <Table.Column title="State" dataIndex="state" cell={value => <Tag>{value}</Tag>} />
          <Table.Column
            title="Bindings"
            cell={(value, index, item) =>
              (item.bindings || [])
                .map(binding => `${binding.runtimeVersion} -> ${binding.versionRange}`)
                .join(', ') || '--'
            }
          />
        </Table>
        {view && view.namingServiceRef && (
          <Button
            style={{ marginTop: 12 }}
            onClick={() => this.props.history.push(namingDetailPath(view.namingServiceRef))}
          >
            Naming: {view.namingServiceRef.groupName}
            {'@@'}
            {view.namingServiceRef.serviceName}
          </Button>
        )}
        <div style={{ color: '#999', marginTop: 8 }}>
          Enable or disable Runtime instances on the Naming service detail page.
        </div>
      </Card>
    );
  }

  render() {
    const {
      loading,
      actionLoading,
      overview,
      versionPage,
      versionDetail,
      selectedProtocol,
      selectedVersion,
      versionStatus,
      versionPageNo,
      labelsText,
    } = this.state;
    if (loading && !overview) {
      return <Loading visible style={{ width: '100%' }} />;
    }
    if (!overview) {
      return <Card contentHeight="auto">Agent not found</Card>;
    }
    const { agent } = overview;
    const protocols = getProtocols((versionDetail && versionDetail.callInterfaces) || []);
    const actions = getVersionActions(versionDetail && versionDetail.status);

    return (
      <div className="agent-detail-page">
        <section className="agent-detail-hero">
          <div className="agent-detail-hero-toolbar">
            <Button text onClick={() => this.props.history.push('/agentManagement')}>
              ← Back to List
            </Button>
            <div>
              <Button onClick={() => this.editPath('metadata')}>Edit Metadata</Button>
              <Button type="primary" onClick={() => this.editPath('draft-create')}>
                Create Draft
              </Button>
            </div>
          </div>
          <div className="agent-detail-identity">
            <div className="agent-detail-avatar">🤖</div>
            <div className="agent-detail-title">
              <div>
                <h1>{agent.displayName || agent.agentName}</h1>
                <Tag type="primary">{agent.status}</Tag>
                <Tag>{agent.scope || '--'}</Tag>
              </div>
              <code>{agent.agentName}</code>
              <p>{agent.description || '--'}</p>
            </div>
            <Select
              value={selectedVersion}
              onChange={this.loadVersion}
              dataSource={((versionPage && versionPage.pageItems) || []).map(version => ({
                value: version.version,
                label: `${version.version} · ${version.status}`,
              }))}
              style={{ width: 240 }}
            />
          </div>
          {versionDetail && (
            <div className="agent-version-action-bar">
              <div>
                <strong>{versionDetail.version}</strong>
                <Tag>{versionDetail.status}</Tag>
                <span>{versionDetail.changeDescription || 'No change description'}</span>
              </div>
              <div>
                {actions.map(action => {
                  if (action === 'editDraft') {
                    return (
                      <Button
                        key={action}
                        size="small"
                        onClick={() => this.editPath('draft-edit', versionDetail.version)}
                      >
                        Edit Draft
                      </Button>
                    );
                  }
                  if (action === 'deleteDraft') {
                    return (
                      <Button
                        key={action}
                        warning
                        size="small"
                        loading={actionLoading}
                        onClick={this.deleteDraft}
                      >
                        Delete Draft
                      </Button>
                    );
                  }
                  return (
                    <Button
                      key={action}
                      type={action === 'submit' || action === 'publish' ? 'primary' : 'normal'}
                      size="small"
                      loading={actionLoading}
                      onClick={() => this.runAction(action)}
                    >
                      {ACTION_LABELS[action]}
                    </Button>
                  );
                })}
              </div>
            </div>
          )}
        </section>

        <div className="agent-detail-grid">
          <main>
            <Card
              title={`Supported Protocols (${protocols.length})`}
              contentHeight="auto"
              className="agent-protocol-card"
            >
              {versionDetail && protocols.length > 0 ? (
                <Tab shape="wrapped" activeKey={selectedProtocol} onChange={this.selectProtocol}>
                  {protocols.map(protocol => {
                    const callInterface = (versionDetail.callInterfaces || []).find(
                      item => item.protocol === protocol
                    );
                    return (
                      <Tab.Item key={protocol} title={protocol.toUpperCase()}>
                        <div className="agent-protocol-summary">
                          <div>
                            <span>Protocol</span>
                            <strong>{callInterface.protocol}</strong>
                          </div>
                          <div>
                            <span>Protocol Version</span>
                            <strong>{callInterface.protocolVersion || '--'}</strong>
                          </div>
                          <div>
                            <span>Descriptor Media Type</span>
                            <strong>{callInterface.descriptorMediaType}</strong>
                          </div>
                          <div>
                            <span>Endpoint Source Order</span>
                            <strong>{(callInterface.endpointSourceOrder || []).join(' → ')}</strong>
                          </div>
                        </div>
                        <div className="agent-declared-endpoints">
                          <strong>Declared Endpoints</strong>
                          {(callInterface.declaredEndpoints || []).length === 0 ? (
                            <p>--</p>
                          ) : (
                            (callInterface.declaredEndpoints || []).map(endpoint => (
                              <div key={`${endpoint.uri}@@${endpoint.transport}`}>
                                <code>{endpoint.uri}</code>
                                <Tag>{endpoint.transport}</Tag>
                              </div>
                            ))
                          )}
                        </div>
                        <pre className="agent-native-descriptor">
                          {JSON.stringify(callInterface.nativeDescriptor, null, 2)}
                        </pre>
                      </Tab.Item>
                    );
                  })}
                </Tab>
              ) : (
                <p>No Call Interface available</p>
              )}
            </Card>

            {this.renderRuntime()}

            <Card title="Version History" contentHeight="auto" style={{ marginTop: 16 }}>
              <Select
                value={versionStatus}
                onChange={value =>
                  this.setState({ versionStatus: value }, () => this.loadVersions(1))
                }
                dataSource={[
                  { value: '', label: 'ALL' },
                  ...VERSION_STATUSES.map(status => ({ value: status, label: status })),
                ]}
                style={{ width: 160, marginBottom: 12 }}
              />
              <Table
                dataSource={(versionPage && versionPage.pageItems) || []}
                size="small"
                primaryKey="version"
              >
                <Table.Column
                  title="Version"
                  dataIndex="version"
                  cell={value => <a onClick={() => this.loadVersion(value)}>{value}</a>}
                />
                <Table.Column title="Status" dataIndex="status" />
                <Table.Column title="Change Description" dataIndex="changeDescription" />
              </Table>
              {versionPage && versionPage.pagesAvailable > 1 && (
                <Pagination
                  current={versionPageNo}
                  total={versionPage.totalCount}
                  pageSize={10}
                  onChange={this.loadVersions}
                />
              )}
            </Card>
          </main>

          <aside>
            <Card title="Agent Metadata" contentHeight="auto">
              <dl className="agent-metadata-list">
                <div>
                  <dt>Owner</dt>
                  <dd>{agent.owner || '--'}</dd>
                </div>
                <div>
                  <dt>Provider</dt>
                  <dd>{(agent.provider && agent.provider.name) || '--'}</dd>
                </div>
                <div>
                  <dt>Tags</dt>
                  <dd>{(agent.tags || []).join(', ') || '--'}</dd>
                </div>
                <div>
                  <dt>Latest Version</dt>
                  <dd>{(agent.versionCatalog && agent.versionCatalog.latestVersion) || '--'}</dd>
                </div>
                <div>
                  <dt>Online Versions</dt>
                  <dd>{(agent.versionInfo && agent.versionInfo.onlineCnt) || 0}</dd>
                </div>
              </dl>
            </Card>
            <Card title="Custom Version Labels" contentHeight="auto" style={{ marginTop: 16 }}>
              <Input.TextArea
                value={labelsText}
                rows={5}
                onChange={value => this.setState({ labelsText: value })}
              />
              <Button type="primary" style={{ marginTop: 8 }} onClick={this.updateLabels}>
                Save Labels
              </Button>
              <div style={{ color: '#999', marginTop: 8 }}>
                The latest label is managed by the server.
              </div>
            </Card>
          </aside>
        </div>
      </div>
    );
  }
}

export default AgentDetail;
