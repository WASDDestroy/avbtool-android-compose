# SPDX-License-Identifier: Apache-2.0
cmake_minimum_required(VERSION 3.22.1)
project(avbfec)

set(CMAKE_CXX_STANDARD 17)

# LGPL-2.1 Reed-Solomon codec (AOSP external/fec). Kept as a separate
# shared library so it can be replaced/relinked independently, as
# required by the LGPL-2.1.
add_library(fec_rs SHARED
    fec_rs/init_rs_char.c
    fec_rs/encode_rs_char.c
)
target_include_directories(fec_rs PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

# Apache-2.0 native bridge. Links dynamically against libfec_rs.so.
add_library(avbfec SHARED
    avb_fec.cpp
)
target_include_directories(avbfec PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

find_library(log-lib log)
target_link_libraries(avbfec PRIVATE fec_rs ${log-lib})
