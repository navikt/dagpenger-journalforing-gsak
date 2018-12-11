#!/usr/bin/env bash

set -eu

git clone https://github.com/navikt/github-apps-support.git
export PATH=`pwd`/github-apps-support/bin:$PATH


echo $BUILDKITE_GITHUB_DEPLOYMENT_ID
