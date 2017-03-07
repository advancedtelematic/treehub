#!/bin/bash

set -e

git fetch origin --tags

git checkout release
git merge master --ff-only
git push origin

git checkout stable
git reset --hard origin/stable
git merge origin/release --ff-only
git push origin

