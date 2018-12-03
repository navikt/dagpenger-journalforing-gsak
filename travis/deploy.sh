#!/usr/bin/env bash

echo "$DOCKER_PASSWORD" | docker login --username "$DOCKER_USERNAME" --password-stdin

./gradlew dockerPush${VERSION} -x check

curl -X POST -i -H "Authorization: token $GH_TOKEN"  https://api.github.com/repos/navikt/dagpenger-journalforing-gsak/deployments --data "{ ref: $VERSION, description: 'Deploy from travis', 'required_contexts': []  }"
