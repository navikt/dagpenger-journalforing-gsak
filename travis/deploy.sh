#!/usr/bin/env bash
set -e

echo "$DOCKER_PASSWORD" | docker login --username "$DOCKER_USERNAME" --password-stdin

./gradlew dockerPush${VERSION} -x check

jq -n --arg ref $VERSION '{ "ref": $ref, "description": "Deploy from travis", "required_contexts": []  }' >> deploy.json

echo "DEPLOYING TO GITHUB"
cat deploy.json

DEPLOYMENT_RESPONSE=$(curl -X POST -H "Authorization: token $GH_TOKEN"  https://api.github.com/repos/navikt/dagpenger-journalforing-gsak/deployments --data @deploy.json)

DEPLOYMENT_ID=$(echo DEPLOYMENT_RESPONSE | jq -r '.id')


if [ -z "DEPLOYMENT_ID" ];
then
   >&2 echo "Unable to obtain deployment ID"
   >&2 echo "$DEPLOYMENT_RESPONSE"
   exit 1
fi


