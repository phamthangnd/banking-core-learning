-- Phase 04: permissions for the account module and for KYC review.

INSERT INTO permissions (id, name, description) VALUES
    ('a0000000-0000-4000-8000-000000000007', 'account:read',  'Read accounts'),
    ('a0000000-0000-4000-8000-000000000008', 'account:write', 'Open accounts and change their lifecycle state'),
    ('a0000000-0000-4000-8000-000000000009', 'account:close', 'Close an account'),
    ('a0000000-0000-4000-8000-00000000000a', 'customer:kyc',  'Review and decide a customer KYC submission');

-- ADMIN keeps every permission.
INSERT INTO role_permissions (role_id, permission_id)
SELECT 'b0000000-0000-4000-8000-000000000001', id
  FROM permissions
 WHERE id IN ('a0000000-0000-4000-8000-000000000007',
              'a0000000-0000-4000-8000-000000000008',
              'a0000000-0000-4000-8000-000000000009',
              'a0000000-0000-4000-8000-00000000000a');

-- OFFICER runs the customer and account lifecycle, including the KYC decision.
INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('b0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000007'),
    ('b0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000008'),
    ('b0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000009'),
    ('b0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-00000000000a');

-- TELLER may look accounts up, and nothing more. Least privilege stays the default.
INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('b0000000-0000-4000-8000-000000000003', 'a0000000-0000-4000-8000-000000000007');
