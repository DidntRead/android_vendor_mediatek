#!/bin/bash
cd ../../..
cd system/core
patch -p1 < ../../vendor/mediatek/patches/system_core.patch
cd ../..
cd frameworks/native
patch -p1 < ../../vendor/mediatek/patches/frameworks_native_fence.patch
cd ../..
cd frameworks/base
patch -p1 <../../vendor/mediatek/patches/frameworks_base.patch
cd ../..
cd frameworks/opt/telephony
patch -p1 <../../../vendor/mediatek/patches/frameworks_opt_telephony.patch
cd ../..
