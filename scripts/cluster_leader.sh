#!/usr/bin/env bash

r=$RANDOM
filename=.gl$r.command

echo 'get -b akka:type=Cluster -d akka Leader' > $filename

java -jar bin/jmxterm-1.0.1-uber.jar -l localhost:9010 -i $filename -v silent -n -e

rm $filename

