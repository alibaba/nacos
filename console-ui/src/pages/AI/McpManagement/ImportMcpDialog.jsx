/*
 * Import MCP Server Dialog - extracted from McpManagement for modularity
 */
import React from 'react';
import PropTypes from 'prop-types';
import { Button, Dialog, Form, Input, Icon, Loading, Progress, Card, Balloon, Message, Table } from '@alifd/next';
import { getParams, request } from '@/globalLib';

class ImportMcpDialog extends React.Component {
    static propTypes = {
        visible: PropTypes.bool.isRequired,
        onClose: PropTypes.func.isRequired,
        onImported: PropTypes.func, // callback to refresh list after successful import
        locale: PropTypes.object,
    };

    static defaultProps = {
        onImported: () => { },
        locale: {},
    };

    DEFAULT_REGISTRY_URL = 'https://registry.modelcontextprotocol.io/v0.1/servers';

    constructor(props) {
        super(props);
        this.state = {
            importType: 'url',
            importData: this.DEFAULT_REGISTRY_URL,
            importOverrideExisting: false,
            importValidating: false,
            importExecuting: false,
            importValidationResult: null,
            importSelectedServerIds: [],
            importUrlCursor: '',
            importRegistrySearch: '',
            importAccumulatedServers: [],
            importNextCursor: null,
            importHoverKey: null,
            importCardsReady: false,
            // front-end pagination
            importPage: 1,
            importPageSize: 12,
            importProgressTotal: 0,
            importProgressCurrent: 0,
            importProgressSuccess: 0,
            importProgressFailed: 0,
            importFailedItems: [],
            // local details modal
            detailVisible: false,
            detailContent: '',
            detailTitle: '',
        };
    }

    // 统一关闭处理：关闭弹窗并刷新外部 MCP 列表
    handleClose = () => {
        try {
            this.props.onClose && this.props.onClose();
        } finally {
            this.props.onImported && this.props.onImported();
        }
    };

    componentDidUpdate(prevProps) {
        // When dialog opens, auto validate and load registry list
        if (!prevProps.visible && this.props.visible) {
            this.resetStateForOpen(() => this.validateImport());
        }
    }

    resetStateForOpen = (cb) => {
        this.setState(
            {
                importType: 'url',
                importData: this.DEFAULT_REGISTRY_URL,
                importOverrideExisting: false,
                importValidating: true,
                importExecuting: false,
                importValidationResult: null,
                importSelectedServerIds: [],
                importUrlCursor: '',
                importRegistrySearch: '',
                importAccumulatedServers: [],
                importNextCursor: null,
                importHoverKey: null,
                importCardsReady: false,
                importPage: 1,
                importProgressTotal: 0,
                importProgressCurrent: 0,
                importProgressSuccess: 0,
                importProgressFailed: 0,
                importFailedItems: [],
            },
            cb
        );
    };

