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
export KAFKA_TOPIC_SUFFIX="${DEPLOY_ENV-production}"

export CLUSTER_CONSTRAINT="${CLUSTER_CONSTRAINT}-ostree}"

if [[ "$JOB_NAME" == "treehub" ]]; then # production
    export JAVA_OPTS="-Xmx1800m"
    export CONTAINER_MEM="2048.0"
    export CONTAINER_CPU="0.9"
else
    export JAVA_OPTS="-Xmx900m"
    export CONTAINER_MEM="1024.0"
    export CONTAINER_CPU="0.8"
fi

# Merge service environment variables with secrets from this vault endpoint.
export CATALOG_ADDR="http://catalog.gw.prod01.internal.advancedtelematic.com"

function deploy {
  REQ=$(envsubst < deploy/service.json)
  curl --show-error --silent --fail \
     --header "X-Vault-Token: ${VAULT_TOKEN}" \
     --request POST \
     --data "$REQ" \
     ${CATALOG_ADDR}/service/${VAULT_ENDPOINT}
}

export SERVICE_SCOPE="public"
export AUTH_PROTOCOL="oauth.accesstoken"
export AUTH_VERIFICATION="auth-plus"
deploy

export JOB_NAME="${JOB_NAME}-internal"
export SERVICE_SCOPE="internal"
export AUTH_PROTOCOL="none"
export AUTH_VERIFICATION="none"
deploy
