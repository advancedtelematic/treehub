#!/bin/bash
set -e

if [[ "$1" == "" ]]; then
    echo "usage: $0 <docker_tag>"
    exit -1
fi

export DOCKER_TAG=$1
export JOB_NAME="${JOB_NAME-treehub}"
export VAULT_ENDPOINT=${VAULT_ENDPOINT-$(echo $JOB_NAME | tr "-" "_")}
export IMAGE_NAME="treehub"
export REGISTRY="advancedtelematic"
export IMAGE_ARTIFACT=${REGISTRY}/${IMAGE_NAME}:${DOCKER_TAG}
export HOST="0.0.0.0"
export PORT="8084"
export AUTH_PROTOCOL="oauth.accesstoken"
export AUTH_VERIFICATION="auth-plus"
export DB_MIGRATE="true"

if [[ "$JOB_NAME" == "treehub" ]]; then # production uses 4G
    export JAVA_OPTS="-Xmx4G"
    export CONTAINER_MEM="4096.0"
else
    export JAVA_OPTS="-Xmx1024m"
    export CONTAINER_MEM="1024.0"
fi

# Merge service environment variables with secrets from this vault endpoint.
export CATALOG_ADDR="http://catalog.gw.prod01.internal.advancedtelematic.com"

REQ=$(envsubst < deploy/service.json)
curl --show-error --silent --fail \
     --header "X-Vault-Token: ${VAULT_TOKEN}" \
     --request POST \
     --data "$REQ" \
     ${CATALOG_ADDR}/service/${VAULT_ENDPOINT}
