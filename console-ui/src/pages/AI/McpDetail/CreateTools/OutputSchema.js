/* eslint-disable react/jsx-indent-props */
import React from 'react';
import PropTypes from 'prop-types';
import SchemaEditor from './SchemaEditor';

const OutputSchema = ({
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
            rootKey="out"
            rootLabel={locale.OutputArgumentsList || locale.ArgumentsList}
            valueName="outputToolParams"
            requiredName="outputRequired"
            bindingPrefix="outputArgs"
            initialRawData={initialRawData}
            initialArgs={initialArgs}
            readOnly={isPreview || onlyEditRuntimeInfo}
            refreshKey={refreshKey}
        />
    );
};

OutputSchema.propTypes = {
    locale: PropTypes.object.isRequired,
    field: PropTypes.object.isRequired,
    isPreview: PropTypes.bool,
    onlyEditRuntimeInfo: PropTypes.bool,
    initialRawData: PropTypes.array,
    initialArgs: PropTypes.object,
    refreshKey: PropTypes.number,
};

export default OutputSchema;
