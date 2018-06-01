#!/bin/bash

cd "$(dirname "$0")"

gradle clean build fatjar -xtest && scp build/libs/Far-Horizons-Steem.jar muksihs@muksihs.com:Far-Horizons/. && date

