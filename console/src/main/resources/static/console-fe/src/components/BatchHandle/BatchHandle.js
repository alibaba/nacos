/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import { Dialog, Pagination, Transfer } from '@alifd/next';
import { request } from '../../globalLib';
import './index.scss';

class BatchHandle extends React.Component {
  static propTypes = {
    valueList: PropTypes.array,
    dataSource: PropTypes.array,
    onSubmit: PropTypes.func,
  };

  constructor(props) {
    super(props);
    this.state = {
      visible: false,
      valueList: props.valueList || [],
      dataSourceList: props.dataSource || [],
      currentPage: 1,
      total: 0,
      pageSize: 10,
      dataSource: {},
    };
  }

  componentDidMount() {}

  openDialog(dataSource) {
    this.setState(
      {
        visible: true,
        dataSource,
        pageSize: dataSource.pageSize,
      },
      () => {
        this.getData();
        this.transfer._instance.filterCheckedValue = function(left, right, dataSource) {
          const result = {
            left,
            right,
          };

          return result;
        };
      }
    );
  }

  closeDialog() {
    this.setState({
      visible: false,
    });
  }

  getData() {
    const { dataSource } = this.state;
    request({
      url: `/diamond-ops/configList/serverId/${dataSource.serverId}?dataId=${
        dataSource.dataId
      }&group=${dataSource.group}&appName=${
        dataSource.appName
      }&config_tags=${dataSource.config_tags || ''}&pageNo=${this.state.currentPage}&pageSize=${
        dataSource.pageSize
      }`,
      success: res => {
        if (res.code === 200) {
          this.setState({
            dataSourceList:
              res.data.map(obj => ({
                label: obj.dataId,
                value: obj.dataId,
              })) || [],
            total: res.total,
          });
        }
      },
    });
  }

  changePage(currentPage) {
    this.setState(
      {
        currentPage,
      },
      () => {
        this.getData();
      }
    );
  }

  onChange(valueList, data, extra) {
    this.setState({
      valueList,
    });
  }

  onSubmit() {
    this.props.onSubmit && this.props.onSubmit(this.state.valueList);
  }

  render() {
    // console.log("valueList: ", this.state.valueList, this.transfer);

    return (
      <Dialog
        visible={this.state.visible}
        style={{ width: '500px' }}
        onCancel={this.closeDialog.bind(this)}
        onClose={this.closeDialog.bind(this)}
        onOk={this.onSubmit.bind(this)}
        title={'批量操作'}
      >
        <div>
          <Transfer
            ref={ref => (this.transfer = ref)}
            listStyle={{ height: 350 }}
            dataSource={this.state.dataSourceList || []}
            value={this.state.valueList}
            onChange={this.onChange.bind(this)}
          />
          <Pagination
            style={{ marginTop: 10 }}
            current={this.state.currentPage}
            total={this.state.total}
            pageSize={this.state.pageSize}
            onChange={this.changePage.bind(this)}
            type="simple"
          />
        </div>
      </Dialog>
    );
  }
}

export default BatchHandle;
