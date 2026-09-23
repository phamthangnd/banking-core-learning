-- Phase 03: the initial role and permission catalogue.
--
-- Roles and permissions are reference data, not user data: they are part of the application's
-- contract, so they belong in a migration where they are versioned and reviewed rather than in
-- a startup routine that silently differs between environments.
--
-- No user is seeded here. A user row would need a password, and a password in a committed
-- migration is a committed secret. The first administrator is created at startup from an
-- environment variable (see AdminBootstrap).

INSERT INTO permissions (id, name, description) VALUES
    ('a0000000-0000-4000-8000-000000000001', 'customer:read',  'Read customer records'),
    ('a0000000-0000-4000-8000-000000000002', 'customer:write', 'Create and update customer records'),
    ('a0000000-0000-4000-8000-000000000003', 'customer:close', 'Close a customer'),
    ('a0000000-0000-4000-8000-000000000004', 'user:read',      'Read user accounts'),
    ('a0000000-0000-4000-8000-000000000005', 'user:manage',    'Create, lock and assign roles to users'),
    ('a0000000-0000-4000-8000-000000000006', 'profile:read',   'Read own profile');

INSERT INTO roles (id, name, description) VALUES
    ('b0000000-0000-4000-8000-000000000001', 'ADMIN',    'Full administrative access'),
    ('b0000000-0000-4000-8000-000000000002', 'OFFICER',  'Manages customer records'),
    ('b0000000-0000-4000-8000-000000000003', 'TELLER',   'Reads customer records'),
    ('b0000000-0000-4000-8000-000000000004', 'CUSTOMER', 'Self-service access only');

-- ADMIN gets every permission that exists today.
INSERT INTO role_permissions (role_id, permission_id)
SELECT 'b0000000-0000-4000-8000-000000000001', id FROM permissions;

-- OFFICER: full customer lifecycle, no user administration.
INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('b0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000001'),
    ('b0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000002'),
    ('b0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000003'),
    ('b0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000006');

-- TELLER: read only. Least privilege is the default, not the exception.
INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('b0000000-0000-4000-8000-000000000003', 'a0000000-0000-4000-8000-000000000001'),
    ('b0000000-0000-4000-8000-000000000003', 'a0000000-0000-4000-8000-000000000006');

-- CUSTOMER: own profile only. Registration assigns this role.
INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('b0000000-0000-4000-8000-000000000004', 'a0000000-0000-4000-8000-000000000006');
