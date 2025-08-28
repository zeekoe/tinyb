#!/bin/bash

# Script created by Tim Littlefair, adapted by zeekoe to build binary release artifacts for
# github fork of the orphan tinyb library originally owned by 
# intel-iot-core

osname=$(uname --kernel-name)
archname=$(uname --machine)
if [ "$osname" != "Linux" ]
then
    echo This script presently only supports Linux
    exit 1
else
    so_arch_libdir="linux-$archname"
fi

# We want reproducible builds so that we can compare
# precompiled .jar and .so files with git and know
# whether they have changed.
# The following definition helps with .so files.
export SOURCE_DATE_EPOCH=0

rm -rf build
mkdir build
cd build
cmake -DBUILDJAVA=ON ..
make all

cp ./java/tinyb.jar ../precompiled-release-artifacts/tinyb-0.5.1.tl250617.jar
cp ./src/libtinyb.so ../precompiled-release-artifacts/$so_arch_libdir
cp ./java/jni/libjavatinyb.so ../precompiled-release-artifacts/$so_arch_libdir

cd ../precompiled-release-artifacts
zip tinyb-0.5.1.tl250617.zip *.jar -r linux*
echo Git status:
echo -----------
git status  
echo -----------
cd ../build