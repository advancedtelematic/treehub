ALTER TABLE `ref`
ADD COLUMN version_temp VARCHAR(50);

UPDATE `ref` q
INNER JOIN `ref` a
ON q.name = a.name AND q.namespace = a.namespace
SET q.version_temp = CONCAT('0.0.', cast(a.version as CHAR))
WHERE q.version_temp IS NULL;

ALTER TABLE `ref`
DROP COLUMN version;

ALTER TABLE `ref`
CHANGE version_temp version VARCHAR(50) NOT NULL DEFAULT '0.0.0';
