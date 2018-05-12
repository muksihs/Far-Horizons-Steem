#!/bin/bash

unset DISPLAY;
clear;

gradle clean build fatjar -xtest || (echo "ERROR" && exit -1)

while [ 1 ]; do
    echo "===";
    echo "--- $(date): Payouts";
    java -jar build/libs/Far-Horizons-Steem.jar --payouts
    sleep $((30*60));
    echo "--- $(date): Run Game";\
    java -jar build/libs/Far-Horizons-Steem.jar --run-game
    sleep $((30*60));    
    echo "--- $(date): Start Game";\
    java -jar build/libs/Far-Horizons-Steem.jar --start-game
    #echo "--- $(date)";\
    sleep $((4*60*60));
done

