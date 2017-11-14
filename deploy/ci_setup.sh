#!/bin/bash

set -u

docker rm --force treehub-mariadb

# Some jobs don't behave, nuke them all
if [[ `docker ps -q | wc -l` -gt 0 ]]; then
    docker ps -q | xargs docker rm --force
fi

if [[ ! -d "entrypoint.d/" ]]; then
    mkdir --verbose entrypoint.d/
fi

echo "
CREATE DATABASE ota_treehub;
GRANT ALL PRIVILEGES ON \`ota\_treehub%\`.* TO 'treehub'@'%';
FLUSH PRIVILEGES;
" > entrypoint.d/db_user.sql

MYSQL_PORT=${MYSQL_PORT-3306}

docker run -d \
  --name treehub-mariadb \
  -p $MYSQL_PORT:3306 \
  -v $(pwd)/entrypoint.d:/docker-entrypoint-initdb.d \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_USER=treehub \
  -e MYSQL_PASSWORD=treehub \
  mariadb:10.1 \
  --character-set-server=utf8 --collation-server=utf8_unicode_ci \
  --max_connections=1000

function mysqladmin_alive {
    docker run \
           --rm \
           --link treehub-mariadb \
           mariadb:10.1 \
           mysqladmin ping --protocol=TCP -h treehub-mariadb -P 3306 -u root -proot
}

TRIES=60
TIMEOUT=1s

for t in `seq $TRIES`; do
    res=$(mysqladmin_alive)
    if [[ $res =~ "mysqld is alive" ]]; then
        echo "mysql is ready"
        exit 0
    else
        echo "Waiting for mariadb"
        sleep $TIMEOUT
    fi
done

exit -1

