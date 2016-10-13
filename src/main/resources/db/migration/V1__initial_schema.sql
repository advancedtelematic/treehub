ALTER DATABASE CHARACTER SET utf8 COLLATE utf8_unicode_ci;

--
-- Table structure for table `object`
--

CREATE TABLE `object` (
  `id` varchar(254) COLLATE utf8_unicode_ci NOT NULL,
  `blob` blob NOT NULL,
  PRIMARY KEY (`id`)
) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

--
-- Table structure for table `ref`
--

CREATE TABLE `ref` (
  `name` varchar(254) COLLATE utf8_unicode_ci NOT NULL,
  `value` text COLLATE utf8_unicode_ci NOT NULL,
  PRIMARY KEY (`name`)
) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
