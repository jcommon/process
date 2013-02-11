#!/bin/sh -e

repeat=$1
value=$2

x=1
while [ $x -le $repeat ]
do
   echo "M:$x" "$value" 1>&2
   x=$(( $x + 1 ))
done
