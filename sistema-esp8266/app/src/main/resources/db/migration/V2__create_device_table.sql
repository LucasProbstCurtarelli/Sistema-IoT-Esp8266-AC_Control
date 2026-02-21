-- Create device table
-- This migration creates the device table for storing smart device configurations

CREATE TABLE IF NOT EXISTS `device` (
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

-- Insert default devices (can be modified later via API)
INSERT INTO `device` (`id`, `device_name`, `display_name`, `location`, `device_type`, `enabled`)
VALUES 
    (UUID(), 'lampada_1', 'Lâmpada 1', 'Quarto', 'light', TRUE),
    (UUID(), 'lampada_2', 'Lâmpada 2', 'Quarto', 'light', TRUE)
ON DUPLICATE KEY UPDATE `device_name` = `device_name`;
