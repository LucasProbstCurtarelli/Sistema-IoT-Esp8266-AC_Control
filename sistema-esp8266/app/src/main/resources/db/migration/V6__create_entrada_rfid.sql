-- Allowlist of RFID UIDs and the single entrance calibration row.
-- The ESP does not store either value. The next dark arrival reads them from here.

CREATE TABLE IF NOT EXISTS `chave_autorizada` (
    `id` CHAR(36) NOT NULL,
    `uid` VARCHAR(20) NOT NULL,
    `rotulo` VARCHAR(100) NULL,
    `created_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_chave_autorizada_uid` (`uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ajuste_entrada` (
    `id` INT NOT NULL,
    `lux_threshold` INT NULL,
    `normal_color` VARCHAR(7) NULL,
    `normal_brightness` INT NULL,
    `prefer_white` TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `ajuste_entrada` (`id`, `lux_threshold`, `normal_color`, `normal_brightness`, `prefer_white`)
SELECT 1, NULL, NULL, NULL, 0
WHERE NOT EXISTS (SELECT 1 FROM `ajuste_entrada` WHERE `id` = 1);
