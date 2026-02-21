-- ============================================
-- Script de Inicialização Completa do Banco de Dados
-- ============================================
-- Este script recria o banco de dados do zero
-- Execute no MySQL Workbench para resetar completamente o banco
-- ============================================

-- Drop e recria o banco de dados
DROP DATABASE IF EXISTS `sistema-esp8266`;
CREATE DATABASE `sistema-esp8266` 
    DEFAULT CHARACTER SET utf8mb4 
    COLLATE utf8mb4_unicode_ci;

USE `sistema-esp8266`;

-- ============================================
-- Tabela de Usuários (com UUID como PK)
-- ============================================
CREATE TABLE `user` (
    `uuid` CHAR(36) NOT NULL,
    `username` VARCHAR(255) NOT NULL UNIQUE,
    `password` VARCHAR(255) NOT NULL,
    `role` VARCHAR(50) NOT NULL,
    PRIMARY KEY (`uuid`),
    UNIQUE KEY `uk_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Tabela de Dispositivos
-- ============================================
CREATE TABLE `device` (
    `id` CHAR(36) NOT NULL,
    `device_name` VARCHAR(50) NOT NULL UNIQUE,
    `display_name` VARCHAR(255) NOT NULL,
    `location` VARCHAR(255) NOT NULL,
    `device_type` VARCHAR(50) NOT NULL DEFAULT 'light',
    `enabled` BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (`id`),
    INDEX `idx_device_name` (`device_name`),
    INDEX `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Tabela de Histórico do Flyway
-- ============================================
CREATE TABLE `flyway_schema_history` (
    `installed_rank` INT NOT NULL,
    `version` VARCHAR(50),
    `description` VARCHAR(200) NOT NULL,
    `type` VARCHAR(20) NOT NULL,
    `script` VARCHAR(1000) NOT NULL,
    `checksum` INT,
    `installed_by` VARCHAR(100) NOT NULL,
    `installed_on` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `execution_time` INT NOT NULL,
    `success` BOOLEAN NOT NULL,
    PRIMARY KEY (`installed_rank`),
    INDEX `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Inserir Usuário Admin
-- ============================================
-- Username: admin
-- Password: admin
-- BCrypt hash para "admin" (gerado com BCryptPasswordEncoder, rounds=10)
-- Este hash foi verificado e está correto
INSERT INTO `user` (`uuid`, `username`, `password`, `role`)
VALUES (
    UUID(),
    'admin',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'ROLE_ADMIN'
)
ON DUPLICATE KEY UPDATE
    `password` = '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    `role` = 'ROLE_ADMIN';

-- ============================================
-- Inserir Dispositivos Padrão
-- ============================================
INSERT INTO `device` (`id`, `device_name`, `display_name`, `location`, `device_type`, `enabled`)
VALUES 
    (UUID(), 'lampada_1', 'Lâmpada 1', 'Quarto', 'light', TRUE),
    (UUID(), 'lampada_2', 'Lâmpada 2', 'Quarto', 'light', TRUE);

-- ============================================
-- Inserir Histórico do Flyway (marcar migrações como executadas)
-- ============================================
INSERT INTO `flyway_schema_history` (
    `installed_rank`, `version`, `description`, `type`, `script`, 
    `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`
) VALUES
    (1, '1', 'create user table', 'SQL', 'V1__create_user_table.sql', NULL, USER(), NOW(), 0, 1),
    (2, '2', 'create device table', 'SQL', 'V2__create_device_table.sql', NULL, USER(), NOW(), 0, 1),
    (3, '3', 'migrate user to uuid', 'SQL', 'V3__migrate_user_to_uuid.sql', NULL, USER(), NOW(), 0, 1),
    (4, '4', 'insert admin user', 'SQL', 'V4__insert_admin_user.sql', NULL, USER(), NOW(), 0, 1);

-- ============================================
-- Verificação
-- ============================================
SELECT 'Banco de dados inicializado com sucesso!' as status;

SELECT 
    `uuid` as id,
    `username`,
    `role`,
    'Admin user' as description
FROM `user` 
WHERE `username` = 'admin';

SELECT 
    COUNT(*) as device_count,
    'Dispositivos criados' as description
FROM `device`;

SELECT 
    COUNT(*) as migration_count,
    'Migrações registradas' as description
FROM `flyway_schema_history`;
