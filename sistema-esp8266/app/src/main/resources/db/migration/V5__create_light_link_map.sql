-- Stores the lamp link map edited from the panel.
-- One row holds the whole map as JSON text so a saved document round-trips on GET.
-- The map is directed: a link from A to B does not insert the reverse link.

CREATE TABLE IF NOT EXISTS `light_link_map` (
    `id` INT NOT NULL,
    `links_json` LONGTEXT NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
