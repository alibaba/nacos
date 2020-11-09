/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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
import { request } from '@/globalLib';
import { Input, Button, Card, ConfigProvider, Form, Loading, Message } from '@alifd/next';
import EditServiceDialog from './EditServiceDialog';
import EditClusterDialog from './EditClusterDialog';
import InstanceTable from './InstanceTable';
import { getParameter } from 'utils/nacosutil';
import MonacoEditor from 'components/MonacoEditor';
import { MONACO_READONLY_OPTIONS, METADATA_ENTER } from './constant';
import './ServiceDetail.scss';

const FormItem = Form.Item;
const pageFormLayout = {
  labelCol: { fixedSpan: 10 },
  wrapperCol: { span: 14 },
};

@ConfigProvider.config
class ServiceDetail extends React.Component {
  static displayName = 'ServiceDetail';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
    location: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.editServiceDialog = React.createRef();
    this.editClusterDialog = React.createRef();
    this.state = {
      serviceName: getParameter(props.location.search, 'name'),
      groupName: getParameter(props.location.search, 'groupName'),
      loading: false,
      currentPage: 1,
      clusters: [],
      instances: {},
      service: {},
      pageSize: 10,
      pageNum: {},
    };
  }

  componentDidMount() {
    if (!this.state.serviceName) {
      this.props.history.goBack();
      return;
    }
    this.getServiceDetail();
  }

  getServiceDetail() {
    const { serviceName, groupName } = this.state;
    request({
      url: `v1/ns/catalog/service?serviceName=${serviceName}&groupName=${groupName}`,
      beforeSend: () => this.openLoading(),
      success: ({ clusters = [], service = {} }) => this.setState({ service, clusters }),
      error: e => Message.error(e.responseText || 'error'),
      complete: () => this.closeLoading(),
    });
  }

  openLoading() {
    this.setState({ loading: true });
  }

  closeLoading() {
    this.setState({ loading: false });
  }

  openEditServiceDialog() {
    this.editServiceDialog.current.getInstance().show(this.state.service);
  }

  openClusterDialog(cluster) {
    this.editClusterDialog.current.getInstance().show(cluster);
  }

  render() {
    const { locale = {} } = this.props;
    const { serviceName, groupName, loading, service = {}, clusters } = this.state;
    const { metadata = {}, selector = {} } = service;
    let metadataText = '';
    if (Object.keys(metadata).length) {
      metadataText = JSON.stringify(metadata, null, '\t');
    }
    return (
      <div className="main-container service-detail">
        <Loading
          shape={'flower'}
          tip={'Loading...'}
          className="loading"
          visible={loading}
          color={'#333'}
        >
          <h1
            style={{
              position: 'relative',
              width: '100%',
            }}
          >
            {locale.serviceDetails}
            <Button
              type="primary"
              className="header-btn"
              onClick={() => this.props.history.goBack()}
            >
              {locale.back}
            </Button>
            <Button
              type="normal"
              className="header-btn"
              onClick={() => this.openEditServiceDialog()}
            >
              {locale.editService}
            </Button>
          </h1>

          <Form {...pageFormLayout}>
            <FormItem label={`${locale.serviceName}:`}>
              <Input value={service.name} readOnly />
            </FormItem>
            <FormItem label={`${locale.groupName}:`}>
              <Input value={service.groupName} readOnly />
            </FormItem>
            <FormItem label={`${locale.protectThreshold}:`}>
              <Input value={service.protectThreshold} readOnly />
            </FormItem>
            <FormItem label={`${locale.metadata}:`}>
              <MonacoEditor
                language="json"
                width={'100%'}
                height={200}
                value={metadataText}
                options={MONACO_READONLY_OPTIONS}
              />
            </FormItem>
            <FormItem label={`${locale.type}:`}>
              <Input value={selector.type} readOnly />
            </FormItem>
            {service.type === 'label' && (
              <FormItem label={`${locale.selector}:`}>
                <Input value={selector.selector} readOnly />
              </FormItem>
            )}
          </Form>
          {clusters.map(cluster => (
            <Card
              key={cluster.name}
              className="cluster-card"
              title={`${locale.cluster}:`}
              subTitle={cluster.name}
              contentHeight="auto"
              extra={
                <Button type="normal" onClick={() => this.openClusterDialog(cluster)}>
                  {locale.editCluster}
                </Button>
              }
            >
              <InstanceTable
                clusterName={cluster.name}
                serviceName={serviceName}
                groupName={groupName}
              />
            </Card>
          ))}
        </Loading>
        <EditServiceDialog
          ref={this.editServiceDialog}
          openLoading={() => this.openLoading()}
          closeLoading={() => this.closeLoading()}
          getServiceDetail={() => this.getServiceDetail()}
        />
        <EditClusterDialog
          ref={this.editClusterDialog}
          openLoading={() => this.openLoading()}
          closeLoading={() => this.closeLoading()}
          getServiceDetail={() => this.getServiceDetail()}
        />
      </div>
    );
  }
}

export default ServiceDetail;
