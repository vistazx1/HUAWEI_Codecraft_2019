#!/bin/bash
sh build.sh
cd bin
sh startup.sh config/car.txt config/road.txt config/cross.txt config/answer.txt
