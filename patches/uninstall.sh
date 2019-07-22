#!/bin/bash
cd ../../..
cd system/core
git reset --hard && git clean -fd
cd ../..
cd frameworks/native
git reset --hard && git clean -fd
cd ../..
cd frameworks/base
git reset --hard && git clean -fd
cd ../..
cd frameworks/opt/telephony
git reset --hard && git clean -fd
cd ../..
