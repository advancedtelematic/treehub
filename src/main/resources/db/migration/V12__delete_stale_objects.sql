create table _deleted_objects as
select * from object where `size` = -1;

delete from object where `size` = -1
;
