ALTER DATABASE CHARACTER SET utf8 COLLATE utf8_unicode_ci;

--
-- Table structure for table `object`
--

CREATE TABLE `object` (
  `namespace` varchar(254) COLLATE utf8_unicode_ci NOT NULL,
  `object_id` varchar(254) COLLATE utf8_unicode_ci NOT NULL,
  `blob` LONGBLOB NOT NULL,
  PRIMARY KEY (`namespace`, `object_id`),
  CONSTRAINT object_unique_namespace UNIQUE (`namespace`, `object_id`)
) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

--
-- Table structure for table `ref`
--

CREATE TABLE `ref` (
  `namespace` varchar(254) COLLATE utf8_unicode_ci NOT NULL,
  `name` varchar(254) COLLATE utf8_unicode_ci NOT NULL,
  `value` text COLLATE utf8_unicode_ci NOT NULL,
  `object_id` varchar(254) COLLATE utf8_unicode_ci NOT NULL,
  PRIMARY KEY (`namespace`, `name`),
  FOREIGN KEY (`namespace`, `object_id`) REFERENCES object(`namespace`, `object_id`)
) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
