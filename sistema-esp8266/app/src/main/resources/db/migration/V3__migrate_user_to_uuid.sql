-- Turn the numeric user id from V1 into the uuid primary key the application expects.
-- The table is still empty at this point.

ALTER TABLE `user` ADD COLUMN `uuid` CHAR(36) NULL;
UPDATE `user` SET `uuid` = UUID() WHERE `uuid` IS NULL;
ALTER TABLE `user` MODIFY `uuid` CHAR(36) NOT NULL;
ALTER TABLE `user` ADD UNIQUE KEY `uk_user_uuid` (`uuid`);
ALTER TABLE `user` MODIFY `id` BIGINT NOT NULL;
ALTER TABLE `user` DROP PRIMARY KEY;
ALTER TABLE `user` ADD PRIMARY KEY (`uuid`);
ALTER TABLE `user` DROP COLUMN `id`;
