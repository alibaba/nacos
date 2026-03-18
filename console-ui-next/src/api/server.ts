import client from './client';
import type { AxiosPromise } from 'axios';

export interface ServerState {
  version: string;
  standaloneMode: string;
  functionMode: string;
  loginPageEnabled: boolean;
  authEnabled: boolean;
  consoleUiEnable: boolean;
  startupMode: string;
  configRetentionDays: number;
  authAdminRequest: boolean;
}

export interface AnnouncementResponse {
  data: string;
}

export interface GuideResponse {
  data: string;
}

export const serverApi = {
  getState: (): AxiosPromise<ServerState> =>
    client.get('v3/console/server/state'),
  
  getAnnouncement: (language: string): AxiosPromise<AnnouncementResponse> =>
    client.get(`v3/console/server/announcement?language=${language}`),
  
  getGuide: (): AxiosPromise<GuideResponse> =>
    client.get('v3/console/server/guide'),
};
