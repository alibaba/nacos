INSERT INTO users (username, password, enabled) VALUES ('ncoss', '$2a$10$nUWqYSAQxbu36t3NQ9vFjepf97Swn5AscmAf3W7PWqotI7huFdeN.', 1);
INSERT INTO users (username, password, enabled) VALUES ('ncoss-api', '$2a$10$81XZ4VcdmkYmc7ef.gswOuO1f6UWp1PoWyyqPfnj3d5gCDnE2JhBa', 1);
UPDATE users SET password='$2a$10$QJUUmYBdozpKC4Px5v9cFehdGJkdFX8eOEqkDsnnvyrfw63YwWcvK' WHERE username='nacos';

INSERT INTO roles (username, role) VALUES ('ncoss', 'ROLE_ADMIN');
INSERT INTO roles (username, role) VALUES ('ncoss-api', 'ROLE_ADMIN');