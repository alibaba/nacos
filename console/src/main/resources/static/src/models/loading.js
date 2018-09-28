
export default {

    namespace: 'loading',

    state: {
        loading: false
    },

    subscriptions: {
        setup({ dispatch, history }) {  // eslint-disable-line
        },
    },

    effects: {

        *open({ payload }, { call, put }) {
            try {
                yield put({ type: 'save', payload: { loading: true } });
            } catch (e) {
                yield put({ type: 'error/save', payload: { errinfo: e.message } })
            }
        },
        *close({ payload }, { call, put }) {
            try {
                yield put({ type: 'save', payload: { loading: false } });
            } catch (e) {
                yield put({ type: 'error/save', payload: { errinfo: e.message } })
            }
        }
    },

    reducers: {
        save(state, action) {
            return { ...state, ...action.payload };
        }
    },

};
