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

import React, { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Input, ConfigProvider, Button, Form, Tag, Card } from '@alifd/next';
import { isDiff } from './util';

const { Group: TagGroup, Closeable: CloseableTag } = Tag;
const FormItem = Form.Item;

function InstanceFilter(props) {
  const [key, setKey] = useState('');
  const [value, setValue] = useState('');
  const [keyState, setKeyState] = useState('');
  const [valueState, setValueState] = useState('');
  const [filters, setFilters] = useState(new Map());
  const { locale = {} } = props;

  const addFilter = () => {
    updateInput();

    if (key && value) {
      const newFilters = new Map(Array.from(filters)).set(key, value);

      setFilters(newFilters);
      setKeyState('');
      setValueState('');

      clearInput();
    }
  };

  const removeFilter = key => {
    const newFilters = new Map(Array.from(filters));
    newFilters.delete(key);

    setFilters(newFilters);
  };

  const clearFilters = () => {
    setFilters(new Map());
  };

  const clearInput = () => {
    setKey('');
    setValue('');
  };

  const updateInput = () => {
    if (!key) {
      setKeyState('error');
    } else {
      setKeyState('');
    }

    if (!value) {
      setValueState('error');
    } else {
      setValueState('');
    }
  };

  useEffect(() => {
    props.setFilters(filters);
  }, [filters]);

  return (
    <Card contentHeight="auto" className="inner-card">
      <Form inline size="small">
        <FormItem label={locale.title}>
          <FormItem>
            <Input
              placeholder={'key'}
              value={key}
              trim
              onChange={key => setKey(key)}
              onPressEnter={addFilter}
              state={keyState}
            />
          </FormItem>
          <FormItem>
            <Input
              placeholder={'value'}
              value={value}
              trim
              onChange={value => setValue(value)}
              onPressEnter={addFilter}
              state={valueState}
            />
          </FormItem>
          <FormItem label="">
            <Button type="primary" onClick={addFilter} style={{ marginRight: 10 }}>
              {locale.addFilter}
            </Button>
            {filters.size > 0 ? (
              <Button type="primary" onClick={clearFilters}>
                {locale.clear}
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
            <CloseableTag size="medium" key={filter[0]} onClose={() => removeFilter(filter[0])}>
              {`${filter[0]} : ${filter[1]}`}
            </CloseableTag>
          );
        })}
      </TagGroup>
    </Card>
  );
}

InstanceFilter.propTypes = {
  locale: PropTypes.object,
  setFilters: PropTypes.func.isRequired,
};

export default ConfigProvider.config(InstanceFilter);
