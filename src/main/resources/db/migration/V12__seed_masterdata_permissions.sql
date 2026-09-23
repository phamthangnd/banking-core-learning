-- Phase 08: permissions for master data and bulk import/export.

INSERT INTO permissions (id, name, description) VALUES
    ('a0000000-0000-4000-8000-000000000010', 'masterdata:read',  'Read reference data'),
    ('a0000000-0000-4000-8000-000000000011', 'masterdata:write', 'Create and update reference data'),
    ('a0000000-0000-4000-8000-000000000012', 'customer:import',  'Import customers in bulk'),
    ('a0000000-0000-4000-8000-000000000013', 'report:export',    'Export data and statements');

-- Reference data is readable by every back-office role; changing it is an administrative act.
INSERT INTO role_permissions (role_id, permission_id)
SELECT 'b0000000-0000-4000-8000-000000000001', id
  FROM permissions
 WHERE id IN ('a0000000-0000-4000-8000-000000000010',
              'a0000000-0000-4000-8000-000000000011',
              'a0000000-0000-4000-8000-000000000012',
              'a0000000-0000-4000-8000-000000000013');

INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('b0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000010'),
    ('b0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000012'),
    ('b0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000013'),
    ('b0000000-0000-4000-8000-000000000003', 'a0000000-0000-4000-8000-000000000010'),
    ('b0000000-0000-4000-8000-000000000003', 'a0000000-0000-4000-8000-000000000013'),
    ('b0000000-0000-4000-8000-000000000004', 'a0000000-0000-4000-8000-000000000010');
