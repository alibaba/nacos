/*
 * Import MCP Server Dialog - source driven AI resource import.
 */
import React from 'react';
import PropTypes from 'prop-types';
import {
    Balloon,
    Button,
    Card,
    Checkbox,
    Dialog,
    Form,
    Icon,
    Input,
    Loading,
    Message,
    Select,
    Switch,
    Table,
} from '@alifd/next';
import { getParams } from '@/globalLib';
import AiResourceImportService from '../services/AiResourceImportService';

const RESOURCE_TYPE_MCP = 'mcp';
const DEFAULT_PAGE_SIZE = 12;

class ImportMcpDialog extends React.Component {
    static propTypes = {
        visible: PropTypes.bool.isRequired,
        onClose: PropTypes.func.isRequired,
        onImported: PropTypes.func,
        locale: PropTypes.object,
    };

    static defaultProps = {
        onImported: () => {},
        locale: {},
    };

    constructor(props) {
        super(props);
        this.state = {
            sources: [],
            selectedSourceId: '',
            sourceLoading: false,
            importValidating: false,
            importExecuting: false,
            importCandidates: [],
            importNextCursor: '',
            importHasMore: false,
            importRegistrySearch: '',
            importSelectedItemKeys: [],
            importValidationResult: null,
            importValidationToken: '',
            importOverrideExisting: false,
            importSkipInvalid: true,
            importHoverKey: null,
            importCardsReady: false,
            detailVisible: false,
            detailContent: '',
            detailTitle: '',
        };
    }

    componentDidUpdate(prevProps) {
        if (!prevProps.visible && this.props.visible) {
            this.resetStateForOpen(this.loadSources);
        }
    }

    resetStateForOpen = cb => {
        this.setState(
            {
                sources: [],
                selectedSourceId: '',
                sourceLoading: false,
                importValidating: false,
                importExecuting: false,
                importCandidates: [],
                importNextCursor: '',
                importHasMore: false,
                importRegistrySearch: '',
                importSelectedItemKeys: [],
                importValidationResult: null,
                importValidationToken: '',
                importOverrideExisting: false,
                importSkipInvalid: true,
                importHoverKey: null,
                importCardsReady: false,
                detailVisible: false,
                detailContent: '',
                detailTitle: '',
            },
            cb
        );
    };

    handleClose = () => {
        try {
            this.props.onClose && this.props.onClose();
        } finally {
            this.props.onImported && this.props.onImported();
        }
    };

    loadSources = async () => {
        const { locale = {} } = this.props;
        this.setState({ sourceLoading: true });
        try {
            const result = await AiResourceImportService.listSources({
                resourceType: RESOURCE_TYPE_MCP,
            });
            if (result && result.code === 0) {
                const sources = (result.data || []).filter(source => source && source.enabled);
                const selectedSourceId = sources[0] ? sources[0].sourceId : '';
                this.setState(
                    {
                        sources,
                        selectedSourceId,
                        sourceLoading: false,
                    },
                    () => {
                        if (selectedSourceId) {
                            this.searchCandidates();
                        }
                    }
                );
                return;
            }
            Message.error(result?.message || locale.loadFailed || '加载失败');
        } catch (e) {
            Message.error(e?.message || locale.loadFailed || '加载失败');
        }
        this.setState({ sourceLoading: false });
    };

    handleSourceChange = selectedSourceId => {
        this.setState(
            {
                selectedSourceId,
                importCandidates: [],
                importNextCursor: '',
                importHasMore: false,
                importSelectedItemKeys: [],
                importValidationResult: null,
                importValidationToken: '',
                importCardsReady: false,
            },
            this.searchCandidates
        );
    };

