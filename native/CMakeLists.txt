cmake_minimum_required(VERSION 3.27)

project(mcmetal_native LANGUAGES C Swift)

if(NOT APPLE)
    message(FATAL_ERROR "MCMetal native library is supported on macOS only.")
endif()

set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_Swift_LANGUAGE_VERSION 5)

find_package(JNI REQUIRED)

add_library(minecraft_metal SHARED
    src/jni_entrypoints.c
    src/RenderStateTracker.swift
    src/MetalContext.swift
)

target_include_directories(minecraft_metal
    PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/include
        ${JNI_INCLUDE_DIRS}
)

target_link_libraries(minecraft_metal
    PRIVATE
        ${JNI_LIBRARIES}
        "-framework Foundation"
        "-framework AppKit"
        "-framework QuartzCore"
        "-framework Metal"
)

target_compile_options(minecraft_metal
    PRIVATE
        $<$<COMPILE_LANGUAGE:C>:-Wall>
        $<$<COMPILE_LANGUAGE:C>:-Wextra>
        $<$<COMPILE_LANGUAGE:C>:-Werror>
)

set_target_properties(minecraft_metal PROPERTIES
    OUTPUT_NAME "minecraft_metal"
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}"
    LIBRARY_OUTPUT_DIRECTORY_DEBUG "${CMAKE_BINARY_DIR}"
    LIBRARY_OUTPUT_DIRECTORY_RELEASE "${CMAKE_BINARY_DIR}"
    LIBRARY_OUTPUT_DIRECTORY_RELWITHDEBINFO "${CMAKE_BINARY_DIR}"
    LIBRARY_OUTPUT_DIRECTORY_MINSIZEREL "${CMAKE_BINARY_DIR}"
    XCODE_ATTRIBUTE_SWIFT_VERSION "5.0"
)
