#!/usr/bin/env bash

export FASTLANE_DISABLE_COLORS=1
export REALM_DISABLE_ANALYTICS=1
export BUNDLE_PATH="${HOME}/.bundle"

du -hsc /usr/lib/android-ndk/sources/*

cp .env.jenkins .env
bundle install --quiet
make prepare-android
