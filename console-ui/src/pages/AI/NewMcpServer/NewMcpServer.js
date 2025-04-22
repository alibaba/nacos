import React from 'react';
import PropTypes from 'prop-types';
import SuccessDialog from '../../../components/SuccessDialog';
import { getParams, setParams, request } from '../../../globalLib';
import {
  Balloon,
  Button,
  Dialog,
  Field,
  Form,
  Icon,
  Grid,
  Input,
  Loading,
  Message,
  Select,
  Radio,
  ConfigProvider,
} from '@alifd/next';
import { McpServerManagementRouteName, McpServerManagementRoute } from '../../../layouts/menu';
const { Row, Col } = Grid;

const FormItem = Form.Item;
const { Group: RadioGroup } = Radio;

@ConfigProvider.config
class NewMcpServer extends React.Component {
  static displayName = 'NewMcpServer';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.successDialog = React.createRef();
    this.field = new Field(this);
    this.searchDataId = getParams('searchDataId') || '';
    this.searchGroup = getParams('searchGroup') || '';
    this.tenant = getParams('namespace') || '';
    this.state = {
      loading: false,
      addonBefore: '',
      namespaceSelects: [],
      useExistService: true,
      serviceList: [],
    };
  }

  componentDidMount() {
    if (!getParams('namespace')) {
      this.props?.history?.push({
        pathname: McpServerManagementRoute,
      });
    }
    this.initEditedData();
    this.getNamespaces();
  }

  // 编辑数据 初始化
  initEditedData = async () => {
    const mcpname = getParams('mcpname') || '';
    const mcptype = getParams('mcptype') || '';
    if (mcpname && mcptype === 'edit') {
      const result = await request({ url: `v3/console/ai/mcp?mcpName=${mcpname}` });
      if (result.code === 0 && result.data) {
        const {
          description = '',
          name = '',
          type = '',
          version = '',
          localServerConfig = {},
          remoteServerConfig = {},
        } = result.data;

        const initFileData = {
          serverName: name,
          serverType: type,
          description: description,
          version: version,
        };

        if (localServerConfig && JSON.stringify(localServerConfig, null, 2) !== '{}') {
          initFileData['localServerConfig'] = JSON.stringify(localServerConfig, null, 2);
        }

        if (remoteServerConfig) {
          initFileData['exportPath'] = remoteServerConfig?.exportPath;
          initFileData['useExistService'] = remoteServerConfig?.serviceRef?.serviceName
            ? true
            : false;
          initFileData['namespace'] = remoteServerConfig?.serviceRef?.namespaceId;
          initFileData['service'] = remoteServerConfig?.serviceRef?.serviceName;

          // 通过 namespaceId 获取服务列表
          if (remoteServerConfig?.serviceRef?.namespaceId) {
            this.getServiceList(remoteServerConfig?.serviceRef?.namespaceId);
          }
        }

        this.field.setValues(initFileData);
      }
    }
  };

  // 获取服务组列表
  getNamespaces = () => {
    request({
      type: 'get',
      url: 'v3/console/core/namespace/list',
      data: {
        pageNo: 1,
        pageSize: 1000,
      },
      success: res => {
        if (res.code == 0 && res.data) {
          this.setState({
            namespaceSelects: res.data?.map(item => ({
              ...item,
              label: item.namespaceShowName,
              value: item.namespace,
            })),
          });
        }
      },
    });
  };

  goList() {
    const { history = {} } = this.props;
    history && history.goBack();
  }

  openLoading() {
    this.setState({
      loading: true,
    });
  }

  closeLoading() {
    this.setState({
      loading: false,
    });
  }

  publishConfig() {
    const { serviceList = [], useExistService = true } = this.state;
    this.field.validate((errors, values) => {
      if (errors) {
        return;
      }
      const params = {
        mcpName: values?.serverName,
        serverSpecification: `{
          "protocol":"${values?.serverType}",
          "name":"${values?.serverName}",
          "description":"${values?.description || ''}",
          "version":"${values?.version || '1.0.0'}",
          "enabled":true,
          "localServerConfig": ${values?.localServerConfig || '{}'}
        }`,
      };

      if (values?.serverType !== 'stdio') {
        // 获取服务组
        params.serverSpecification = `{
          "protocol":"${values?.serverType}",
          "name":"${values?.serverName}",
          "description":"${values?.description || ''}",
          "version":"${values?.version || '1.0.0'}",
          "enabled":true,
          "remoteServerConfig":{
            "exportPath": "${values?.exportPath || ''}"
          }
        }`;
        // 添加服务
        const serverGroup = serviceList.find(item => item.value === values?.service);

        params.endpointSpecification = useExistService
          ? `{
          "type": "REF",
          "data":{
            "namespaceId":"${values?.namespace || ''}",
            "serviceName": "${values?.service || ''}",
            "groupName":"${serverGroup?.groupName || ''}"
          }
        }`
          : `{"type": "DIRECT","data":{"address":"${values?.address}","port": "${values?.port}"}}`;
      }

      if (getParams('mcptype') === 'edit') {
        return this.editMcpServer(params);
      } else {
        return this.createMcpServer(params);
      }
    });
  }

  // 新建服务
  createMcpServer = async params => {
    const { locale = {} } = this.props;
    this.openLoading();
    const result = await request({
      url: 'v3/console/ai/mcp',
      method: 'post',
      data: params,
      error: err => {
        this.closeLoading();
      },
    });

    if (result.data === 'ok' || result.message === 'success') {
      Message.success({
        content: locale.publishSuccessfully,
        align: 'tc tc',
        animation: true,
        duration: 800,
        afterClose: () => {
          this.goList();
          this.closeLoading();
        },
      });
    }
  };

  // 编辑服务
  editMcpServer = async params => {
    const { locale = {} } = this.props;
    this.openLoading();
    const result = await request({
      url: 'v3/console/ai/mcp',
      method: 'put',
      data: params,
      error: err => {
        this.closeLoading();
      },
    });

    if (result.data === 'ok' || result.message === 'success') {
      Message.success({
        content: locale.editSuccessfully,
        align: 'tc tc',
        animation: true,
        duration: 800,
        afterClose: () => {
          this.goList();
          this.closeLoading();
        },
      });
    }
  };

  validateChart(rule, value, callback) {
    const { locale = {} } = this.props;
    const chartReg = /[@#\$%\^&\*\s]+/g;

    if (chartReg.test(value)) {
      callback(locale.doNotEnter);
    } else {
      callback();
    }
  }

  getServiceList = async namespaceId => {
    const data = {
      ignoreEmptyService: false,
      withInstances: false,
      pageNo: 1,
      pageSize: 100,
      namespaceId: namespaceId,
    };
    const result = await request({
      url: 'v3/console/ns/service/list',
      method: 'get',
      data,
    });

    if (result.code === 0) {
      this.setState({
        serviceList: result.data.pageItems.map(item => ({
          label: `${item.groupName} / ${item.name}`,
          value: item.name,
          ...item,
        })),
      });
    } else {
      Message.error(result.message);
    }
  };
  handleNamespaceChange = value => {
    this.field.reset('service');
    this.getServiceList(value);
  };

  render() {
    const { locale = {} } = this.props;
    const { init } = this.field;
    const isEdit = getParams('mcptype') && getParams('mcptype') === 'edit';
    const formItemLayout = {
      labelCol: {
        span: 4,
      },
      wrapperCol: {
        span: 20,
      },
    };
    const textAreaProps = {
      'aria-label': 'auto height',
      autoHeight: {
        minRows: 10,
        maxRows: 20,
      },
    };

    return (
      <Loading
        shape={'flower'}
        tip={'Loading...'}
        style={{ width: '100%', position: 'relative' }}
        visible={this.state.loading}
        color={'#333'}
      >
        <h1>{locale.newListing}</h1>
        <Form className="new-config-form" field={this.field} {...formItemLayout}>
          <Form.Item label={locale.namespace}>
            <p>{this.tenant ? this.tenant : McpServerManagementRouteName}</p>
          </Form.Item>
          <FormItem label={locale.serverName} required>
            <Input
              {...init('serverName', {
                rules: [
                  {
                    required: true,
                    message: locale.serverNameCannotBeEmpty,
                  },
                  { validator: this.validateChart.bind(this) },
                ],
              })}
              maxLength={255}
              addonTextBefore={
                this.state.addonBefore ? (
                  <div style={{ minWidth: 100, color: '#373D41' }}>{this.state.addonBefore}</div>
                ) : null
              }
              isPreview={isEdit}
            />
          </FormItem>
          {/* 服务类型 */}
          <FormItem label={locale.serverType}>
            <RadioGroup {...init('serverType', { initValue: 'stdio' })} isPreview={isEdit}>
              {['stdio', 'http', 'dubbo', 'mcp-sse', 'mcp-streamble'].map(item => {
                return (
                  <Radio key={item} id={item} value={item}>
                    {item.charAt(0).toUpperCase() + item.slice(1)}
                  </Radio>
                );
              })}
            </RadioGroup>
          </FormItem>
          {this.field.getValue('serverType') !== 'stdio' ? (
            <>
              <FormItem label={locale.backendService}>
                <RadioGroup
                  value={this.state.useExistService ? 'useExistService' : 'useRemoteService'}
                  onChange={value => {
                    this.setState({
                      useExistService: value === 'useExistService' ? true : false,
                    });
                  }}
                >
                  <Radio id="useExistService" value="useExistService">
                    {locale.useExistService}
                    {/* 使用已有服务 */}
                  </Radio>
                  <Radio id="useRemoteService" value="useRemoteService">
                    {locale.useNewService}
                    {/* 新建服务 */}
                  </Radio>
                </RadioGroup>
              </FormItem>
              {this.state.useExistService ? (
                <FormItem label={locale.serviceRef} required>
                  <Row gutter={8}>
                    <Col span={12}>
                      <FormItem label="namespace">
                        <Select
                          {...init('namespace', {
                            rules: [{ required: true, message: locale.placeSelect }],
                            props: {
                              placeholder: 'namespace',
                              dataSource: this.state.namespaceSelects,
                              onChange: this.handleNamespaceChange,
                              style: { width: '100%' },
                            },
                          })}
                        />
                      </FormItem>
                    </Col>
                    <Col span={12}>
                      <FormItem label="service">
                        <Select
                          {...init('service', {
                            rules: [{ required: true, message: locale.placeSelect }],
                            props: {
                              dataSource: this.state.serviceList,
                              style: { width: '100%' },
                              placeholder: 'service',
                            },
                          })}
                        />
                      </FormItem>
                    </Col>
                  </Row>
                </FormItem>
              ) : (
                <FormItem label={locale.useNewService} required>
                  <Row gutter={8}>
                    <Col span={12}>
                      <FormItem label="address">
                        <Input
                          {...init('address', {
                            rules: [{ required: true, message: locale.pleaseEnter }],
                          })}
                          style={{ width: '100%' }}
                        />
                      </FormItem>
                    </Col>
                    <Col span={4}>
                      <FormItem label="port">
                        <Input
                          {...init('port', {
                            rules: [{ required: true, message: locale.pleaseEnter }],
                          })}
                        />
                      </FormItem>
                    </Col>
                  </Row>
                </FormItem>
              )}
              {/* 暴露路径 */}
              <FormItem label={locale.exportPath} required>
                <Input
                  {...init('exportPath', {
                    rules: [
                      {
                        required: true,
                        message: locale.pleaseEnter,
                      },
                    ],
                  })}
                  maxLength={255}
                  addonTextBefore={
                    this.state.addonBefore ? (
                      <div style={{ minWidth: 100, color: '#373D41' }}>
                        {this.state.addonBefore}
                      </div>
                    ) : null
                  }
                />
              </FormItem>
            </>
          ) : (
            // Local Server 配置
            <>
              <FormItem label={locale.localServerConfig}>
                <Input.TextArea {...init('localServerConfig', { props: textAreaProps })} />
              </FormItem>
            </>
          )}
          <FormItem label={locale.description} required>
            <Input.TextArea
              {...init('description', {
                rules: [
                  {
                    required: true,
                    message: locale.pleaseEnter,
                  },
                ],
                props: textAreaProps,
              })}
            />
          </FormItem>
          {/* 服务版本 */}
          <FormItem label={locale.serverVersion}>
            <Input {...init('version', { props: { placeholder: '1.0.0' } })} />
          </FormItem>

          <FormItem label=" ">
            <div style={{ textAlign: 'right' }}>
              <Button
                type={'primary'}
                style={{ marginRight: 10 }}
                onClick={this.publishConfig.bind(this)}
              >
                {isEdit ? locale.updateExit : locale.escExit}
              </Button>

              <Button type={'normal'} onClick={this.goList.bind(this)}>
                {locale.release}
              </Button>
            </div>
          </FormItem>
        </Form>
        <SuccessDialog ref={this.successDialog} />
      </Loading>
    );
  }
}

export default NewMcpServer;