    // 构建 endpointSpecification 字符串
    buildEndpointSpecByServer(server) {
        if (!server) return '';
        try {
            const proto = (server.protocol || server.frontProtocol || '').toLowerCase();
            // stdio 类型不需要 endpointSpecification
            if (proto === 'stdio') return '';

            let remoteCfg = server.remoteServerConfig;
            // 允许 remoteServerConfig 为字符串/对象/数组
            if (typeof remoteCfg === 'string') {
                try { remoteCfg = JSON.parse(remoteCfg); } catch (_) { /* ignore */ }
            }

            let first = remoteCfg?.frontEndpointConfigList[0];

            if (!first) return '';

            // 根据 McpEndpointSpec 定义转换字段：需要 { address, port } 字符串
            const raw = first || {};
            const endpointData = raw.endpointData || raw.address || '';
            const epProto = (raw.protocol || proto || '').toLowerCase();

            const parseHostPort = (s) => {
                if (!s || typeof s !== 'string') return { address: '', port: '' };
                let tmp = s.trim();
                // 去掉 scheme
                tmp = tmp.replace(/^[a-zA-Z][a-zA-Z0-9+.-]*:\/\//, '');
                // 去掉路径
                const slashIdx = tmp.indexOf('/');
                if (slashIdx >= 0) tmp = tmp.slice(0, slashIdx);
                // 处理 IPv6 格式 [host]:port
                if (tmp.startsWith('[')) {
                    const end = tmp.indexOf(']');
                    if (end > 0) {
                        const host = tmp.slice(1, end);
                        const rest = tmp.slice(end + 1);
                        const p = rest.startsWith(':') ? rest.slice(1) : '';
                        return { address: host, port: p };
                    }
                }
                const lastColon = tmp.lastIndexOf(':');
                if (lastColon > -1 && tmp.indexOf(':') === lastColon) {
                    // 单个冒号 host:port
                    const host = tmp.slice(0, lastColon);
                    const p = tmp.slice(lastColon + 1);
                    return { address: host, port: p };
                }
                return { address: tmp, port: '' };
            };

            let { address, port } = parseHostPort(endpointData);
            if (!port) {
                if (epProto === 'https' || epProto === 'wss') port = '443';
                else if (epProto === 'http' || epProto === 'ws') port = '80';
                else port = '';
            }

            const data = { address: String(address || ''), port: String(port || '') };
            return JSON.stringify({ type: 'DIRECT', data });
        } catch (e) {
            return '';
        }
    }

    validateImport = async () => {
        const { locale = {} } = this.props;
        const { importType, importData, importOverrideExisting, importAccumulatedServers, importRegistrySearch, importPage, importPageSize } = this.state;
        if (!importData) {
            Message.warning(locale.pleaseEnterImportData || '请输入导入数据');
            return;
        }
        this.setState({ importValidating: true });
        const namespaceId = getParams('namespace') || '';
        const limit = importType === 'url' ? Math.max(1, (importPage || 1) * (importPageSize || 12)) : undefined;
        const payload = {
            namespaceId,
            importType,
            data: importData,
            overrideExisting: !!importOverrideExisting,
            validateOnly: true,
            skipInvalid: true,
        };
        if (importType === 'url') {
            // Do not rely on server-side nextCursor. Use larger limit to fetch more.
            if (!Number.isNaN(limit) && limit > 0) payload.limit = limit;
            if (importRegistrySearch && String(importRegistrySearch).trim()) {
                payload.search = String(importRegistrySearch).trim();
            }
        }
        const result = await request({
            url: 'v3/console/ai/mcp/import/validate',
            type: 'post',
            data: payload,
            error: () => this.setState({ importValidating: false }),
        });
        if (result && result.code === 0) {
            const validation = result.data || {};
            // For URL import, we re-fetch from the beginning with a larger limit, so just use returned list
            const mergedServers = importType === 'url'
                ? (validation.servers || [])
                : (validation.servers || []);
            const mergedKeysSet = new Set((mergedServers || []).map(it => it.serverId || it.serverName).filter(Boolean));
            const prevSelected = this.state.importSelectedServerIds || [];
            const nextSelected = (prevSelected && prevSelected.length) ? prevSelected.filter(id => mergedKeysSet.has(id)) : [];
            this.setState({
                importValidationResult: { ...validation, servers: mergedServers },
                importSelectedServerIds: nextSelected,
                importAccumulatedServers: mergedServers,
                importNextCursor: validation.nextCursor || null,
            });
            if (!this.state.importCardsReady) {
                setTimeout(() => this.setState({ importCardsReady: true }), 0);
            }
        } else {
            Message.error(result?.message || '校验失败');
        }
        this.setState({ importValidating: false });
    };

    // 加载更多：前端分页，增大 limit 重新获取
    handleLoadMore = () => {
        if (this.state.importValidating) return;
        this.setState(
            prev => ({ importPage: (prev.importPage || 1) + 1 }),
            () => this.validateImport()
        );
    };

    // 逐个创建导入
    executeImportIndividually = async (importAllValid) => {
        const { importValidationResult, importSelectedServerIds } = this.state;
        const servers = (importValidationResult && importValidationResult.servers) || [];
        if (!servers.length) {
            Message.notice(this.props.locale.noData || '暂无可导入的服务');
            return;
        }
        let targets = [];
        if (importAllValid) {
            targets = servers.filter(it => (it.status || '').toLowerCase() === 'valid');
        } else {
            const set = new Set(importSelectedServerIds || []);
            targets = servers.filter(it => set.has(it.serverId || it.serverName));
        }
        if (!targets.length) {
            Message.warning(this.props.locale.pleaseSelect || '请先选择要导入的服务');
            return;
        }

        this.setState({
            importExecuting: true,
            importProgressTotal: targets.length,
            importProgressCurrent: 0,
            importProgressSuccess: 0,
            importProgressFailed: 0,
            importFailedItems: [],
        });

        let success = 0, failed = 0, current = 0;
        const failedItems = [];
        for (const item of targets) {
            current += 1;
            const server = item.server || {};
            try {
                const serverSpec = JSON.stringify(server);
                const toolSpec = server.toolSpec ? JSON.stringify(server.toolSpec) : '';
                const endpointSpec = this.buildEndpointSpecByServer(server);
                const res = await request({
                    url: 'v3/console/ai/mcp',
                    type: 'post',
                    data: { serverSpecification: serverSpec, toolSpecification: toolSpec, endpointSpecification: endpointSpec },
                });
                if (res && res.code === 0) {
                    success += 1;
                } else {
                    failed += 1;
                    failedItems.push({ name: server.name || item.serverName || item.serverId, reason: res?.message || 'unknown error' });
                }
            } catch (e) {
                failed += 1;
                failedItems.push({ name: server.name || item.serverName || item.serverId, reason: e?.message || 'exception' });
            }
            this.setState({ importProgressCurrent: current, importProgressSuccess: success, importProgressFailed: failed, importFailedItems: failedItems });
        }

    this.setState({ importExecuting: false });
        Dialog.alert({
            title: this.props.locale.importResult || '导入结果',
            style: { width: 'clamp(420px, 86vw, 640px)' },
            content: (
                <div>
                    <div style={{ marginBottom: 8 }}>{`成功 ${success} 个，失败 ${failed} 个`}</div>
                    {failedItems && failedItems.length ? (
                        <div style={{ maxHeight: 260, overflow: 'auto', marginTop: 8 }}>
                            <Table
                                dataSource={(failedItems || []).map((it, idx) => ({ key: idx, name: it.name || '--', status: 'failed', reason: it.reason || '' }))}
                                size="small"
                                hasBorder={false}
                                primaryKey="key"
                                stickyHeader
                            >
                                <Table.Column title={this.props.locale.name || '名称'} dataIndex="name" width={220} />
                                <Table.Column
                                    title={this.props.locale.status || '状态'}
                                    dataIndex="status"
                                    width={100}
                                    cell={val => (
                                        <span style={{ backgroundColor: '#fee2e2', color: '#991b1b', borderRadius: 12, padding: '2px 8px', fontSize: 12, lineHeight: '18px' }}>
                                            {this.props.locale.failed || '失败'}
                                        </span>
                                    )}
                                />
                                <Table.Column
                                    title={this.props.locale.reason || '原因'}
                                    dataIndex="reason"
                                    cell={val => <span style={{ whiteSpace: 'normal', wordBreak: 'break-all' }}>{val}</span>}
                                />
                            </Table>
                        </div>
                    ) : null}
                </div>
            ),
        });
    // 自动刷新弹窗内容，反映已成功导入项（即使部分失败）
    this.validateImport();
    if (failed === 0) this.handleClose();
    };

    // 通过后端批量接口执行导入（用于“导入全部”按钮）
    executeImportByBackend = async () => {
    const { importType, importData, importOverrideExisting, importUrlCursor, importRegistrySearch } = this.state;
        const { locale = {} } = this.props;
        if (!importData) {
            Message.warning(locale.pleaseEnterImportData || '请输入导入数据');
            return;
        }
        const namespaceId = getParams('namespace') || '';

        // 设置简易进度，直到服务端返回
        this.setState({
            importExecuting: true,
            importProgressTotal: 1,
            importProgressCurrent: 0,
            importProgressSuccess: 0,
            importProgressFailed: 0,
            importFailedItems: [],
        });

        const payload = {
            namespaceId,
            importType,
            data: importData,
            overrideExisting: !!importOverrideExisting,
            validateOnly: false,
            skipInvalid: true,
        };
        if (importType === 'url') {
            // 全量导入：后端按约定 limit = -1 代表抓取所有分页
            if (importUrlCursor) payload.cursor = importUrlCursor;
            payload.limit = -1;
            if (importRegistrySearch && String(importRegistrySearch).trim()) {
                payload.search = String(importRegistrySearch).trim();
            }
        }

        try {
            const result = await request({
                url: 'v3/console/ai/mcp/import/execute',
                type: 'post',
                data: payload,
            });
            this.setState({ importProgressCurrent: 1 });
            if (result && result.code === 0) {
                const data = result.data || {};
                const { successCount = 0, failedCount = 0, skippedCount = 0, results = [] } = data;
                const failedItems = (results || [])
                    .filter(r => (r && r.status === 'failed'))
                    .map(r => ({ name: r && (r.serverName || r.serverId), reason: r && r.errorMessage }));
                this.setState({
                    importProgressSuccess: successCount,
                    importProgressFailed: failedCount,
                    importFailedItems: failedItems,
                    importExecuting: false,
                });

                Dialog.alert({
                    title: locale.importResult || '导入结果',
                    style: { width: 'clamp(420px, 86vw, 640px)' },
                    content: (
                        <div>
                            <div style={{ marginBottom: 8 }}>{`成功 ${successCount} 个，失败 ${failedCount} 个，跳过 ${skippedCount} 个`}</div>
                            {Array.isArray(results) && results.length ? (
                                <div style={{ maxHeight: 320, overflow: 'auto', marginTop: 8 }}>
                                    <Table
                                        dataSource={(results || []).map((r, idx) => ({
                                            key: idx,
                                            name: (r && (r.serverName || r.serverId)) || '--',
                                            status: (r && r.status) || '--',
                                            reason: (r && (r.errorMessage || r.message)) || '',
                                        }))}
                                        size="small"
                                        hasBorder={false}
                                        primaryKey="key"
                                        stickyHeader
                                    >
                                        <Table.Column title={locale.name || '名称'} dataIndex="name" width={220} />
                                        <Table.Column
                                            title={locale.status || '状态'}
                                            dataIndex="status"
                                            width={100}
                                            cell={val => {
                                                const lower = String(val || '').toLowerCase();
                                                if (lower === 'success' || lower === 'succeed' || lower === 'ok') {
                                                    return (
                                                        <span style={{ backgroundColor: '#dcfce7', color: '#166534', borderRadius: 12, padding: '2px 8px', fontSize: 12, lineHeight: '18px' }}>
                                                            {locale.success || '成功'}
                                                        </span>
                                                    );
                                                }
                                                if (lower === 'skipped' || lower === 'skip') {
                                                    return (
                                                        <span style={{ backgroundColor: '#e5e7eb', color: '#374151', borderRadius: 12, padding: '2px 8px', fontSize: 12, lineHeight: '18px' }}>
                                                            {locale.skipped || '跳过'}
                                                        </span>
                                                    );
                                                }
                                                return (
                                                    <span style={{ backgroundColor: '#fee2e2', color: '#991b1b', borderRadius: 12, padding: '2px 8px', fontSize: 12, lineHeight: '18px' }}>
                                                        {locale.failed || '失败'}
                                                    </span>
                                                );
                                            }}
                                        />
                                        <Table.Column
                                            title={locale.reason || '原因'}
                                            dataIndex="reason"
                                            cell={val => <span style={{ whiteSpace: 'normal', wordBreak: 'break-all' }}>{val}</span>}
                                        />
                                    </Table>
                                </div>
                            ) : null}
                        </div>
                    ),
                });
                // 自动刷新弹窗内容，反映已成功导入项（即使部分失败）
                this.validateImport();
                if (!failedCount) this.handleClose();
            } else {
                this.setState({ importExecuting: false });
                Message.error(result?.message || '导入执行失败');
            }
        } catch (e) {
            this.setState({ importExecuting: false });
            Message.error(e?.message || '导入执行异常');
        }
    };

    // For card selection
    toggleRegistryCardSelection = (serverKey, checked) => {
        const set = new Set(this.state.importSelectedServerIds || []);
        if (checked) set.add(serverKey); else set.delete(serverKey);
        this.setState({ importSelectedServerIds: Array.from(set) });
    };

    clearAllSelectionForRegistry = () => {
        this.setState({ importSelectedServerIds: [] });
    };

    openDetailModal = (item) => {
        const name = item.serverName || item.serverId || 'Unnamed';
        // prefer server object details
        const detailObj = item.server || item || {};
        const content = (() => {
            try { return JSON.stringify(detailObj, null, 2); } catch (e) { return String(detailObj); }
        })();
        this.setState({ detailVisible: true, detailContent: content, detailTitle: name });
    };

    closeDetailModal = () => this.setState({ detailVisible: false, detailContent: '', detailTitle: '' });

    render() {
        const { visible, onClose, locale = {} } = this.props;
        return (
            <>
                <Dialog
                    title={locale.importMcpServer || '导入 MCP Server'}
                    visible={visible}
                    footer={
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
                            <div>
                                <Button disabled={!(this.state.importSelectedServerIds && this.state.importSelectedServerIds.length) || this.state.importExecuting} onClick={this.clearAllSelectionForRegistry}>
                                    {locale.clearAll || '取消全选'}
                                </Button>
                            </div>
                            {this.state.importExecuting ? (
                                <div style={{ display: 'flex', alignItems: 'center', gap: 16, minWidth: 420, justifyContent: 'flex-end' }}>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: '#1d4ed8' }}>
                                        <Icon type="loading" size="small" />
                                        <span>{locale.importing || '导入中...'}</span>
                                    </div>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                                        <Progress percent={this.state.importProgressTotal ? Math.round((this.state.importProgressCurrent / this.state.importProgressTotal) * 100) : 0} shape="line" hasBorder={false} style={{ width: 240 }} />
                                        <span style={{ color: '#6b7280' }}>{`${this.state.importProgressCurrent}/${this.state.importProgressTotal}`}</span>
                                        {this.state.importProgressSuccess > 0 ? (
                                            <span style={{ backgroundColor: '#dcfce7', color: '#166534', borderRadius: 12, padding: '2px 8px', fontSize: 12, lineHeight: '18px' }}>
                                                {(locale.success || '成功')} {this.state.importProgressSuccess}
                                            </span>
                                        ) : null}
                                        {this.state.importProgressFailed > 0 ? (
                                            <span style={{ backgroundColor: '#fee2e2', color: '#991b1b', borderRadius: 12, padding: '2px 8px', fontSize: 12, lineHeight: '18px' }}>
                                                {(locale.failed || '失败')} {this.state.importProgressFailed}
                                            </span>
                                        ) : null}
                                    </div>
                                </div>
                            ) : (
                                <div style={{ display: 'flex', gap: 8 }}>
                                    <Button type="primary" disabled={this.state.importValidating} onClick={() => this.executeImportIndividually(false)}>
                                        {locale.importSelected || '导入选中'}
                                    </Button>
                                    <Button type="secondary" disabled={this.state.importValidating} onClick={this.executeImportByBackend}>
                                        {locale.importAll || '导入全部'}
                                    </Button>
                                </div>
                            )}
                        </div>
                    }
                    onClose={this.handleClose}
                    v2
                    style={{ width: 800 }}
                >
                    <Form labelAlign="left">
                        <Loading
                            visible={this.state.importValidating || this.state.importExecuting}
                            tip={(this.state.importExecuting ? (locale.importing || '导入中...') : (locale.loading || '加载中...'))}
                            style={{ width: '100%' }}
                        >
                            {this.state.importValidationResult ? (
                                <>
                                    <div style={{ display: 'flex', alignItems: 'center', marginBottom: 8 }}>
                                        <Input
                                            placeholder={locale.searchPlaceholder || '按名称关键词搜索（模糊）'}
                                            value={this.state.importRegistrySearch}
                                            innerAfter={
                                                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginRight: 6 }}>
                                                    {this.state.importRegistrySearch ? (
                                                        <span
                                                            title={locale.clear || '清除'}
                                                            onClick={() => !this.state.importValidating && this.setState({ importRegistrySearch: '', importUrlCursor: '', importAccumulatedServers: [], importPage: 1 }, () => this.validateImport())}
                                                            style={{
                                                                width: 20,
                                                                height: 20,
                                                                lineHeight: '20px',
                                                                textAlign: 'center',
                                                                borderRadius: '50%',
                                                                background: '#c4c6cf',
                                                                color: '#fff',
                                                                fontSize: 12,
                                                                cursor: this.state.importValidating ? 'not-allowed' : 'pointer',
                                                                userSelect: 'none',
                                                                display: 'inline-flex',
                                                                alignItems: 'center',
                                                                justifyContent: 'center',
                                                            }}
                                                        >
                                                            ×
                                                        </span>
                                                    ) : null}
                                                    <Icon
                                                        type="search"
                                                        size="small"
                                                        style={{ margin: '0 4px', color: '#9aa1a7', cursor: this.state.importValidating ? 'not-allowed' : 'pointer' }}
                                                        title={locale.search || '搜索'}
                                                        onClick={() => !this.state.importValidating && this.setState({ importUrlCursor: '', importAccumulatedServers: [], importPage: 1 }, () => this.validateImport())}
                                                    />
                                                </div>
                                            }
                                            onChange={val => this.setState({ importRegistrySearch: val })}
                                            onPressEnter={() => this.setState({ importUrlCursor: '', importAccumulatedServers: [], importPage: 1 }, () => this.validateImport())}
                                            style={{ flex: 1 }}
                                        />
                                    </div>

                                    {(() => {
                                        const serversList = (this.state.importValidationResult.servers || []);
                                        if (!serversList.length) {
                                            return (
                                                <div style={{ height: 220, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', color: '#8a8f8d', textAlign: 'center' }}>
                                                    <div style={{ marginBottom: 8 }}>
                                                        {locale.noSearchResult || '未找到匹配的结果'}
                                                    </div>
                                                    {this.state.importRegistrySearch ? (
                                                        <Button text disabled={this.state.importValidating} onClick={() => !this.state.importValidating && this.setState({ importRegistrySearch: '', importUrlCursor: '', importAccumulatedServers: [], importPage: 1 }, () => this.validateImport())}>
                                                            {locale.clearSearch || '清空搜索并重试'}
                                                        </Button>
                                                    ) : null}
                                                </div>
                                            );
                                        }
                                        return (
                                            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 20, width: '100%', alignItems: 'stretch', justifyItems: 'stretch' }}>
                                        {serversList.map((item, idx) => {
                                            const key = item.serverId || item.serverName;
                                            const name = item.serverName || item.serverId || 'Unnamed';
                                            const validationStatus = (item.status || '').toLowerCase();
                                            const checked = (this.state.importSelectedServerIds || []).includes(key);
                                            const server = item.server || {};
                                            const proto = server.frontProtocol || server.protocol || '--';
                                            const version = (server.versionDetail && server.versionDetail.version) || '--';
                                            const serverStatus = (server.status || '').toLowerCase();
                                            const errorText = (item.errors && item.errors.length) ? item.errors.join('; ') : '';
                                            const alreadyExists = !!item.exists || /already\s+exists/i.test(errorText);
                                            const disabled = validationStatus !== 'valid' || alreadyExists;
                                            const isInvalid = validationStatus === 'invalid';
                                            const desc = server.description || item.description || '';
                                            const repoUrl = (server.repository && (server.repository.url || server.repository.link))
                                                || server.repositoryUrl || server.repositoryURL || server.homepage || server.url || item.repositoryUrl || item.url || '';

                                            const borderColor = checked ? '#2563eb' : '#e6e7eb';
                                            const isHovered = this.state.importHoverKey === key;
                                            const isReady = this.state.importCardsReady;
                                            const activeHover = !disabled && isHovered;
                                            const baseTransform = !isReady ? 'translateY(6px)' : (checked || activeHover) ? 'translateY(-2px) scale(1.01)' : 'translateY(0)';
                                            const boxShadow = checked ? '0 8px 22px rgba(37,99,235,0.22)' : activeHover ? '0 8px 20px rgba(0,0,0,0.10)' : 'none';
                                            const transitionDelay = isReady ? `${Math.min(idx * 30, 240)}ms` : '0ms';
                                            const wrapperStyle = { cursor: disabled ? 'not-allowed' : 'pointer', width: '100%', height: '100%', display: 'flex' };

                                            return (
                                                <div key={key} style={{ minWidth: 0, height: '100%' }}>
                                                    <div
                                                        onClick={() => !disabled && this.toggleRegistryCardSelection(key, !checked)}
                                                        onMouseEnter={() => !disabled && this.setState({ importHoverKey: key })}
                                                        onMouseLeave={() => this.setState({ importHoverKey: null })}
                                                        style={wrapperStyle}
                                                    >
                                                        <Card
                                                            free
                                                            style={{
                                                                width: '100%',
                                                                height: '100%',
                                                                minHeight: 160,
                                                                display: 'flex',
                                                                flexDirection: 'column',
                                                                boxSizing: 'border-box',
                                                                border: `1px solid ${isInvalid ? '#d1d5db' : borderColor}`,
                                                                backgroundColor: isInvalid ? '#f3f4f6' : '#ffffff',
                                                                boxShadow,
                                                                opacity: isInvalid ? 1 : (disabled ? 0.7 : 1),
                                                                transform: baseTransform,
                                                                transition: 'transform 220ms ease, box-shadow 220ms ease, border-color 220ms ease, opacity 260ms ease',
                                                                transitionDelay,
                                                            }}
                                                        >
                                                            <Card.Header
                                                                style={{ backgroundColor: isInvalid ? '#f3f4f6' : undefined }}
                                                                title={
                                                                    <span title={name} style={{ fontWeight: 600, display: 'inline-block', maxWidth: 'calc(100% - 88px)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', color: isInvalid ? '#4b5563' : undefined }}>
                                                                        {name}
                                                                    </span>
                                                                }
                                                                subTitle={<span style={{ color: '#8a8f8d' }}>v{version}</span>}
                                                                extra={
                                                                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }} onClick={e => e.stopPropagation()}>
                                                                        {errorText ? (
                                                                            <Balloon.Tooltip trigger={<Icon type="prompt" size="small" style={{ color: '#f59e0b' }} />}>{errorText}</Balloon.Tooltip>
                                                                        ) : null}
                                                                        {repoUrl ? (
                                                                            <a href={repoUrl} target="_blank" rel="noopener noreferrer" onClick={e => e.stopPropagation()} style={{ color: '#2563eb' }}>
                                                                                {locale.details || '详情'}
                                                                            </a>
                                                                        ) : null}
                                                                    </div>
                                                                }
                                                            />
                                                            <Card.Divider />
                                                            <Card.Content style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
                                                                <div title={desc} style={{ color: '#666', lineHeight: '20px', overflow: 'hidden', display: '-webkit-box', WebkitLineClamp: 3, WebkitBoxOrient: 'vertical' }}>
                                                                    {desc || '--'}
                                                                </div>
                                                            </Card.Content>
                                                            <Card.Divider />
                                                            <div style={{ padding: '8px 12px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
                                                                <div style={{ color: '#111827' }}>
                                                                    <span style={{ color: '#6b7280', marginRight: 6 }}>{locale.mcpServerType || '服务类型'}:</span>
                                                                    <span style={{ color: '#2563eb' }}>{proto || '--'}</span>
                                                                </div>
                                                                <div style={{ color: '#111827', display: 'flex', alignItems: 'center', gap: 8 }}>
                                                                    <span style={{ color: '#6b7280' }}>{locale.status || '状态'}:</span>
                                                                    {serverStatus ? (
                                                                        <span style={{ backgroundColor: serverStatus === 'active' ? '#dcfce7' : '#fef3c7', color: serverStatus === 'active' ? '#166534' : '#92400e', borderRadius: 12, padding: '2px 8px', fontSize: 12, lineHeight: '18px' }}>
                                                                            {serverStatus}
                                                                        </span>
                                                                    ) : (
                                                                        <span style={{ color: '#9aa1a7' }}>--</span>
                                                                    )}
                                                                    {alreadyExists && (
                                                                        <span style={{ backgroundColor: '#e5e7eb', color: '#374151', borderRadius: 12, padding: '2px 8px', fontSize: 12, lineHeight: '18px' }}>
                                                                            {locale.alreadyImported || '已导入'}
                                                                        </span>
                                                                    )}
                                                                </div>
                                                            </div>
                                                        </Card>
                                                    </div>
                                                </div>
                                            );
                                        })}
                                            </div>
                                        );
                                    })()}
                                    {(() => {
                                        const servers = this.state.importAccumulatedServers || [];
                                        const page = this.state.importPage || 1;
                                        const size = this.state.importPageSize || 12;
                                        const requested = page * size;
                                        // 后端未返回 nextCursor 时，若返回数量 >= 请求数量，则认为可能还有更多
                                        const canLoadMore = (this.state.importType === 'url') && (this.state.importNextCursor || servers.length >= requested);
                                        return canLoadMore ? (
                                            <div style={{ textAlign: 'center', marginTop: 12 }}>
                                                <Button loading={this.state.importValidating} disabled={this.state.importValidating} onClick={this.handleLoadMore}>
                                                    {locale.loadMore || '加载更多'}
                                                </Button>
                                            </div>
                                        ) : null;
                                    })()}
                                </>
                            ) : (
                                this.state.importValidating ? (
                                    <div style={{ height: 220 }} />
                                ) : (
                                    <div style={{ height: 220, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#8a8f8d' }}>
                                        {locale.pubNoData || locale.noData || '暂无数据'}
                                    </div>
                                )
                            )}
                        </Loading>
                    </Form>
                </Dialog>
                {/* local details dialog */}
                {this.state.detailVisible ? (
                    <Dialog
                        key="mcp-detail"
                        title={this.state.detailTitle || (locale.details || '详情')}
                        visible
                        onClose={this.closeDetailModal}
                        v2
                        style={{ width: 720 }}
                        footer={false}
                    >
                        <div style={{ maxHeight: 420, overflow: 'auto', background: '#f9fafb', border: '1px solid #e5e7eb', padding: 12, borderRadius: 4 }}>
                            <pre style={{ margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{this.state.detailContent}</pre>
                        </div>
                    </Dialog>
                ) : null}
            </>
        );
    }
}

export default ImportMcpDialog;
