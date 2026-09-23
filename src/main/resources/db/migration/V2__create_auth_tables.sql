-- Phase 03: authentication and authorization.
--
-- Security rules encoded in this schema (CLAUDE.md section 4):
--   * passwords are stored as a hash, never in plaintext and never reversibly encrypted;
--   * refresh tokens are stored as a SHA-256 hash, so a database dump does not hand an
--     attacker usable tokens — the raw value exists only in the client's hands;
--   * password reset tokens follow the same rule and are single use;
--   * refresh tokens record their rotation chain, which is what makes reuse detectable.

CREATE TABLE users (
    id                    UUID         NOT NULL,
    username              VARCHAR(100) NOT NULL,
    email                 VARCHAR(255) NOT NULL,
    -- BCrypt hash with its algorithm prefix, e.g. {bcrypt}$2a$12$...
    password_hash         VARCHAR(255) NOT NULL,
    full_name             VARCHAR(150) NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    -- Set when the account is temporarily locked after repeated failed logins.
    locked_until          TIMESTAMPTZ,
    -- Lets a password change invalidate anything issued before it.
    password_changed_at   TIMESTAMPTZ  NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED'))
);

-- Both are normalised to lower case before they are stored, so these unique indexes are
-- case-insensitive guarantees and also serve the login lookup.
CREATE UNIQUE INDEX ux_users_username ON users (username);
CREATE UNIQUE INDEX ux_users_email ON users (email);

CREATE TABLE roles (
    id          UUID         NOT NULL,
    name        VARCHAR(50)  NOT NULL,
    description VARCHAR(255) NOT NULL,

    CONSTRAINT pk_roles PRIMARY KEY (id)
);
CREATE UNIQUE INDEX ux_roles_name ON roles (name);

CREATE TABLE permissions (
    id          UUID         NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255) NOT NULL,

    CONSTRAINT pk_permissions PRIMARY KEY (id)
);
CREATE UNIQUE INDEX ux_permissions_name ON permissions (name);

CREATE TABLE role_permissions (
    role_id       UUID NOT NULL,
    permission_id UUID NOT NULL,

    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
);
CREATE INDEX ix_role_permissions_permission ON role_permissions (permission_id);

CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,

    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
);
CREATE INDEX ix_user_roles_role ON user_roles (role_id);

CREATE TABLE refresh_tokens (
    id          UUID        NOT NULL,
    user_id     UUID        NOT NULL,
    -- SHA-256 hex of the raw token. A refresh token is a high-entropy random value, so a fast
    -- hash is the right tool here; BCrypt exists to slow down guessing of low-entropy
    -- human-chosen passwords, which this is not.
    token_hash  VARCHAR(64) NOT NULL,
    issued_at   TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    -- The token issued when this one was rotated. Presenting an already-rotated token means
    -- it leaked, and the whole chain is revoked.
    replaced_by UUID,

    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    -- ON DELETE SET NULL, not the default RESTRICT: the pointer is an audit trail of the
    -- rotation chain, not an ownership link. Without it, deleting a token (or cascading from a
    -- deleted user) would be blocked by the very chain it is part of.
    CONSTRAINT fk_refresh_tokens_replaced_by FOREIGN KEY (replaced_by) REFERENCES refresh_tokens (id)
        ON DELETE SET NULL
);
CREATE UNIQUE INDEX ux_refresh_tokens_hash ON refresh_tokens (token_hash);
CREATE INDEX ix_refresh_tokens_user_expires ON refresh_tokens (user_id, expires_at);

CREATE TABLE password_reset_tokens (
    id         UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    -- Single use: set the moment the token is redeemed.
    used_at    TIMESTAMPTZ,

    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX ux_password_reset_tokens_hash ON password_reset_tokens (token_hash);
CREATE INDEX ix_password_reset_tokens_user ON password_reset_tokens (user_id, expires_at);

COMMENT ON TABLE users IS 'Application users. Passwords are stored hashed, never in plaintext.';
COMMENT ON TABLE refresh_tokens IS 'Hashed, rotating refresh tokens with a chain for reuse detection.';
