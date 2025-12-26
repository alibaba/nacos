
import React, { useImperativeHandle, useState } from 'react';
import PropTypes from 'prop-types';
import {
  Dialog,
  Field,
  Form,
  Message,
  Tab,
  Button,
} from '@alifd/next';
import { formitemLayout } from './components';
import { request } from '../../../../globalLib';
import BasicInfo from './BasicInfo';
import InputSchema from './InputSchema';
import OutputSchema from './OutputSchema';
import AdvancedConfig from './AdvancedConfig';
import { convertPropertiesToTreeData } from './utils';
import './CreateTools.css';

const CreateTools = React.forwardRef((props, ref) => {
  const { locale, showTemplates = false, onlyEditRuntimeInfo = false } = props;
  const field = Field.useField({
    parseName: true,
    values: {
      toolParams: [],
      outputToolParams: [],
      outputRequired: [],
      invokeContext: [],
      templates: '',
      transparentAuth: false,
      securitySchemeId: '',
      clientSecuritySchemeId: '',
    },
  });

  const [visible, setVisible] = useState(false);
  const [type, setType] = useState('');
  const [okLoading, setOkLoading] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);

  // States to pass to children for initialization
  const [initialInputData, setInitialInputData] = useState({ rawData: [], args: {} });
  const [initialOutputData, setInitialOutputData] = useState({ rawData: [], args: {} });
  const [initialTemplate, setInitialTemplate] = useState('');

  const openVisible = ({ record, type, toolsMeta }) => {
    const { name, description, inputSchema, outputSchema } = record;
    setType(type);

    // --- Input Schema Initialization ---
    const nextArgs = {};
    const _toolParams = inputSchema?.properties
      ? convertPropertiesToTreeData(
        inputSchema?.properties,
        'args',
        nextArgs,
        Array.isArray(inputSchema?.required) ? inputSchema?.required : []
      )
      : [];

    let rootNode = {
      type: 'object',
      label: locale.ArgumentsList,
      key: 'args',
      description: '',
      children: _toolParams,
    };
    nextArgs.args = rootNode;

    if (rootNode.children.length === 0) {
      const defaultNewArg = {
        type: 'string',
        label: 'NewArg1',
        key: 'args@@NewArg1',
        description: '',
        defaultValue: '',
        required: false,
        children: [],
        arg: {
          type: 'string',
          description: '',
        },
      };
      rootNode.children = [defaultNewArg];
      nextArgs['args@@NewArg1'] = defaultNewArg;
    }

    setInitialInputData({
      rawData: [rootNode],
      args: nextArgs
    });


    // --- Output Schema Initialization ---
    const nextOutputArgs = {};
    const _outputParams = outputSchema?.properties
      ? convertPropertiesToTreeData(
        outputSchema?.properties,
        'out',
        nextOutputArgs,
        Array.isArray(outputSchema?.required) ? outputSchema?.required : []
      )
      : [];

    let outputRootNode = {
      type: 'object',
      label: locale.OutputArgumentsList || locale.ArgumentsList,
      key: 'out',
      description: '',
      children: _outputParams,
    };
    nextOutputArgs.out = outputRootNode;

    if (outputRootNode.children.length === 0) {
      const defaultNewOut = {
        type: 'string',
        label: 'result',
        key: 'out@@result',
        description: '',
        defaultValue: '',
        required: false,
        children: [],
        arg: {
          type: 'string',
          description: '',
        },
      };
      outputRootNode.children = [defaultNewOut];
      nextOutputArgs['out@@result'] = defaultNewOut;
    }

    setInitialOutputData({
      rawData: [outputRootNode],
      args: nextOutputArgs
    });

    // --- Templates & Security Initialization ---
    const _invokeContext = toolsMeta?.invokeContext
      ? Object.keys(toolsMeta?.invokeContext).map(key => ({
        key,
        value: toolsMeta?.invokeContext[key],
      }))
      : [];

    let templatesStr = '';
    let extractedSecuritySchemeId = '';
    let extractedClientSecuritySchemeId = '';
    let extractedTransparentAuth = false;

    if (toolsMeta?.templates !== undefined && 'json-go-template' in toolsMeta?.templates) {
      templatesStr = JSON.stringify(toolsMeta?.templates['json-go-template'], null, 2);

      try {
        const templateObj = toolsMeta?.templates['json-go-template'];

        if (templateObj?.requestTemplate?.security) {
          extractedSecuritySchemeId = templateObj?.requestTemplate?.security.id;
        }

        if (templateObj?.security) {
          if (templateObj.security.id) {
            extractedClientSecuritySchemeId = templateObj.security.id;
          }
          if (templateObj.security.passthrough === true) {
            extractedTransparentAuth = true;
          }
        }
      } catch (error) {
        // ignore error
      }
    }

    setInitialTemplate(templatesStr);

    field.setValues({
      name,
      description,
      toolParams: inputSchema?.properties || {},
      required: inputSchema?.required,
      outputToolParams: outputSchema?.properties || {},
      outputRequired: outputSchema?.required,
      invokeContext: _invokeContext,
      templates: templatesStr,
      enabled: toolsMeta?.enabled,
      transparentAuth: extractedTransparentAuth || toolsMeta?.transparentAuth || false,
      securitySchemeId: extractedSecuritySchemeId || toolsMeta?.securitySchemeId || '',
      clientSecuritySchemeId:
        extractedClientSecuritySchemeId || toolsMeta?.clientSecuritySchemeId || '',
    });

    setRefreshKey(prev => prev + 1);
    setOkLoading(false);
    setVisible(true);
  };

  useImperativeHandle(ref, () => ({
    openVisible,
  }));

  const closeDialog = () => {
    setVisible(false);
    setType('');
    setInitialTemplate('');
    setInitialInputData({ rawData: [], args: {} });
    setInitialOutputData({ rawData: [], args: {} });
  };

  const createItems = () => {
    field.validate((error, values) => {
      const records = props?.serverConfig;
      if (error) {
        return;
      }

      const invokeContext = {};
      if (values?.invokeContext?.length) {
        values.invokeContext.forEach(item => {
          invokeContext[item.key] = item.value;
        });
      }

      const templates = {};

      if (
        (records.protocol === 'http' || records.protocol === 'https') &&
        values?.templates?.length > 0
      ) {
        try {
          // values.templates should already contain the security injection
          // if AdvancedConfig works correctly
          let parsedTemplate = JSON.parse(values.templates);

          if (parsedTemplate && Object.keys(parsedTemplate).length > 0) {
            templates['json-go-template'] = parsedTemplate;
          }
        } catch (error) {
          Message.error(locale.templateParseError || '模板格式错误，请检查 JSON 格式');
          return;
        }
      }

      // 根据 item.name  去除 重复的 name 值
      let _tool = JSON.parse(JSON.stringify(records?.toolSpec?.tools || []));
      let _toolsMeta = JSON.parse(JSON.stringify(records?.toolSpec?.toolsMeta || {}));

      const properties = values?.toolParams;
      const _toolitem = {
        name: values?.name,
        description: values?.description,
        inputSchema: {
          type: 'object',
          properties,
          required: values?.required,
        },
      };

      const outputProperties = values?.outputToolParams;
      const hasOutputProperties =
        outputProperties && typeof outputProperties === 'object' && Object.keys(outputProperties).length > 0;
      if (hasOutputProperties) {
        _toolitem.outputSchema = {
          type: 'object',
          properties: outputProperties,
          required: values?.outputRequired,
        };
      }
      const _toolsMetaitem = {
        [values?.name]: {
          enabled: values?.enabled,
          invokeContext,
          templates,
          transparentAuth: values?.transparentAuth || false,
          securitySchemeId: values?.securitySchemeId || '',
          clientSecuritySchemeId: values?.clientSecuritySchemeId || '',
        },
      };

      if (type === 'edit') {
        _tool
          .map(i => i.name)
          .forEach((name, index) => {
            if (values?.name === name) {
              _tool[index] = _toolitem;
              _toolsMeta[values?.name] = _toolsMetaitem[values?.name];
            }
          });
      } else {
        _tool.push(_toolitem);
        _toolsMeta = {
          ..._toolsMeta,
          ..._toolsMetaitem,
        };
      }
      const toolSpecification = JSON.stringify({
        tools: _tool,
        toolsMeta: _toolsMeta,
      });

      const endpointSpecification = JSON.stringify({
        type: 'REF',
        data: {
          namespaceId: records?.remoteServerConfig?.serviceRef?.namespaceId,
          serviceName: records?.remoteServerConfig?.serviceRef?.serviceName,
          groupName: records?.remoteServerConfig?.serviceRef?.groupName,
        },
      });

      const params = {
        mcpName: records?.name,
        serverSpecification: JSON.stringify({
          protocol: records?.protocol,
          name: records?.name,
          description: records?.description,
          version: records?.version,
          enbled: true,
          remoteServerConfig: {
            exportPath: records?.remoteServerConfig?.exportPath,
          },
        }),
        toolSpecification,
      };

      if (records?.protocol !== 'stdio') {
        params.endpointSpecification = endpointSpecification;
      }

      if (props?.onChange) {
        props.onChange(JSON.parse(toolSpecification));
        closeDialog();
      }
    });
  };

  const isPreview = type === 'preview';

  return (
    <div>
      {visible ? (
        <Dialog
          v2
          title={
            <div style={{ fontSize: '18px', fontWeight: '600', color: '#262626', display: 'flex', alignItems: 'center' }}>
              <span
                style={{
                  display: 'inline-block',
                  width: '4px',
                  height: '18px',
                  backgroundColor: '#1890ff',
                  marginRight: '12px',
                  borderRadius: '2px',
                }}
              />
              {(() => {
                if (type === 'edit') return locale.editTool || '编辑工具';
                if (type === 'preview') return locale.previewTool || '预览工具';
                return locale.createTool || '创建工具';
              })()}
            </div>
          }
          footer={
            isPreview ? (
              <Button
                type="primary"
                size="large"
                onClick={closeDialog}
                style={{ borderRadius: '6px', height: '40px', fontSize: '16px', fontWeight: '500' }}
              >
                {locale.close}
              </Button>
            ) : (
              true
            )
          }
          visible
          footerActions={isPreview ? [] : ['ok', 'cancel']}
          onOk={createItems}
          okProps={{
            loading: okLoading,
            size: 'large',
            style: { borderRadius: '6px', height: '40px', fontSize: '16px', fontWeight: '500' },
          }}
          cancelProps={{
            size: 'large',
            style: { borderRadius: '6px', height: '40px', fontSize: '16px' },
          }}
          onClose={closeDialog}
          className="create-tools-modal"
          style={{ width: '80%', maxWidth: '1200px', minWidth: '800px' }}
          bodyStyle={{ padding: 0 }}
        >
          <div className="create-tools-dialog-body">
            <Form
              field={field}
              {...formitemLayout}
              className="create-tools-form-container"
            >
              <Tab shape="wrapped" size="medium" style={{ marginTop: 0 }} contentStyle={{ padding: '20px 0' }}>
                <Tab.Item title={locale.basicInfo || '基础信息'} key="basic">
                  <BasicInfo
                    locale={locale}
                    field={field}
                    type={type}
                    serverConfig={props?.serverConfig}
                    isPreview={isPreview}
                  />
                </Tab.Item>
                <Tab.Item title={locale.toolInputSchema || '入参配置'} key="input">
                  <InputSchema
                    locale={locale}
                    field={field}
                    isPreview={isPreview}
                    onlyEditRuntimeInfo={onlyEditRuntimeInfo}
                    initialRawData={initialInputData.rawData}
                    initialArgs={initialInputData.args}
                    refreshKey={refreshKey}
                  />
                </Tab.Item>
                <Tab.Item title={locale.toolOutputSchema || '出参配置'} key="output">
                  <OutputSchema
                    locale={locale}
                    field={field}
                    isPreview={isPreview}
                    onlyEditRuntimeInfo={onlyEditRuntimeInfo}
                    initialRawData={initialOutputData.rawData}
                    initialArgs={initialOutputData.args}
                    refreshKey={refreshKey}
                  />
                </Tab.Item>
                {showTemplates && (
                  <Tab.Item title={locale.advancedConfig || '高级配置'} key="template">
                    <AdvancedConfig
                      locale={locale}
                      field={field}
                      serverConfig={props?.serverConfig}
                      onlyEditRuntimeInfo={onlyEditRuntimeInfo}
                      initialOriginalTemplate={initialTemplate}
                      refreshKey={refreshKey}
                    />
                  </Tab.Item>
                )}
              </Tab>
            </Form>
          </div>
        </Dialog>
      ) : null}
    </div>
  );
});

CreateTools.propTypes = {
  locale: PropTypes.object,
  showTemplates: PropTypes.bool,
  onlyEditRuntimeInfo: PropTypes.bool,
  serverConfig: PropTypes.object,
  onChange: PropTypes.func,
};

export default CreateTools;
