-- Phase 05: permissions for money movement.

INSERT INTO permissions (id, name, description) VALUES
    ('a0000000-0000-4000-8000-00000000000b', 'transaction:read',  'Read transactions and account history'),
    ('a0000000-0000-4000-8000-00000000000c', 'transaction:write', 'Post deposits, withdrawals and transfers');

INSERT INTO role_permissions (role_id, permission_id)
SELECT 'b0000000-0000-4000-8000-000000000001', id
  FROM permissions
 WHERE id IN ('a0000000-0000-4000-8000-00000000000b', 'a0000000-0000-4000-8000-00000000000c');

-- OFFICER and TELLER both move money: taking a deposit over the counter is a teller's job.
INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('b0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-00000000000b'),
    ('b0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-00000000000c'),
    ('b0000000-0000-4000-8000-000000000003', 'a0000000-0000-4000-8000-00000000000b'),
    ('b0000000-0000-4000-8000-000000000003', 'a0000000-0000-4000-8000-00000000000c');
