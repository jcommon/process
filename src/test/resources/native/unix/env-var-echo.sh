#!/bin/sh -e

for i in $*; do
  var_name=$i
  var_value=${!i}

  echo ${var_name}=${var_value}
done
