#!/bin/sh -e

repeat=$1
value=$2

x=1
while [ $x -le $repeat ]
do
   even_or_odd=$[ $x % 2 ]

   if [ $even_or_odd -eq 1  ]; then
     echo "M:$x" "$value"
   else
     echo "M:$x" "$value" 1>&2
   fi

   x=$(( $x + 1 ))
done
