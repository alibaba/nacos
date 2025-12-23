/* eslint-disable react/jsx-indent-props */
import React from 'react';
import PropTypes from 'prop-types';
import SchemaEditor from './SchemaEditor';

const InputSchema = ({
    locale,
    field,
    isPreview,
    onlyEditRuntimeInfo,
    initialRawData,
    initialArgs,
    refreshKey,
}) => {
    return (
        <SchemaEditor
            locale={locale}
            field={field}
            rootKey="args"
            rootLabel={locale.ArgumentsList}
            valueName="toolParams"
            requiredName="required"
            bindingPrefix="args"
            initialRawData={initialRawData}
            initialArgs={initialArgs}
            readOnly={isPreview || onlyEditRuntimeInfo}
            refreshKey={refreshKey}
        />
    );
};

InputSchema.propTypes = {
    locale: PropTypes.object.isRequired,
    field: PropTypes.object.isRequired,
    isPreview: PropTypes.bool,
    onlyEditRuntimeInfo: PropTypes.bool,
    initialRawData: PropTypes.array,
    initialArgs: PropTypes.object,
    refreshKey: PropTypes.number,
};

export default InputSchema;
