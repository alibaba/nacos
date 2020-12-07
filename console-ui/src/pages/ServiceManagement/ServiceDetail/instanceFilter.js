/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React, { Fragment } from 'react';
import PropTypes from 'prop-types';
import { Input, Button, Form, Tag, Card } from '@alifd/next';
import { isDiff } from './util';

const { Group: TagGroup, Closeable: CloseableTag } = Tag;
const FormItem = Form.Item;

export default class InstanceFilter extends React.Component {
  static propTypes = {
    setFilters: PropTypes.func.isRequired,
  };

  constructor(props) {
    super(props);

    this.state = {
      key: '',
      value: '',
      filters: new Map(),
    };
  }

  addFilter = () => {
    const { key, value, filters } = this.state;

    if (key && value) {
      const newFilters = new Map(Array.from(filters)).set(key, value);
      this.setState({
        filters: newFilters,
      });
      this.clearInput();
    }
  };

  removeFilter = key => {
    const { filters } = this.state;
    const newFilters = new Map(Array.from(filters));
    newFilters.delete(key);

    this.setState({ filters: newFilters });
  };

  clearInput = () => {
    this.setState({
      key: '',
      value: '',
    });
  };

  clearFilters = () => {
    this.setState({
      filters: new Map(),
    });
  };

  componentDidUpdate(prevProps, prevState) {
    const { filters } = this.state;

    if (isDiff(prevState.filters, filters)) {
      this.props.setFilters(filters);
    }
  }

  render() {
    const { key, value, filters } = this.state;
    return (
      <Card subTitle={'元数据过滤'} contentHeight="auto" className="inner-card">
        <Form inline size="small">
          <FormItem label={'元数据过滤'}>
            <FormItem>
              <Input
                placeholder={'key'}
                value={key}
                trim
                onChange={key => this.setState({ key })}
              />
            </FormItem>
            <FormItem>
              <Input
                placeholder={'value'}
                value={value}
                trim
                onChange={value => this.setState({ value })}
              />
            </FormItem>
            <FormItem label="">
              <Button type="primary" onClick={this.addFilter} style={{ marginRight: 10 }}>
                {'添加过滤'}
              </Button>
              {filters.size > 0 ? (
                <Button type="primary" onClick={this.clearFilters}>
                  {'清空'}
                </Button>
              ) : (
                ''
              )}
            </FormItem>
          </FormItem>
        </Form>
        <TagGroup>
          {Array.from(filters).map(filter => {
            return (
              <CloseableTag
                size="medium"
                key={filter[0]}
                onClose={() => this.removeFilter(filter[0])}
              >
                {`${filter[0]} : ${filter[1]}`}
              </CloseableTag>
            );
          })}
        </TagGroup>
      </Card>
    );
  }
}
