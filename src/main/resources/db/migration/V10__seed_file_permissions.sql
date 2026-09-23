-- Phase 07: permissions for files, notifications and the audit trail.

INSERT INTO permissions (id, name, description) VALUES
    ('a0000000-0000-4000-8000-00000000000d', 'file:read',   'Download stored files'),
    ('a0000000-0000-4000-8000-00000000000e', 'file:write',  'Upload and delete files'),
    ('a0000000-0000-4000-8000-00000000000f', 'audit:read',  'Read the audit trail');

-- ADMIN keeps everything, including the audit trail — reading it is itself a privileged action.
INSERT INTO role_permissions (role_id, permission_id)
SELECT 'b0000000-0000-4000-8000-000000000001', id
  FROM permissions
 WHERE id IN ('a0000000-0000-4000-8000-00000000000d',
              'a0000000-0000-4000-8000-00000000000e',
              'a0000000-0000-4000-8000-00000000000f');

-- OFFICER handles customer documents; TELLER may look at them but not upload.
INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('b0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-00000000000d'),
    ('b0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-00000000000e'),
    ('b0000000-0000-4000-8000-000000000003', 'a0000000-0000-4000-8000-00000000000d');
