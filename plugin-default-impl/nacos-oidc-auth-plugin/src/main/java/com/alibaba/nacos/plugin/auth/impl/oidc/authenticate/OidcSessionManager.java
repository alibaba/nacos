/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.oidc.authenticate;

import com.alibaba.nacos.plugin.auth.impl.oidc.identity.OidcUserMapper.OidcUser;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Manages OIDC user sessions.
 * Provides session token generation and user lookup.
 *
 * @author WangzJi
 */
@SuppressWarnings("PMD")
public class OidcSessionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(OidcSessionManager.class);

    private static volatile OidcSessionManager instance;

    /**
     * Session cache: sessionToken -> OidcUser.
     */
    private final Cache<String, OidcUser> sessionCache;

    /**
     * Secure random for token generation.
     */
    private final SecureRandom secureRandom;

    /**
     * Default session timeout: 8 hours.
     */
    private static final long DEFAULT_SESSION_TIMEOUT_HOURS = 8;

    private OidcSessionManager() {
        this.secureRandom = new SecureRandom();
        this.sessionCache = Caffeine.newBuilder()
                .expireAfterAccess(DEFAULT_SESSION_TIMEOUT_HOURS, TimeUnit.HOURS)
                .maximumSize(10000)
                .build();
    }

    /**
     * Get singleton instance.
     *
     * @return OidcSessionManager instance
     */
    public static OidcSessionManager getInstance() {
        if (instance == null) {
            synchronized (OidcSessionManager.class) {
                if (instance == null) {
                    instance = new OidcSessionManager();
                }
            }
        }
        return instance;
    }

    /**
     * Create a new session for the user.
     *
     * @param user OidcUser to create session for
     * @return session token
     */
    public String createSession(OidcUser user) {
        String sessionToken = generateSessionToken();
        sessionCache.put(sessionToken, user);
        LOGGER.debug("Created session for user: {}", user.getUsername());
        return sessionToken;
    }

    /**
     * Get user from session token.
     *
     * @param sessionToken session token
     * @return OidcUser or null if not found/expired
     */
    public OidcUser getUser(String sessionToken) {
        if (sessionToken == null) {
            return null;
        }
        return sessionCache.getIfPresent(sessionToken);
    }

    /**
     * Invalidate a session.
     *
     * @param sessionToken session token to invalidate
     */
    public void invalidateSession(String sessionToken) {
        if (sessionToken != null) {
            OidcUser user = sessionCache.getIfPresent(sessionToken);
            sessionCache.invalidate(sessionToken);
            if (user != null) {
                LOGGER.debug("Invalidated session for user: {}", user.getUsername());
            }
        }
    }

    /**
     * Refresh session expiration.
     *
     * @param sessionToken session token
     * @return true if session was refreshed, false if not found
     */
    public boolean refreshSession(String sessionToken) {
        OidcUser user = sessionCache.getIfPresent(sessionToken);
        if (user != null) {
            // Re-put to refresh expiration
            sessionCache.put(sessionToken, user);
            return true;
        }
        return false;
    }

    /**
     * Update user in session.
     *
     * @param sessionToken session token
     * @param user         updated user
     */
    public void updateSession(String sessionToken, OidcUser user) {
        if (sessionToken != null && user != null) {
            sessionCache.put(sessionToken, user);
        }
    }

    /**
     * Get approximate number of active sessions.
     *
     * @return number of active sessions
     */
    public long getActiveSessionCount() {
        return sessionCache.estimatedSize();
    }

    /**
     * Clear all sessions (for admin/maintenance).
     */
    public void clearAllSessions() {
        sessionCache.invalidateAll();
        LOGGER.info("All sessions cleared");
    }

    /**
     * Generate a secure session token.
     *
     * @return base64-encoded random token
     */
    private String generateSessionToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
