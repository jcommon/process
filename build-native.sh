#!/bin/sh -e

CURR_DIR=`pwd`
TOP=$(dirname $0)/.
ROOT=$( (cd "$TOP" && pwd) )

BIN_DIR=$ROOT/src/main/resources/native/unix
SRC_DIR=$ROOT/src/main/c
OBJ_DIR=$ROOT/target/objs

DEPENDENCIES_DIR=$ROOT/dependencies/native/unix

build_full() {
  gcc=$1
  compile_options=$2
  link_options=$3
  exe=$4
  obj=$5
  src=$6

  exedir=$(dirname $exe)
  mkdir -p "$BIN_DIR/$exedir"

  echo Building $exe...

  echo "$gcc" $compile_options -I"$SRC_DIR" -o "$OBJ_DIR/$obj" -c "$SRC_DIR/$src"
  echo "$gcc" $link_options -o "$BIN_DIR/$exe" "$OBJ_DIR/$obj"
}

build() {
  name=$1
  build_full $DEPENDENCIES_DIR/musl-gcc-x86_64 "-m64 -static -Os" "" x86_64/${name} ${name}.o ${name}.c
  build_full $DEPENDENCIES_DIR/musl-gcc-x86 "-m32 -static -Os" "-m32" x86/${name} ${name}.o ${name}.c
}

cd "$ROOT"

#setup
mkdir -p $BIN_DIR
mkdir -p $OBJ_DIR


#build_all
build spawn
echo Build complete.


#done
cd "$CURR_DIR"

