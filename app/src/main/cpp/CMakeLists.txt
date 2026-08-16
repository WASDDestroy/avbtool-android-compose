cmake_minimum_required(VERSION 3.22.1)
project(avbfec)

set(CMAKE_CXX_STANDARD 17)

add_library(avbfec SHARED
    avb_fec.cpp
    fec_rs/init_rs_char.c
    fec_rs/encode_rs_char.c
)

target_include_directories(avbfec PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

find_library(log-lib log)
target_link_libraries(avbfec ${log-lib})
