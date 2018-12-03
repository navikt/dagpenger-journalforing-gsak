#!/usr/bin/env bash
set -e

echo "$DOCKER_PASSWORD" | docker login --username "$DOCKER_USERNAME" --password-stdin

./gradlew dockerPush${VERSION} -x check

jq -n --arg ref $VERSION '{ "ref": $ref, "description": "Deploy from travis", "required_contexts": []  }' >> deploy.json

echo "DEPLOYING TO GITHUB"
cat deploy.json

curl -X POST -H "Authorization: token $GH_TOKEN"  https://api.github.com/repos/navikt/dagpenger-journalforing-gsak/deployments --data @deploy.json
