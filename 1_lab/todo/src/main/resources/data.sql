INSERT INTO roles (name)
SELECT 'ROLE_ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_ADMIN');

INSERT INTO roles (name)
SELECT 'ROLE_USER'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_USER');

INSERT INTO users (username)
SELECT 'admin'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');

INSERT INTO users (username)
SELECT 'user'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'user');

INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'ROLE_ADMIN'
WHERE u.username = 'admin'
  AND NOT EXISTS (
      SELECT 1
      FROM user_role ur
      WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'ROLE_USER'
WHERE u.username = 'user'
  AND NOT EXISTS (
      SELECT 1
      FROM user_role ur
      WHERE ur.user_id = u.id AND ur.role_id = r.id
  );
