cmake_minimum_required(VERSION 3.22.1)
project(scrcpyforandroid)

set(CMAKE_CXX_STANDARD 17)
add_compile_options(-fno-exceptions -fno-rtti -fno-threadsafe-statics)

find_package(boringssl REQUIRED CONFIG)
find_package(cxx REQUIRED CONFIG)

add_library(adbpairing SHARED adb_pairing.cpp)

target_link_libraries(
    adbpairing
    log
    boringssl::crypto_static
    cxx::cxx
)
