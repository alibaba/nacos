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

package com.alibaba.nacos.naming.cluster;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;

/**
 * Member node of Nacos cluster
 *
 * // TODO This object will be deleted sometime after version 1.3.0
 *
 * @author nkorange
 * @since 1.0.0
 * @deprecated 1.3.0
 */
public class Server implements Comparable<Server> {

	/**
	 * IP of member
	 */
	private String ip;

	/**
	 * serving port of member.
	 */
	private int servePort;

	private String site = UtilsAndCommons.UNKNOWN_SITE;

	private int weight = 1;

	/**
	 * additional weight, used to adjust manually
	 */
	private int adWeight;

	private boolean alive = false;

	private long lastRefTime = 0L;

	private String lastRefTimeStr;

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public int getServePort() {
		return servePort;
	}

	public void setServePort(int servePort) {
		this.servePort = servePort;
	}

	public String getSite() {
		return site;
	}

	public void setSite(String site) {
		this.site = site;
	}

	public int getWeight() {
		return weight;
	}

	public void setWeight(int weight) {
		this.weight = weight;
	}

	public int getAdWeight() {
		return adWeight;
	}

	public void setAdWeight(int adWeight) {
		this.adWeight = adWeight;
	}

	public boolean isAlive() {
		return alive;
	}

	public void setAlive(boolean alive) {
		this.alive = alive;
	}

	public long getLastRefTime() {
		return lastRefTime;
	}

	public void setLastRefTime(long lastRefTime) {
		this.lastRefTime = lastRefTime;
	}

	public String getLastRefTimeStr() {
		return lastRefTimeStr;
	}

	public void setLastRefTimeStr(String lastRefTimeStr) {
		this.lastRefTimeStr = lastRefTimeStr;
	}

	public String getKey() {
		return ip + UtilsAndCommons.IP_PORT_SPLITER + servePort;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		Server server = (Server) o;
		return servePort == server.servePort && ip.equals(server.ip);
	}

	@Override
	public int hashCode() {
		int result = ip.hashCode();
		result = 31 * result + servePort;
		return result;
	}

	@Override
	public String toString() {
		return JacksonUtils.toJson(this);
	}

	@Override
	public int compareTo(Server server) {
		if (server == null) {
			return 1;
		}
		return this.getKey().compareTo(server.getKey());
	}

	public static ServerBuilder builder() {
		return new ServerBuilder();
	}

	public static final class ServerBuilder {
		private String ip;
		private int servePort;
		private String site = UtilsAndCommons.UNKNOWN_SITE;
		private int weight = 1;
		private int adWeight;
		private boolean alive = false;
		private long lastRefTime = 0L;
		private String lastRefTimeStr;

		private ServerBuilder() {
		}

		public ServerBuilder ip(String ip) {
			this.ip = ip;
			return this;
		}

		public ServerBuilder servePort(int servePort) {
			this.servePort = servePort;
			return this;
		}

		public ServerBuilder site(String site) {
			this.site = site;
			return this;
		}

		public ServerBuilder weight(int weight) {
			this.weight = weight;
			return this;
		}

		public ServerBuilder adWeight(int adWeight) {
			this.adWeight = adWeight;
			return this;
		}

		public ServerBuilder alive(boolean alive) {
			this.alive = alive;
			return this;
		}

		public ServerBuilder lastRefTime(long lastRefTime) {
			this.lastRefTime = lastRefTime;
			return this;
		}

		public ServerBuilder lastRefTimeStr(String lastRefTimeStr) {
			this.lastRefTimeStr = lastRefTimeStr;
			return this;
		}

		public Server build() {
			Server server = new Server();
			server.setIp(ip);
			server.setServePort(servePort);
			server.setSite(site);
			server.setWeight(weight);
			server.setAdWeight(adWeight);
			server.setAlive(alive);
			server.setLastRefTime(lastRefTime);
			server.setLastRefTimeStr(lastRefTimeStr);
			return server;
		}
	}
}