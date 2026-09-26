cmake_minimum_required(VERSION 3.22.1)
project(handwriting)

# 导入预编译的 NCNN 动态库
add_library(ncnn SHARED IMPORTED)
set_target_properties(ncnn PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libncnn.so
)

# 编译 JNI 桥接库
add_library(handwriting SHARED handwriting_jni.cpp)

# 头文件搜索路径
target_include_directories(handwriting PRIVATE
    ${CMAKE_SOURCE_DIR}/include
    ${CMAKE_SOURCE_DIR}/include/ncnn
)

target_link_libraries(handwriting
    ncnn
    android
    log
    jnigraphics
)