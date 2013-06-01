#!/bin/sh -e

#To determine the musl version used, please see build-native-musl-cross.sh

CURR_DIR=`pwd`
TOP=$(dirname $0)/.
ROOT=$( (cd "$TOP" && pwd) )

OBJ_DIR=/tmp/build/jcommon-process

OPT_DIR=$ROOT/opt/native/unix
SRC_DIR=$ROOT/src/main/c
OUTPUT_DIR=$ROOT/src/main/resources/native/unix



build() {
  arch=$1
  cross_gcc=$2
  name=$3

  obj_base=$OBJ_DIR/$arch
  output_base=$OUTPUT_DIR/$arch/bin
  src=$SRC_DIR/$name.c
  obj=$obj_base/$name.o
  out=$output_base/$name

  mkdir -p "$obj_base"
  mkdir -p "$output_base"

  gcc=$OPT_DIR/$cross_gcc/bin/${cross_gcc}-gcc

  #opts="$opts -fauto-inc-dec -fcompare-elim -fcprop-registers -fdce -fdefer-pop -fdse -fguess-branch-probability -fif-conversion2 -fif-conversion -fipa-pure-const -fipa-profile -fipa-reference -fmerge-constants -fsplit-wide-types -ftree-bit-ccp -ftree-builtin-call-dce -ftree-ccp -ftree-ch -ftree-copyrename -ftree-dce -ftree-dominator-opts -ftree-dse -ftree-forwprop -ftree-fre -ftree-phiprop -ftree-sra -ftree-pta -ftree-ter -funit-at-a-time"
  #opts="$opts -fthread-jumps -fcaller-saves -fcrossjumping -fcse-follow-jumps  -fcse-skip-blocks -fdelete-null-pointer-checks -fdevirtualize -fexpensive-optimizations -fgcse -fgcse-lm -finline-small-functions -findirect-inlining -fipa-sra -foptimize-sibling-calls -fpartial-inlining -fpeephole2 -fregmove -freorder-functions -frerun-cse-after-loop -fsched-interblock  -fsched-spec -fschedule-insns  -fschedule-insns2 -fstrict-aliasing -fstrict-overflow -ftree-switch-conversion -ftree-tail-merge -ftree-pre -ftree-vrp"

  "$gcc" -Os -I"$SRC_DIR" -o "$obj" -c "$src"
  "$gcc" -static -static-libgcc -o "$out" "$obj"
}

build_for_all_architectures() {
  name=$1

  echo Building ${name}...

  build x86 i686-linux-musl $name
  build x86_64 x86_64-linux-musl $name
}

#Ensure that we've unpacked the cross compilers.
cd "$OPT_DIR"
for a in i686 x86_64
do
  dest_dir=${a}-linux-musl
  tar_file=${a}-linux-musl.tar.xz
  echo Unpacking ${tar_file}...

  test -d "$dest_dir" || tar Jxvf "$tar_file"
done

#Clean things up first.
rm -rf "$OBJ_DIR"

#Build the necessary processes for all supported architectures.
build_for_all_architectures spawn



cd "$CURR_DIR"