    searchCandidates = async (append = false) => {
        const { locale = {} } = this.props;
        const { selectedSourceId, importRegistrySearch, importNextCursor } = this.state;
        if (!selectedSourceId) {
            return;
        }
        this.setState({ importValidating: true });
        const payload = {
            namespaceId: getParams('namespace') || '',
            resourceType: RESOURCE_TYPE_MCP,
            sourceId: selectedSourceId,
            query: importRegistrySearch && String(importRegistrySearch).trim(),
            limit: DEFAULT_PAGE_SIZE,
        };
        if (append && importNextCursor) {
            payload.cursor = importNextCursor;
        }
        try {
            const result = await AiResourceImportService.search(payload);
            if (result && result.code === 0) {
                const data = result.data || {};
                const incoming = data.items || [];
                const candidates = append
                    ? this.mergeCandidates(this.state.importCandidates, incoming)
                    : incoming;
                this.setState({
                    importCandidates: candidates,
                    importNextCursor: data.nextCursor || '',
                    importHasMore: !!data.hasMore,
                    importSelectedItemKeys: candidates.map(this.getImportItemKey),
                    importValidationResult: null,
                    importValidationToken: '',
                    importCardsReady: true,
                    importValidating: false,
                });
                return;
            }
            Message.error(result?.message || locale.searchFailed || '搜索失败');
        } catch (e) {
            Message.error(e?.message || locale.searchFailed || '搜索失败');
        }
        this.setState({ importValidating: false });
    };

    mergeCandidates = (current, incoming) => {
        const result = [...(current || [])];
        const keys = new Set(result.map(this.getImportItemKey));
        (incoming || []).forEach(item => {
            const key = this.getImportItemKey(item);
            if (!keys.has(key)) {
                keys.add(key);
                result.push(item);
            }
        });
        return result;
    };

    handleSearch = () => {
        this.setState(
            {
                importNextCursor: '',
                importHasMore: false,
                importCandidates: [],
                importSelectedItemKeys: [],
                importValidationResult: null,
                importValidationToken: '',
                importCardsReady: false,
            },
            () => this.searchCandidates(false)
        );
    };

    handleLoadMore = () => {
        if (!this.state.importValidating && this.state.importHasMore) {
            this.searchCandidates(true);
        }
    };

    validateSelected = async () => {
        const { locale = {} } = this.props;
        const selectedItems = this.getSelectedImportItems();
        if (!selectedItems.length) {
            Message.warning(locale.pleaseSelect || '请先选择要导入的服务');
            return;
        }
        this.setState({ importValidating: true });
        try {
            const result = await AiResourceImportService.validate({
                namespaceId: getParams('namespace') || '',
                resourceType: RESOURCE_TYPE_MCP,
                sourceId: this.state.selectedSourceId,
                selectedItems: JSON.stringify(selectedItems),
                overwriteExisting: !!this.state.importOverrideExisting,
            });
            if (result && result.code === 0) {
                const data = result.data || {};
                const validationItems = data.items || [];
                this.setState(prev => {
                    const validationMap = this.buildValidationMap(validationItems);
                    const nextSelected = (prev.importSelectedItemKeys || []).filter(key => {
                        const validation = validationMap[key];
                        return !validation || this.isValidationImportable(validation);
                    });
                    return {
                        importValidationResult: validationItems,
                        importValidationToken: data.validationToken || '',
                        importSelectedItemKeys: nextSelected,
                        importValidating: false,
                    };
                });
                return;
            }
            Message.error(result?.message || locale.validateFailed || '校验失败');
        } catch (e) {
            Message.error(e?.message || locale.validateFailed || '校验失败');
        }
        this.setState({ importValidating: false });
    };

