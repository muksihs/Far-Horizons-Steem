#!/bin/bash

unset DISPLAY;
clear;

gradle clean build fatjar -xtest || (echo "ERROR" && exit -1)

echo "===";
echo "--- $(date): Announce Game";
java -jar build/libs/Far-Horizons-Steem.jar --announce-game

