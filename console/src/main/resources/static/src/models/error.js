
export default {

    namespace: 'error',

    state: {
        errinfo: '未知错误',
        errcode: 0,
        erralert: false,
    },

    subscriptions: {
        setup({ dispatch, history }) {  // eslint-disable-line
        },
    },

    effects: {
        *fetch({ payload }, { call, put }) {  // eslint-disable-line
            yield put({ type: 'save' });
        },
    },

    reducers: {
        save(state, action) {
            return { ...state, ...action.payload, errcode: 1 };
        },
        clear(state, action) {
            return { ...state, errinfo: '', errcode: 0 };
        },
    },

};