    executeImport = async importAllValid => {
        const { locale = {} } = this.props;
        if (!this.state.importValidationResult) {
            Message.warning(locale.importValidate || '请先校验');
            return;
        }
        const selectedItems = importAllValid
            ? this.getImportableItemsFromValidation()
            : this.getSelectedImportItems(true);
        if (!selectedItems.length) {
            Message.warning(locale.pleaseSelect || '请先选择要导入的服务');
            return;
        }
        this.setState({ importExecuting: true });
        try {
            const result = await AiResourceImportService.execute({
                namespaceId: getParams('namespace') || '',
                resourceType: RESOURCE_TYPE_MCP,
                sourceId: this.state.selectedSourceId,
                selectedItems: JSON.stringify(selectedItems),
                overwriteExisting: !!this.state.importOverrideExisting,
                skipInvalid: !!this.state.importSkipInvalid,
                validationToken: this.state.importValidationToken,
            });
            if (result && result.code === 0) {
                this.showExecuteResult(result.data || {});
                if (!result.data || !result.data.failedCount) {
                    this.handleClose();
                } else {
                    this.searchCandidates(false);
                }
                return;
            }
            Message.error(result?.message || locale.importFailed || '导入执行失败');
        } catch (e) {
            Message.error(e?.message || locale.importFailed || '导入执行异常');
        }
        this.setState({ importExecuting: false });
    };

