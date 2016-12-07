


-- we do not drop blobs until we are sure everything is migrated manually by users
-- this migration can take a few minutes
alter table `object` change column `blob` `blob` longblob null ;

-- Write the following migration after local fs is stable
-- alter table object drop column `blob` ;

