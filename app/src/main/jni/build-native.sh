#!/bin/sh
set -eu
: "${PHONE_NDK_PATH:?Set PHONE_NDK_PATH to an Android NDK directory}"
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
compiler="$PHONE_NDK_PATH/toolchains/llvm/prebuilt/linux-x86_64/bin"
for entry in 'arm64-v8a:aarch64-linux-android26' 'armeabi-v7a:armv7a-linux-androideabi26' 'x86_64:x86_64-linux-android26' 'x86:i686-linux-android26'; do
  abi=${entry%%:*}; target=${entry#*:}; mkdir -p "$script_dir/../jniLibs/$abi"
  "$compiler/$target-clang" -shared -fPIC -O2 -Wall -Wextra -Werror -o "$script_dir/../jniLibs/$abi/libphonepty.so" "$script_dir/phonepty.c"
done