    showExecuteResult = data => {
        const { locale = {} } = this.props;
        const results = data.results || [];
        this.setState({ importExecuting: false });
        Dialog.alert({
            title: locale.importResult || '导入结果',
            style: { width: 'clamp(420px, 86vw, 640px)' },
            content: (
                <div>
                    <div style={{ marginBottom: 8 }}>
                        {`成功 ${data.successCount || 0} 个，失败 ${data.failedCount || 0} 个，跳过 ${
                            data.skippedCount || 0
                        } 个`}
                    </div>
                    {results.length ? (
                        <div style={{ maxHeight: 320, overflow: 'auto', marginTop: 8 }}>
                            <Table
                                dataSource={results.map((item, idx) => ({
                                    key: idx,
                                    name: item.resourceName || item.externalId || '--',
                                    status: item.status || '--',
                                    reason: item.errorMessage || '',
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
                                    cell={value => this.renderStatusTag(value)}
                                />
                                <Table.Column
                                    title={locale.reason || '原因'}
                                    dataIndex="reason"
                                    cell={value => (
                                        <span style={{ whiteSpace: 'normal', wordBreak: 'break-all' }}>
                                            {value}
                                        </span>
                                    )}
                                />
                            </Table>
                        </div>
                    ) : null}
                </div>
            ),
        });
    };

    toggleCandidateSelection = (item, checked) => {
        const key = this.getImportItemKey(item);
        const set = new Set(this.state.importSelectedItemKeys || []);
        if (checked) {
            set.add(key);
        } else {
            set.delete(key);
        }
        this.setState({ importSelectedItemKeys: Array.from(set) });
    };

    clearAllSelectionForRegistry = () => {
        this.setState({ importSelectedItemKeys: [] });
    };

    openDetailModal = item => {
        const title = item.name || item.externalId || 'Unnamed';
        this.setState({
            detailVisible: true,
            detailTitle: title,
            detailContent: JSON.stringify(item, null, 2),
        });
    };

    closeDetailModal = () =>
        this.setState({ detailVisible: false, detailTitle: '', detailContent: '' });

    getSelectedImportItems = onlyImportable => {
        const selectedKeys = new Set(this.state.importSelectedItemKeys || []);
        const validationMap = this.buildValidationMap(this.state.importValidationResult || []);
        return (this.state.importCandidates || [])
            .filter(item => selectedKeys.has(this.getImportItemKey(item)))
            .filter(item => {
                if (!onlyImportable) {
                    return true;
                }
                const validation = validationMap[this.getImportItemKey(item)];
                return !validation || this.isValidationImportable(validation);
            })
            .map(this.toImportItem);
    };

    getImportableItemsFromValidation = () => {
        const validationMap = this.buildValidationMap(this.state.importValidationResult || []);
        return (this.state.importCandidates || [])
            .filter(item => {
                const validation = validationMap[this.getImportItemKey(item)];
                return validation && this.isValidationImportable(validation);
            })
            .map(this.toImportItem);
    };

    buildValidationMap = validationItems => {
        const result = {};
        (validationItems || []).forEach(item => {
            result[this.getImportItemKey(item)] = item;
        });
        return result;
    };

    getImportItemKey = item => {
        const id = item && (item.externalId || item.name || item.resourceName);
        const version = (item && item.version) || '';
        return `${id || 'unknown'}__${version}`;
    };

    toImportItem = item => ({
        externalId: item.externalId,
        name: item.name,
        version: item.version,
        metadata: item.metadata,
    });

    isValidationImportable = validation => {
        const status = String(validation && validation.status ? validation.status : '').toLowerCase();
        if (status === 'valid' || status === 'warning') {
            return true;
        }
        if (status === 'conflict') {
            return !!this.state.importOverrideExisting;
        }
        return false;
    };

    renderStatusTag = status => {
        const { locale = {} } = this.props;
        const lower = String(status || '').toLowerCase();
        if (lower === 'success') {
            return (
                <span style={this.statusStyle('#dcfce7', '#166534')}>
                    {locale.success || '成功'}
                </span>
            );
        }
        if (lower === 'skipped') {
            return (
                <span style={this.statusStyle('#e5e7eb', '#374151')}>
                    {locale.skipped || '跳过'}
                </span>
            );
        }
        if (lower === 'warning') {
            return (
                <span style={this.statusStyle('#fef3c7', '#92400e')}>
                    {locale.warning || '警告'}
                </span>
            );
        }
        if (lower === 'conflict') {
            return (
                <span style={this.statusStyle('#fee2e2', '#991b1b')}>
                    {locale.conflict || '冲突'}
                </span>
            );
        }
        if (lower === 'valid') {
            return (
                <span style={this.statusStyle('#dcfce7', '#166534')}>
                    {locale.valid || '有效'}
                </span>
            );
        }
        return (
            <span style={this.statusStyle('#fee2e2', '#991b1b')}>
                {locale.failed || locale.invalid || '失败'}
            </span>
        );
    };

    statusStyle = (backgroundColor, color) => ({
        backgroundColor,
        color,
        borderRadius: 12,
        padding: '2px 8px',
        fontSize: 12,
        lineHeight: '18px',
        display: 'inline-block',
    });

    renderSourcePanel() {
        const { locale = {} } = this.props;
        const { sources, selectedSourceId } = this.state;
        const selectedSource = sources.find(source => source.sourceId === selectedSourceId);
        return (
            <div style={{ marginBottom: 12 }}>
                <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
                    <Select
                        value={selectedSourceId}
                        onChange={this.handleSourceChange}
                        disabled={this.state.sourceLoading || this.state.importValidating}
                        style={{ minWidth: 260 }}
                        placeholder={locale.selectSource || '选择来源'}
                    >
                        {sources.map(source => (
                            <Select.Option key={source.sourceId} value={source.sourceId}>
                                {source.displayName || source.sourceId}
                            </Select.Option>
                        ))}
                    </Select>
                    <Input
                        placeholder={locale.searchPlaceholder || '按名称关键词搜索'}
                        value={this.state.importRegistrySearch}
                        onChange={value =>
                            this.setState({
                                importRegistrySearch: value,
                                importValidationResult: null,
                                importValidationToken: '',
                            })
                        }
                        onPressEnter={this.handleSearch}
                        innerAfter={
                            <Icon
                                type="search"
                                size="small"
                                style={{ margin: '0 6px', color: '#9aa1a7', cursor: 'pointer' }}
                                onClick={this.handleSearch}
                            />
                        }
                        style={{ flex: 1 }}
                    />
                    <Button
                        type="primary"
                        loading={this.state.importValidating}
                        disabled={!selectedSourceId || this.state.importExecuting}
                        onClick={this.handleSearch}
                    >
                        {locale.search || '搜索'}
                    </Button>
                </div>
                {selectedSource ? (
                    <div
                        style={{
                            marginTop: 8,
                            color: '#6b7280',
                            display: 'flex',
                            gap: 12,
                            flexWrap: 'wrap',
                            alignItems: 'center',
                        }}
                    >
                        <span>{selectedSource.description || selectedSource.displayName || selectedSource.sourceId}</span>
                        <span style={this.statusStyle('#eef2ff', '#3730a3')}>
                            {selectedSource.pluginName || '--'}
                        </span>
                        {(selectedSource.capabilities || []).map(capability => (
                            <span key={capability} style={this.statusStyle('#ecfeff', '#155e75')}>
                                {capability}
                            </span>
                        ))}
                    </div>
                ) : null}
            </div>
        );
    }

    renderOptions() {
        const { locale = {} } = this.props;
        return (
            <div style={{ display: 'flex', gap: 24, alignItems: 'center', marginBottom: 12 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <Switch
                        checked={this.state.importOverrideExisting}
                        onChange={checked =>
                            this.setState({
                                importOverrideExisting: checked,
                                importValidationResult: null,
                                importValidationToken: '',
                            })
                        }
                    />
                    <span>{locale.importOverride || '覆盖已存在资源'}</span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <Switch
                        checked={this.state.importSkipInvalid}
                        onChange={checked => this.setState({ importSkipInvalid: checked })}
                    />
                    <span>{locale.importSkipInvalid || '跳过无效项'}</span>
                </div>
            </div>
        );
    }

    renderCandidateCards() {
        const { locale = {} } = this.props;
        const candidates = this.state.importCandidates || [];
        const validationMap = this.buildValidationMap(this.state.importValidationResult || []);
        if (!candidates.length) {
            return (
                <div
                    style={{
                        height: 220,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color: '#8a8f8d',
                    }}
                >
                    {this.state.selectedSourceId
                        ? locale.noSearchResult || '未找到匹配的结果'
                        : locale.noData || '暂无数据'}
                </div>
            );
        }
        return (
            <>
                <div
                    style={{
                        display: 'grid',
                        gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
                        gap: 20,
                        width: '100%',
                        alignItems: 'stretch',
                        justifyItems: 'stretch',
                    }}
                >
                    {candidates.map((item, idx) => this.renderCandidateCard(item, idx, validationMap))}
                </div>
                {this.state.importHasMore ? (
                    <div style={{ textAlign: 'center', marginTop: 12 }}>
                        <Button
                            loading={this.state.importValidating}
                            disabled={this.state.importValidating}
                            onClick={this.handleLoadMore}
                        >
                            {locale.loadMore || '加载更多'}
                        </Button>
                    </div>
                ) : null}
            </>
        );
    }

    renderCandidateCard(item, idx, validationMap) {
        const { locale = {} } = this.props;
        const key = this.getImportItemKey(item);
        const validation = validationMap[key];
        const checked = (this.state.importSelectedItemKeys || []).includes(key);
        const status = validation && validation.status;
        const importable = !validation || this.isValidationImportable(validation);
        const disabled = !!validation && !importable;
        const metadata = item.metadata || {};
        const protocol = metadata.protocol || '--';
        const repository = metadata.repository || '';
        const errors = validation && validation.errors && validation.errors.length
            ? validation.errors.join('; ')
            : '';
        const warnings = validation && validation.warnings && validation.warnings.length
            ? validation.warnings.join('; ')
            : '';
        const borderColor = checked ? '#2563eb' : '#e6e7eb';
        const isHovered = this.state.importHoverKey === key;
        const activeHover = !disabled && isHovered;
        const baseTransform = !this.state.importCardsReady
            ? 'translateY(6px)'
            : checked || activeHover
                ? 'translateY(-2px) scale(1.01)'
                : 'translateY(0)';
        const boxShadow = checked
            ? '0 8px 22px rgba(37,99,235,0.22)'
            : activeHover
                ? '0 8px 20px rgba(0,0,0,0.10)'
                : 'none';
        return (
            <div key={key} style={{ minWidth: 0, height: '100%' }}>
                <div
                    onClick={() => !disabled && this.toggleCandidateSelection(item, !checked)}
                    onMouseEnter={() => !disabled && this.setState({ importHoverKey: key })}
                    onMouseLeave={() => this.setState({ importHoverKey: null })}
                    style={{
                        cursor: disabled ? 'not-allowed' : 'pointer',
                        width: '100%',
                        height: '100%',
                        display: 'flex',
                    }}
                >
                    <Card
                        free
                        style={{
                            width: '100%',
                            height: '100%',
                            minHeight: 168,
                            display: 'flex',
                            flexDirection: 'column',
                            boxSizing: 'border-box',
                            border: `1px solid ${disabled ? '#d1d5db' : borderColor}`,
                            backgroundColor: disabled ? '#f3f4f6' : '#ffffff',
                            boxShadow,
                            opacity: disabled ? 0.72 : 1,
                            transform: baseTransform,
                            transition:
                                'transform 220ms ease, box-shadow 220ms ease, border-color 220ms ease, opacity 260ms ease',
                            transitionDelay: `${Math.min(idx * 30, 240)}ms`,
                        }}
                    >
                        <Card.Header
                            title={
                                <span
                                    title={item.name || item.externalId}
                                    style={{
                                        fontWeight: 600,
                                        display: 'inline-block',
                                        maxWidth: 'calc(100% - 100px)',
                                        overflow: 'hidden',
                                        textOverflow: 'ellipsis',
                                        whiteSpace: 'nowrap',
                                    }}
                                >
                                    {item.name || item.externalId || 'Unnamed'}
                                </span>
                            }
                            subTitle={<span style={{ color: '#8a8f8d' }}>v{item.version || '--'}</span>}
                            extra={
                                <div
                                    style={{ display: 'flex', alignItems: 'center', gap: 8 }}
                                    onClick={e => e.stopPropagation()}
                                >
                                    <Checkbox
                                        checked={checked}
                                        disabled={disabled}
                                        onChange={value => this.toggleCandidateSelection(item, value)}
                                    />
                                    {errors || warnings ? (
                                        <Balloon.Tooltip
                                            trigger={
                                                <Icon
                                                    type="prompt"
                                                    size="small"
                                                    style={{ color: errors ? '#dc2626' : '#f59e0b' }}
                                                />
                                            }
                                        >
                                            {errors || warnings}
                                        </Balloon.Tooltip>
                                    ) : null}
                                    <a
                                        onClick={() => this.openDetailModal(item)}
                                        style={{ color: '#2563eb' }}
                                    >
                                        {locale.details || '详情'}
                                    </a>
                                </div>
                            }
                        />
                        <Card.Divider />
                        <Card.Content style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
                            <div
                                title={item.description}
                                style={{
                                    color: '#666',
                                    lineHeight: '20px',
                                    overflow: 'hidden',
                                    display: '-webkit-box',
                                    WebkitLineClamp: 3,
                                    WebkitBoxOrient: 'vertical',
                                }}
                            >
                                {item.description || '--'}
                            </div>
                        </Card.Content>
                        <Card.Divider />
                        <div
                            style={{
                                padding: '8px 12px',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'space-between',
                                gap: 12,
                                flexWrap: 'wrap',
                            }}
                        >
                            <div style={{ color: '#111827' }}>
                                <span style={{ color: '#6b7280', marginRight: 6 }}>
                                    {locale.mcpServerType || '服务类型'}:
                                </span>
                                <span style={{ color: '#2563eb' }}>{protocol}</span>
                            </div>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                                {repository ? (
                                    <a
                                        href={repository}
                                        target="_blank"
                                        rel="noopener noreferrer"
                                        onClick={e => e.stopPropagation()}
                                        style={{ color: '#2563eb' }}
                                    >
                                        {locale.repository || 'Repository'}
                                    </a>
                                ) : null}
                                {status ? this.renderStatusTag(status) : null}
                            </div>
                        </div>
                    </Card>
                </div>
            </div>
        );
    }

    render() {
        const { visible, locale = {} } = this.props;
        const hasSources = (this.state.sources || []).length > 0;
        return (
            <>
                <Dialog
                    title={locale.importMcpServer || '导入 MCP Server'}
                    visible={visible}
                    footer={
                        <div
                            style={{
                                display: 'flex',
                                justifyContent: 'space-between',
                                alignItems: 'center',
                                gap: 8,
                            }}
                        >
                            <Button
                                disabled={
                                    !this.state.importSelectedItemKeys.length ||
                                    this.state.importExecuting
                                }
                                onClick={this.clearAllSelectionForRegistry}
                            >
                                {locale.clearAll || '取消全选'}
                            </Button>
                            <div style={{ display: 'flex', gap: 8 }}>
                                <Button
                                    disabled={
                                        !this.state.importSelectedItemKeys.length ||
                                        this.state.importValidating ||
                                        this.state.importExecuting
                                    }
                                    loading={this.state.importValidating && !this.state.importExecuting}
                                    onClick={this.validateSelected}
                                >
                                    {locale.importValidate || '校验'}
                                </Button>
                                <Button
                                    type="primary"
                                    disabled={
                                        !this.state.importValidationResult ||
                                        !this.state.importSelectedItemKeys.length ||
                                        this.state.importExecuting
                                    }
                                    loading={this.state.importExecuting}
                                    onClick={() => this.executeImport(false)}
                                >
                                    {locale.importSelected || '导入选中'}
                                </Button>
                                <Button
                                    type="secondary"
                                    disabled={
                                        !this.state.importValidationResult ||
                                        this.state.importExecuting
                                    }
                                    onClick={() => this.executeImport(true)}
                                >
                                    {locale.importAll || '导入全部'}
                                </Button>
                            </div>
                        </div>
                    }
                    onClose={this.handleClose}
                    v2
                    style={{ width: 880 }}
                >
                    <Form labelAlign="left">
                        <Loading
                            visible={
                                this.state.sourceLoading ||
                                this.state.importValidating ||
                                this.state.importExecuting
                            }
                            tip={
                                this.state.importExecuting
                                    ? locale.importing || '导入中...'
                                    : locale.loading || '加载中...'
                            }
                            style={{ width: '100%' }}
                        >
                            {hasSources ? (
                                <>
                                    {this.renderSourcePanel()}
                                    {this.renderOptions()}
                                    {this.renderCandidateCards()}
                                </>
                            ) : (
                                <div
                                    style={{
                                        height: 220,
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                        color: '#8a8f8d',
                                    }}
                                >
                                    {locale.noImportSource || '暂无可用导入来源'}
                                </div>
                            )}
                        </Loading>
                    </Form>
                </Dialog>
                {this.state.detailVisible ? (
                    <Dialog
                        key="mcp-detail"
                        title={this.state.detailTitle || locale.details || '详情'}
                        visible
                        onClose={this.closeDetailModal}
                        v2
                        style={{ width: 720 }}
                        footer={false}
                    >
                        <div
                            style={{
                                maxHeight: 420,
                                overflow: 'auto',
                                background: '#f9fafb',
                                border: '1px solid #e5e7eb',
                                padding: 12,
                                borderRadius: 4,
                            }}
                        >
                            <pre
                                style={{
                                    margin: 0,
                                    whiteSpace: 'pre-wrap',
                                    wordBreak: 'break-word',
                                }}
                            >
                                {this.state.detailContent}
                            </pre>
                        </div>
                    </Dialog>
                ) : null}
            </>
        );
    }
}

export default ImportMcpDialog;
