-- The app adds these columns automatically on first launch (see DatabaseConnection.ensureSchema).
-- Run this by hand only if your MySQL user is not allowed to ALTER tables.
ALTER TABLE transactions ADD COLUMN approved_at DATETIME NULL;
ALTER TABLE transactions ADD COLUMN return_requested_on DATE NULL;
ALTER TABLE transactions ADD COLUMN late_fee_charged DECIMAL(10,2) NOT NULL DEFAULT 0;
ALTER TABLE transactions ADD COLUMN return_condition VARCHAR(40) NULL;
ALTER TABLE transactions ADD COLUMN damage_report TEXT NULL;
-- status values are now: PENDING, RESERVED, BORROWED, PENDING RETURN, RETURNED, REJECTED, CANCELLED
-- If cancelling a request does nothing, the status columns are still ENUMs. Run:
ALTER TABLE transactions MODIFY status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE equipment MODIFY status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE';
