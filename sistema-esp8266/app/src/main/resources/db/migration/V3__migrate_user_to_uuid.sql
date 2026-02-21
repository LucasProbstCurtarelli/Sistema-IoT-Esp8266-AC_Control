-- Migrate User table to use UUID as primary key
-- This migration is idempotent - it only runs if the table has 'id' column (BIGINT)
-- If the table already has UUID as primary key (from setup_database.sql), this migration does nothing

-- Note: This migration assumes the table structure. If UUID is already PK, 
-- the ALTER statements will fail gracefully or be skipped by Flyway validation.
-- If you get errors, run fix_flyway_history.sql to repair the migration history.

-- Check if migration is needed by attempting to add UUID column (will fail silently if exists)
-- We use a simple approach: try to add the column, ignore errors if it already exists

-- Step 1: Add UUID column (only if it doesn't exist - MySQL will error if exists, but Flyway will handle it)
-- We'll use a workaround: check via a SELECT that won't fail
SET @uuid_exists = (
    SELECT COUNT(*) 
    FROM information_schema.columns 
    WHERE table_schema = DATABASE()
    AND table_name = 'user' 
    AND column_name = 'uuid'
);

-- If UUID column doesn't exist and id column exists, perform migration
SET @id_exists = (
    SELECT COUNT(*) 
    FROM information_schema.columns 
    WHERE table_schema = DATABASE()
    AND table_name = 'user' 
    AND column_name = 'id'
    AND data_type = 'bigint'
);

-- Since we can't use IF in plain SQL without procedures, we'll make this migration
-- safe by only running if the table structure indicates migration is needed
-- For now, we'll make it a no-op if UUID already exists as PK

-- Actually, the simplest approach: if UUID is already PK, this migration should be skipped
-- But Flyway doesn't support conditional migrations easily
-- So we'll make it idempotent by using IF NOT EXISTS where possible

-- For MySQL, we need to handle this differently. Let's use a simpler approach:
-- Just check if we need to migrate, and if not, do nothing (but Flyway requires at least one statement)

-- Since the table might already be migrated, we'll use a safe approach:
-- Only execute migration steps if the conditions are met
-- We'll use a temporary approach with a variable check

-- Simplest solution: Make this a no-op if UUID is already the primary key
-- We'll use a SELECT that always succeeds to make Flyway happy
SELECT 1 as migration_check;

-- The actual migration will be handled by checking the table state
-- If UUID is already PK, the ALTER statements below will either:
-- 1. Fail (if column doesn't exist) - but we check first
-- 2. Succeed but do nothing (if already in correct state)

-- For a truly idempotent migration, we need to check first
-- Since MySQL doesn't support IF in DDL easily, we'll use a workaround:
-- Create a view to check, then conditionally execute

-- Actually, the best approach for Flyway is to make this migration
-- that checks the state and only executes if needed
-- But since we can't easily do conditional DDL, we'll make it safe by
-- ensuring the table is in the expected state before running

-- Final approach: Use a stored procedure that's idempotent
-- But stored procedures in Flyway can be problematic
-- So we'll use a simpler approach: just execute the migration
-- and handle errors gracefully

-- Since the user table might already have UUID (from setup_database.sql),
-- we need to make this truly idempotent. The best way is to check first.

-- Let's use a simple approach: try to add column, it will fail if exists but that's ok
-- Actually, better: use a stored procedure that checks first

DELIMITER $$

DROP PROCEDURE IF EXISTS migrate_user_to_uuid$$

CREATE PROCEDURE migrate_user_to_uuid()
BEGIN
    DECLARE uuid_is_pk INT DEFAULT 0;
    DECLARE id_exists INT DEFAULT 0;
    
    -- Check if UUID is already primary key
    SELECT COUNT(*) INTO uuid_is_pk
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu 
        ON tc.constraint_name = kcu.constraint_name
        AND tc.table_schema = kcu.table_schema
    WHERE tc.table_schema = DATABASE()
    AND tc.table_name = 'user'
    AND tc.constraint_type = 'PRIMARY KEY'
    AND kcu.column_name = 'uuid';
    
    -- Check if id column exists
    SELECT COUNT(*) INTO id_exists
    FROM information_schema.columns 
    WHERE table_schema = DATABASE()
    AND table_name = 'user' 
    AND column_name = 'id'
    AND data_type = 'bigint';
    
    -- Only migrate if UUID is not PK and id exists
    IF uuid_is_pk = 0 AND id_exists > 0 THEN
        -- Add UUID column if it doesn't exist
        SET @col_exists = (
            SELECT COUNT(*) FROM information_schema.columns 
            WHERE table_schema = DATABASE() 
            AND table_name = 'user' 
            AND column_name = 'uuid'
        );
        
        IF @col_exists = 0 THEN
            ALTER TABLE `user` ADD COLUMN `uuid` CHAR(36) NULL AFTER `id`;
        END IF;
        
        -- Generate UUIDs
        UPDATE `user` SET `uuid` = UUID() WHERE `uuid` IS NULL;
        
        -- Make UUID NOT NULL
        ALTER TABLE `user` MODIFY `uuid` CHAR(36) NOT NULL;
        
        -- Add unique constraint if it doesn't exist
        SET @uk_exists = (
            SELECT COUNT(*) FROM information_schema.table_constraints 
            WHERE table_schema = DATABASE()
            AND table_name = 'user' 
            AND constraint_name = 'uk_user_uuid'
        );
        
        IF @uk_exists = 0 THEN
            ALTER TABLE `user` ADD UNIQUE KEY `uk_user_uuid` (`uuid`);
        END IF;
        
        -- Drop old PK if id is still PK
        SET @id_is_pk = (
            SELECT COUNT(*) FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu 
                ON tc.constraint_name = kcu.constraint_name
                AND tc.table_schema = kcu.table_schema
            WHERE tc.table_schema = DATABASE()
            AND tc.table_name = 'user'
            AND tc.constraint_type = 'PRIMARY KEY'
            AND kcu.column_name = 'id'
        );
        
        IF @id_is_pk > 0 THEN
            ALTER TABLE `user` DROP PRIMARY KEY;
        END IF;
        
        -- Set UUID as PK
        ALTER TABLE `user` ADD PRIMARY KEY (`uuid`);
    END IF;
END$$

DELIMITER ;

-- Execute migration
CALL migrate_user_to_uuid();

-- Clean up
DROP PROCEDURE IF EXISTS migrate_user_to_uuid;
