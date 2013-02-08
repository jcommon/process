#!/bin/sh -e

value=$1
repeat=$2

x=1
while [ $x -le $repeat ]
do
   echo "M:$x" "$value"
   x=$(( $x + 1 ))
done
