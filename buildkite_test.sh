#!/usr/bin/env bash

set -eu


if [ -d "`pwd`/github-apps-support" ]; then
  cd github-apps-support
  git pull
  cd ..
else
    git clone https://github.com/navikt/github-apps-support.git
fi

export PATH=`pwd`/github-apps-support/bin:$PATH


echo $BUILDKITE_GITHUB_DEPLOYMENT_ID
