#!/usr/bin/env bash

set -eu

rm -rf github-apps-support
git clone https://github.com/navikt/github-apps-support.git
export PATH=`pwd`/github-apps-support/bin:$PATH


kubectl config current-context
kubectl config use-context preprod-fss
kubectl apply -f nais.yaml



echo $BUILDKITE_GITHUB_DEPLOYMENT_ID
