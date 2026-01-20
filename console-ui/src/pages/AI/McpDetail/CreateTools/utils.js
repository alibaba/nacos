
export const convertPropertiesToTreeData = (properties, prefix, store, requiredList = []) => {
    if (properties == null) {
        return [];
    }
    const keys = Object.keys(properties);
    let result = [];
    for (let index = 0; index < keys.length; index++) {
        const element = keys[index];
        const arg = properties[element];
        let children = [];
        if (arg.type === 'object') {
            // Nested objects don't process their required list for now (currently only supports root-level required)
            children = convertPropertiesToTreeData(arg.properties, `${prefix}@@${element}`, store);
        } else if (arg.type === 'array') {
            children = convertPropertiesToTreeData(
                {
                    items: arg.items,
                },
                `${prefix}@@${element}`,
                store
            );
        }
        const node = {
            label: element,
            type: arg.type,
            arg: arg,
            description: arg.description ? arg.description : '',
            defaultValue: arg.default || '',
            // Only root-level required uses inputSchema.required for marking
            required: Array.isArray(requiredList) ? requiredList.includes(element) : false,
            children,
            key: `${prefix}@@${element}`,
        };
        result.push(node);
        store[`${prefix}@@${element}`] = node;
    }
    return result;
};

export const rawDataToFiledValue = rawData => {
    const result = {};
    if (!rawData) {
        return result;
    }
    for (let index = 0; index < rawData.length; index++) {
        const element = rawData[index];
        let arg = {
            ...element.arg,
            type: element.type,
        };
        // items node doesn't need description
        if (!String(element.key || '').endsWith('@@items')) {
            arg.description = element.description;
        } else if (arg && arg.description !== undefined) {
            delete arg.description;
        }
        arg.type = element.type;
        if (
            element.defaultValue !== undefined &&
            element.defaultValue !== '' &&
            !String(element.key || '').endsWith('@@items')
        ) {
            // Set default value based on type
            if (element.type === 'boolean') {
                arg.default = element.defaultValue === 'true';
            } else if (element.type === 'number') {
                const num = parseFloat(element.defaultValue);
                if (!isNaN(num)) {
                    arg.default = num;
                }
            } else if (element.type === 'integer') {
                const int = parseInt(element.defaultValue, 10);
                if (!isNaN(int)) {
                    arg.default = int;
                }
            } else {
                arg.default = element.defaultValue;
            }
        }
        if (element.type === 'object' && element.children.length > 0) {
            arg.properties = rawDataToFiledValue(element.children);
        } else if (element.type === 'array') {
            arg.items = rawDataToFiledValue(element.children).items;
        }
        result[element.label] = arg;
    }
    return result;
};
