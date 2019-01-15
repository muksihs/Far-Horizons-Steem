#!/bin/bash

cd "$(dirname "$0")"

./gradlew clean build fatjar -xtest
rsync --verbose --progress -z build/libs/Far-Horizons-Steem.jar muksihs@muksihs.com:Far-Horizons/.
date

