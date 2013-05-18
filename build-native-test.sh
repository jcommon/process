#!/bin/sh -e

CURR_DIR=`pwd`
TOP=$(dirname $0)/.
ROOT=$( (cd "$TOP" && pwd) )

BIN_DIR=$ROOT/src/test/resources/native/unix
SRC_DIR=$ROOT/src/test/c
OBJ_DIR=$ROOT/target/objs

build_full() {
  gcc=$1
  options=$2
  exe=$3
  obj=$4
  src=$5

  exedir=$(dirname $exe)
  mkdir -p "$BIN_DIR/$exedir"

  echo Building $exe...

  "$gcc" $options -I"$SRC_DIR" -o "$OBJ_DIR/$obj" -c "$SRC_DIR/$src"
  "$gcc" $options -o "$BIN_DIR/$exe" "$OBJ_DIR/$obj"
}

build() {
  name=$1
  build_full gcc -m32 x86/${name} ${name}.o ${name}.c
  build_full gcc -m64 x86_64/${name} ${name}.o ${name}.c
}

cd "$ROOT"

#setup
mkdir -p $BIN_DIR
mkdir -p $OBJ_DIR


#build_all
build stdin-1
build stderr-1
build stdout-1
echo Build complete.


#done
cd "$CURR_DIR"
