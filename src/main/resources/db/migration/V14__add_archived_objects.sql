CREATE TABLE `archived_object` (
  `namespace` varchar(254) NOT NULL,
  `object_id` varchar(254) NOT NULL,
  `size` bigint(20) NOT NULL,
  `reason` TEXT NOT NULL,
  `client_created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),
  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  PRIMARY KEY (`namespace`,`object_id`)
)
;
