/* eslint-disable react/jsx-indent-props */
import React, { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import { Button, Tag, Tree, Form, Grid, Input, Select, Switch, Dialog } from '@alifd/next';
import { rawDataToFiledValue } from './utils';
import './CreateTools.css';

const { Row, Col } = Grid;

const SchemaEditor = ({
    locale,
    field,
    rootKey,
    rootLabel,
    valueName,
    requiredName,
    bindingPrefix,
    initialRawData,
    initialArgs,
    readOnly,
    refreshKey,
}) => {
    const [rawData, setRawData] = useState([]);
    const [data, setData] = useState([]);
    const [args, setArgs] = useState({});
    const [expandedKeys, setExpandedKeys] = useState([]);
    const [currentNode, setCurrentNode] = useState({
        description: '',
        type: 'object',
        label: rootLabel,
        key: rootKey,
        children: [],
    });

    useEffect(() => {
        if (initialRawData && initialArgs) {
            setRawData(initialRawData);
            setData(JSON.parse(JSON.stringify(initialRawData)));
            setArgs(initialArgs);
            setExpandedKeys([rootKey]);
            setCurrentNode({
                description: '',
                type: 'object',
                label: rootLabel,
                key: rootKey,
                children: initialRawData[0] ? initialRawData[0].children : [],
            });
        }
    }, [refreshKey, initialRawData, initialArgs, rootLabel, rootKey]);

    const saveParamToFiled = (currentRawData = rawData) => {
        if (!currentRawData || currentRawData.length === 0) {
            return;
        }
        const root = currentRawData[0];
        field.setValue(valueName, rawDataToFiledValue(root.children));
        // 同步 required（仅根级参数）
        const req = Array.isArray(root.children)
            ? root.children.filter(n => n.required).map(n => n.label)
            : [];
        field.setValue(requiredName, req);
    };

    const changeNodeInfo = (node) => {
        // setData trigger re-render
        setData(JSON.parse(JSON.stringify(rawData)));
        saveParamToFiled();
    };

    const isArrayItemsNode = node => {
        if (!node || !node.key) return false;
        if (!node.key.endsWith('@@items')) return false;
        const parentKey = node.key
            .split('@@')
            .slice(0, -1)
            .join('@@');
        return args[parentKey]?.type === 'array';
    };

    const AddPropertiesToArgs = () => {
        // 总是在根级 args 下添加新参数
        const parentNode = args[rootKey];
        if (!parentNode.children) {
            parentNode.children = [];
        }
        const childLen = parentNode.children.length + 1;
        // 使用 generic name simple logic
        // InputSchema logic used: `newArg${childLen}`
        // OutputSchema logic used: `newField${childLen}`
        // We can stick to a common naming or make it dynamic. Let's use 'newArg' generic enough or pass as prop?
        // Let's use a generic 'newProperty' or 'newArg' is fine. To match previous behavior perfectly we might want to differentiate,
        // but 'newProperty' is safe for both. InputSchema used `newArg`, OutputSchema used `newField`.
        // Let's use `newProperty` for consistency if acceptable, or `newArg` as a standard.
        // To minimize friction let's just use `newProperty` which is generic.
        const newArgsName = `newProperty${childLen}`;
        const newNodeKey = `${rootKey}@@${newArgsName}`;
        const newNode = {
            label: newArgsName,
            key: newNodeKey,
            type: 'string',
            description: '',
            defaultValue: '',
            required: false,
            children: [],
            arg: {
                type: 'string',
                description: '',
            },
        };

        args[newNodeKey] = newNode;
        if (!parentNode.children) {
            parentNode.children = [];
        }
        parentNode.children.push(newNode);

        // 确保新节点的父节点展开
        const updatedExpandedKeys = [...expandedKeys];
        if (!updatedExpandedKeys.includes(rootKey)) {
            updatedExpandedKeys.push(rootKey);
        }
        setExpandedKeys(updatedExpandedKeys);

        setRawData(rawData);
        setArgs(args);
        setData(JSON.parse(JSON.stringify(rawData)));
        saveParamToFiled();
    };

    // 为指定的节点添加属性
    const AddPropertiesToCurrentNode = () => {
        if (!currentNode || !currentNode.key || currentNode.type !== 'object') {
            return;
        }

        const parentNode = args[currentNode.key];
        if (!parentNode.children) {
            parentNode.children = [];
        }
        const childLen = parentNode.children.length + 1;
        const newPropertyName = `newProperty${childLen}`;
        const newNodeKey = `${currentNode.key}@@${newPropertyName}`;
        const newNode = {
            label: newPropertyName,
            key: newNodeKey,
            type: 'string',
            description: '',
            defaultValue: '',
            required: false,
            children: [],
            arg: {
                type: 'string',
                description: '',
            },
        };

        args[newNodeKey] = newNode;
        if (!parentNode.children) {
            parentNode.children = [];
        }
        parentNode.children.push(newNode);

        // 确保当前节点展开，以显示新添加的属性
        const updatedExpandedKeys = [...expandedKeys];
        if (!updatedExpandedKeys.includes(currentNode.key)) {
            updatedExpandedKeys.push(currentNode.key);
        }
        setExpandedKeys(updatedExpandedKeys);

        setRawData(rawData);
        setArgs(args);
        setData(JSON.parse(JSON.stringify(rawData)));
        saveParamToFiled();
    };

    // 删除参数或属性
    const deleteNode = nodeKey => {
        if (nodeKey === rootKey) {
            // 不允许删除根节点
            return;
        }

        // 显示确认对话框
        Dialog.confirm({
            title: locale.confirmDelete || '确认删除',
            content: locale.confirmDeleteMessage || '确定要删除此项及其所有子项吗？',
            onOk: () => {
                // 执行删除操作
                const performDelete = targetKey => {
                    // 递归删除所有子节点
                    const deleteChildrenRecursively = key => {
                        Object.keys(args).forEach(argKey => {
                            if (argKey.startsWith(key + '@@')) {
                                delete args[argKey];
                            }
                        });
                    };
                    deleteChildrenRecursively(targetKey);

                    // 从args对象中删除该节点
                    delete args[targetKey];

                    // 找到父节点并从其children数组中删除该节点
                    const keyParts = targetKey.split('@@');
                    const parentKey = keyParts.slice(0, -1).join('@@');

                    if (parentKey && args[parentKey]) {
                        const parentNode = args[parentKey];
                        if (parentNode.children) {
                            parentNode.children = parentNode.children.filter(child => child.key !== targetKey);

                            // 检查父节点是否为object类型且删除后没有子节点了
                            if (
                                parentNode.type === 'object' &&
                                parentNode.children.length === 0 &&
                                parentKey !== rootKey
                            ) {
                                // 递归删除空的object父节点
                                performDelete(parentKey);
                                return;
                            }
                        }
                    }
                };

                // 开始删除操作
                performDelete(nodeKey);

                // 如果删除的节点或其父节点是当前选中的节点，重置当前节点
                if (
                    currentNode.key === nodeKey ||
                    currentNode.key.startsWith(nodeKey + '@@') ||
                    nodeKey.startsWith(currentNode.key + '@@') ||
                    !args[currentNode.key]
                ) {
                    setCurrentNode({
                        key: '',
                        label: '',
                        type: 'string',
                        description: '',
                    });
                }

                // 重新构建rawData
                const rebuildRawData = () => {
                    const rootNode = args[rootKey];
                    if (rootNode) {
                        setRawData([rootNode]);
                        setData(JSON.parse(JSON.stringify([rootNode])));
                        setArgs(args);
                        saveParamToFiled([rootNode]);
                    }
                };

                rebuildRawData();
            },
        });
    };

    return (
        <div className="create-tools-card">
            <Form.Item label={locale.ArgumentTree || '参数树'} style={{ margin: '16px 0 0' }}>
                <div className="tree-wrapper">
                    <Row style={{ marginBottom: '16px' }}>
                        <Col style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                            <Button
                                type="primary"
                                size="medium"
                                onClick={AddPropertiesToArgs}
                                disabled={readOnly}
                                style={{
                                    borderRadius: '6px',
                                    height: '36px',
                                    fontWeight: '500',
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '6px',
                                }}
                            >
                                {locale.AddNewArg || '增加参数'}
                            </Button>

                            {/* 为 object 类型节点添加属性按钮 */}
                            {currentNode.type === 'object' && currentNode.key !== rootKey && (
                                <Button
                                    type="primary"
                                    size="medium"
                                    onClick={AddPropertiesToCurrentNode}
                                    disabled={readOnly}
                                    style={{
                                        borderRadius: '6px',
                                        height: '36px',
                                        fontWeight: '500',
                                        backgroundColor: '#722ed1',
                                        borderColor: '#722ed1',
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: '6px',
                                    }}
                                >
                                    {locale.AddNewProperties || '增加属性'}
                                </Button>
                            )}

                            {readOnly && (
                                <Tag color="orange" style={{ margin: 0 }}>
                                    {locale.editExistVersionMessage || '只读模式不可编辑'}
                                </Tag>
                            )}
                        </Col>
                    </Row>

                    <Row>
                        <Col style={{ width: '100%' }}>
                            <div className="tree-scroll-container">
                                <Tree
                                    showLine
                                    isLabelBlock
                                    dataSource={data}
                                    defaultSelectedKeys={[rootKey]}
                                    expandedKeys={expandedKeys}
                                    onExpand={keys => setExpandedKeys(keys)}
                                    aria-label={'schema-tree'}
                                    labelRender={node => {
                                        return (
                                            <Row
                                                style={{ fontSize: 'medium', width: '100%' }}
                                                justify="space-between"
                                                align="middle"
                                            >
                                                <Col>
                                                    <Row>
                                                        <Col>
                                                            <a style={{ color: '#1890ff', cursor: 'pointer' }}>
                                                                {node.label}
                                                                {node.key.split('@@').length === 2 && (
                                                                    <span style={{ color: '#fa541c', marginLeft: 6 }}>
                                                                        {args[node.key]?.required ? '*' : ''}
                                                                    </span>
                                                                )}
                                                            </a>
                                                            <span style={{ color: '#666' }}>&nbsp;&nbsp;({args[node.key]?.type})</span>
                                                        </Col>
                                                        {/* items 节点不显示描述 */}
                                                        {!isArrayItemsNode(args[node.key]) && (
                                                            <Col style={{ textOverflow: 'ellipsis', marginLeft: 10, color: '#666' }}>
                                                                {args[node.key]?.description?.length <= 25
                                                                    ? args[node.key]?.description
                                                                    : `${args[node.key]?.description?.substring(0, 20)}...`}
                                                            </Col>
                                                        )}
                                                    </Row>
                                                </Col>
                                                {/* 删除按钮 - 不能删除根节点 */}
                                                {node.key !== rootKey && !readOnly && (
                                                    <Col>
                                                        <Button
                                                            type="primary"
                                                            warning
                                                            size="small"
                                                            onClick={e => {
                                                                e.stopPropagation(); // 阻止事件冒泡，避免触发节点选择
                                                                deleteNode(node.key);
                                                            }}
                                                            style={{
                                                                marginLeft: 10,
                                                                padding: '2px 8px',
                                                                fontSize: '12px',
                                                                height: '20px',
                                                                lineHeight: '16px',
                                                            }}
                                                        >
                                                            ×
                                                        </Button>
                                                    </Col>
                                                )}
                                            </Row>
                                        );
                                    }}
                                    onSelect={data => {
                                        if (data.length === 1) {
                                            const currentNode = args[data];
                                            setCurrentNode(currentNode);
                                        } else if (data.length === 0) {
                                            setCurrentNode({
                                                key: '',
                                                label: '',
                                                type: 'string',
                                                description: '',
                                            });
                                        }
                                    }}
                                />
                            </div>
                        </Col>
                    </Row>
                </div>
            </Form.Item>

            {currentNode.key !== '' && currentNode.key !== rootKey && (
                <div className="detail-info-container">
                    <h3 className="detail-info-header">
                        {locale.ArgumentInfo || '参数详情'}
                    </h3>

                    <Row gutter={24}>
                        <Col span={12}>
                            <Form.Item
                                name={`${bindingPrefix}.name`}
                                label={locale.toolParamName}
                                required
                                requiredTrigger="onBlur"
                                asterisk={false}
                                style={{ marginBottom: '20px' }}
                            >
                                <Input
                                    size="large"
                                    style={{ borderRadius: '6px' }}
                                    isPreview={readOnly}
                                    disabled={currentNode.key === rootKey || isArrayItemsNode(currentNode)}
                                    value={currentNode.label}
                                    placeholder="请输入参数名称"
                                    onChange={data => {
                                        if (currentNode.key !== '' && !isArrayItemsNode(currentNode)) {
                                            currentNode.label = data;
                                            changeNodeInfo(currentNode);
                                        }
                                    }}
                                />
                            </Form.Item>
                        </Col>
                        <Col span={12}>
                            <Form.Item
                                name={`${bindingPrefix}.type`}
                                label={locale.toolParamType}
                                style={{ marginBottom: '20px' }}
                            >
                                <Select
                                    size="large"
                                    style={{ borderRadius: '6px' }}
                                    isPreview={readOnly}
                                    disabled={currentNode.key === rootKey}
                                    value={currentNode.type}
                                    placeholder="请选择参数类型"
                                    dataSource={[
                                        { label: '字符串类型 (string)', value: 'string' },
                                        { label: '数字类型 (number)', value: 'number' },
                                        { label: '整数类型 (integer)', value: 'integer' },
                                        { label: '布尔类型 (boolean)', value: 'boolean' },
                                        { label: '数组类型 (array)', value: 'array' },
                                        { label: '对象类型 (object)', value: 'object' },
                                    ]}
                                    onChange={data => {
                                        if (currentNode.key !== '') {
                                            if (!(data === 'array' || data === 'object')) {
                                                currentNode.children = [];
                                            }
                                            currentNode.type = data;
                                            if (currentNode.arg) {
                                                currentNode.arg.type = data;
                                            }
                                            if (data === 'array') {
                                                const itemNode = {
                                                    label: 'items',
                                                    type: 'string',
                                                    description: '',
                                                    defaultValue: '',
                                                    key: `${currentNode.key}@@items`,
                                                    arg: {
                                                        type: 'string',
                                                        description: '',
                                                    },
                                                };
                                                currentNode.children = [itemNode];
                                                args[`${currentNode.key}@@items`] = itemNode;

                                                const updatedExpandedKeys = [...expandedKeys];
                                                if (!updatedExpandedKeys.includes(currentNode.key)) {
                                                    updatedExpandedKeys.push(currentNode.key);
                                                }
                                                setExpandedKeys(updatedExpandedKeys);

                                                changeNodeInfo(currentNode);
                                            } else if (data === 'object') {
                                                const defaultPropertyNode = {
                                                    label: 'property1',
                                                    type: 'string',
                                                    description: '',
                                                    defaultValue: '',
                                                    key: `${currentNode.key}@@property1`,
                                                    children: [],
                                                    arg: {
                                                        type: 'string',
                                                        description: '',
                                                    },
                                                };
                                                currentNode.children = [defaultPropertyNode];
                                                args[`${currentNode.key}@@property1`] = defaultPropertyNode;

                                                const updatedExpandedKeys = [...expandedKeys];
                                                if (!updatedExpandedKeys.includes(currentNode.key)) {
                                                    updatedExpandedKeys.push(currentNode.key);
                                                }
                                                setExpandedKeys(updatedExpandedKeys);

                                                changeNodeInfo(currentNode);
                                            } else {
                                                changeNodeInfo(currentNode);
                                            }
                                        }
                                    }}
                                />
                            </Form.Item>
                        </Col>
                    </Row>

                    {!isArrayItemsNode(currentNode) && (
                        <Row>
                            <Col span={24}>
                                <Form.Item
                                    label={locale.toolParamDescription}
                                    name={`${bindingPrefix}.description`}
                                    asterisk={false}
                                    style={{ marginBottom: '20px' }}
                                >
                                    <Input.TextArea
                                        size="large"
                                        style={{ borderRadius: '6px', minHeight: '80px' }}
                                        disabled={currentNode.key === rootKey}
                                        value={currentNode.description}
                                        placeholder="请输入参数描述信息"
                                        onChange={data => {
                                            if (currentNode.key !== '') {
                                                currentNode.description = data;
                                                if (currentNode.arg) {
                                                    currentNode.arg.description = data;
                                                }
                                                changeNodeInfo(currentNode);
                                            }
                                        }}
                                    />
                                </Form.Item>
                            </Col>
                        </Row>
                    )}

                    {currentNode.key && currentNode.key.split('@@').length === 2 && (
                        <Row>
                            <Col span={24}>
                                <Form.Item
                                    label={locale.toolParamRequired || '是否必填'}
                                    name={`${bindingPrefix}.required`}
                                    asterisk={false}
                                    style={{ marginBottom: '12px' }}
                                >
                                    <div style={{ display: 'flex', alignItems: 'center' }}>
                                        <Switch
                                            size="large"
                                            checked={!!currentNode.required}
                                            onChange={checked => {
                                                if (currentNode.key) {
                                                    currentNode.required = !!checked;
                                                    changeNodeInfo(currentNode);
                                                }
                                            }}
                                            disabled={readOnly}
                                            checkedChildren={locale.required || '必填'}
                                            unCheckedChildren={locale.optional || '可选'}
                                        />
                                        <span style={{ marginLeft: 12, color: '#666', fontSize: 12 }}>
                                            {currentNode.required
                                                ? locale.required || '必填'
                                                : locale.optional || '可选'}
                                        </span>
                                    </div>
                                </Form.Item>
                            </Col>
                        </Row>
                    )}

                    {currentNode.type &&
                        currentNode.type !== 'object' &&
                        currentNode.type !== 'array' &&
                        currentNode.key !== rootKey &&
                        !isArrayItemsNode(currentNode) && (
                            <Row>
                                <Col span={24}>
                                    <Form.Item
                                        label={locale.toolParamDefaultValue || '默认值'}
                                        name={`${bindingPrefix}.defaultValue`}
                                        asterisk={false}
                                        style={{ marginBottom: '0' }}
                                        extra={
                                            <div
                                                style={{
                                                    color: '#666',
                                                    fontSize: '12px',
                                                    marginTop: '4px',
                                                }}
                                            >
                                                {currentNode.type === 'boolean'
                                                    ? '布尔类型请输入 true 或 false'
                                                    : currentNode.type === 'number' || currentNode.type === 'integer'
                                                        ? '请输入数字'
                                                        : '可选：为此参数设置默认值'}
                                            </div>
                                        }
                                    >
                                        {currentNode.type === 'boolean' ? (
                                            <Select
                                                size="large"
                                                style={{ borderRadius: '6px' }}
                                                value={currentNode.defaultValue}
                                                placeholder="请选择默认值"
                                                allowClear
                                                dataSource={[
                                                    { label: 'true', value: 'true' },
                                                    { label: 'false', value: 'false' },
                                                ]}
                                                onChange={data => {
                                                    if (currentNode.key !== '' && !isArrayItemsNode(currentNode)) {
                                                        currentNode.defaultValue = data || '';
                                                        if (currentNode.arg) {
                                                            if (data) {
                                                                currentNode.arg.default = data === 'true';
                                                            } else {
                                                                delete currentNode.arg.default;
                                                            }
                                                        }
                                                        changeNodeInfo(currentNode);
                                                    }
                                                }}
                                            />
                                        ) : (
                                            <Input
                                                size="large"
                                                style={{ borderRadius: '6px' }}
                                                value={currentNode.defaultValue}
                                                placeholder={
                                                    currentNode.type === 'number' || currentNode.type === 'integer'
                                                        ? '请输入数字默认值'
                                                        : '请输入默认值'
                                                }
                                                onChange={data => {
                                                    if (currentNode.key !== '' && !isArrayItemsNode(currentNode)) {
                                                        currentNode.defaultValue = data;
                                                        if (currentNode.arg) {
                                                            if (data && data.trim()) {
                                                                if (currentNode.type === 'number') {
                                                                    const num = parseFloat(data);
                                                                    if (!isNaN(num)) {
                                                                        currentNode.arg.default = num;
                                                                    }
                                                                } else if (currentNode.type === 'integer') {
                                                                    const int = parseInt(data, 10);
                                                                    if (!isNaN(int)) {
                                                                        currentNode.arg.default = int;
                                                                    }
                                                                } else {
                                                                    currentNode.arg.default = data;
                                                                }
                                                            } else {
                                                                delete currentNode.arg.default;
                                                            }
                                                        }
                                                        changeNodeInfo(currentNode);
                                                    }
                                                }}
                                            />
                                        )}
                                    </Form.Item>
                                </Col>
                            </Row>
                        )}
                </div>
            )}
        </div>
    );
};

SchemaEditor.propTypes = {
    locale: PropTypes.object.isRequired,
    field: PropTypes.object.isRequired,
    rootKey: PropTypes.string.isRequired,
    rootLabel: PropTypes.string.isRequired,
    valueName: PropTypes.string.isRequired,
    requiredName: PropTypes.string.isRequired,
    bindingPrefix: PropTypes.string.isRequired,
    initialRawData: PropTypes.array,
    initialArgs: PropTypes.object,
    readOnly: PropTypes.bool,
    refreshKey: PropTypes.number,
};

export default SchemaEditor;
