/--#!/bin/bash

set -u

docker rm --force treehub-mariadb || true

mkdir entrypoint.d/ || true

echo "
CREATE DATABASE ota_treehub;
GRANT ALL PRIVILEGES ON \`ota\_treehub%\`.* TO 'treehub'@'%';
FLUSH PRIVILEGES;
" > entrypoint.d/db_user.sql

docker run -d \
  --name treehub-mariadb \
  -p 3306:3306 \
  -v $(pwd)/entrypoint.d:/docker-entrypoint-initdb.d \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_USER=treehub \
  -e MYSQL_PASSWORD=treehub \
  mariadb:10.1 \
  --character-set-server=utf8 --collation-server=utf8_unicode_ci \
  --max_connections=1000 --innodb_log_file_size=2097152 \
  --max_allowed_packet=1073741824

function mysqladmin_alive {
    docker run \
           --link treehub-mariadb \
           mariadb:10.1 \
           mysqladmin ping --protocol=TCP -h treehub-mariadb -P 3306 -u root -proot
}

TRIES=60
TIMEOUT=1s

for t in `seq $TRIES`; do
    res=$(mysqladmin_alive || true)
    if [[ $res =~ "mysqld is alive" ]]; then
        echo "mysql is ready"
        exit 0
    else
        echo "Waiting for mariadb"
        sleep $TIMEOUT
    fi
done

exit -1

