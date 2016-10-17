ALTER DATABASE CHARACTER SET utf8 COLLATE utf8_unicode_ci;

--
-- Table structure for table `object`
--

CREATE TABLE `object` (
  `object_id` varchar(254) COLLATE utf8_unicode_ci NOT NULL,
  `blob` LONGBLOB NOT NULL,
  PRIMARY KEY (`object_id`)
) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

--
-- Table structure for table `ref`
--

CREATE TABLE `ref` (
  `name` varchar(254) COLLATE utf8_unicode_ci NOT NULL,
  `value` text COLLATE utf8_unicode_ci NOT NULL,
  `object_id` varchar(254) COLLATE utf8_unicode_ci NOT NULL,
  PRIMARY KEY (`name`),
  FOREIGN KEY (object_id) REFERENCES object(object_id)
) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
