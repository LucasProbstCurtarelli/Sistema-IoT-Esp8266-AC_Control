-- Insert admin user for testing
-- Username: admin
-- Password: admin
-- 
-- This migration creates an admin user with a BCrypt-hashed password.
-- The BCrypt hash for "admin" is generated using Spring Security's BCryptPasswordEncoder.
-- 
-- This will run automatically when Flyway migrations execute.
-- Note: This will only insert if the user doesn't already exist (based on username uniqueness)

-- BCrypt hash for password "admin" (generated with BCryptPasswordEncoder, rounds=10)
-- You can verify this matches by checking: new BCryptPasswordEncoder().encode("admin")
SET @admin_password_hash = '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy';

-- Insert admin user (only if username doesn't exist)
INSERT INTO `user` (`uuid`, `username`, `password`, `role`)
VALUES (
    UUID(),
    'admin',
    @admin_password_hash,
    'ROLE_ADMIN'
)
ON DUPLICATE KEY UPDATE 
    `username` = `username`; -- If admin already exists, do nothing
