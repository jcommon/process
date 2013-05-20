#!/bin/sh -e

#Uses musl v0.9.10

CURR_DIR=`pwd`
TOP=$(dirname $0)/.
ROOT=$( (cd "$TOP" && pwd) )

REPO_URI=https://bitbucket.org/GregorR/musl-cross
DIR_NAME=musl-cross

BASE_SRC_DIR=/tmp/src
BASE_BUILD_DIR=/tmp/build
BASE_INSTALL_DIR=$ROOT/opt/native/unix

SRC_DIR=$BASE_SRC_DIR/$DIR_NAME
BUILD_SH=$SRC_DIR/build.sh
BUILD_GCC_DEPS_SH=$SRC_DIR/extra/build-gcc-deps.sh
BUILD_TARBALLS_SH=$SRC_DIR/extra/build-tarballs.sh

#Check dependencies
which m4 > /dev/null || sudo apt-get install m4
which hg > /dev/null || sudo apt-get install mercurial
which makeinfo > /dev/null || sudo apt-get install texinfo
test -f /usr/include/gmp.h || sudo apt-get install libgmp-dev
test -f /usr/include/mpfr.h || sudo apt-get install libmpfr-dev
test -f /usr/include/mpc.h || sudo apt-get install libmpc-dev



mkdir -p "$SRC_DIR"
cd "$SRC_DIR"
test -f "$BUILD_SH" || hg clone "$REPO_URI" .

PKG_BUILD_DIR=$BASE_BUILD_DIR/$DIR_NAME
mkdir -p "$PKG_BUILD_DIR"
cd "$PKG_BUILD_DIR"



#Append additional architectures on the end in order to cross-compile from x86_64 to that architecture.
#./extra/build-tarballs.sh <install dir> <tarball prefix> <tarball suffix> <host arch> <1...n target architectures>
"$BUILD_TARBALLS_SH" "$BASE_INSTALL_DIR" "" "-native" x86_64 i686



cd "$CURR_DIR"

