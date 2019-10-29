CREATE TABLE `manifest` (
  `namespace` varchar(254) COLLATE utf8_unicode_ci NOT NULL,
  `object_id` varchar(254) COLLATE utf8_unicode_ci NOT NULL,
  `content` TEXT NOT NULL,

  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (`namespace`, `object_id`),
  FOREIGN KEY (`namespace`, `object_id`) REFERENCES object(`namespace`, `object_id`)
);
