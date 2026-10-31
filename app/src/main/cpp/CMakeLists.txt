cmake_minimum_required(VERSION 3.22.1)
project(smartllama C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

if(ANDROID_ABI STREQUAL "arm64-v8a")
  # Always optimise: a Debug (-O0) llama.cpp is unusably slow.
  set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
  set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
  set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
  set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
  set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
  set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
  set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
  set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
  set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
  set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
  set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod" CACHE STRING "" FORCE)   # Kotlin refuses to load the library on CPUs without dotprod
  add_subdirectory(llama.cpp ${CMAKE_BINARY_DIR}/llama.cpp)
  add_library(smartllama SHARED smartllama.cpp)
  target_include_directories(smartllama PRIVATE llama.cpp/include llama.cpp/ggml/include)
  target_link_libraries(smartllama PRIVATE llama ggml log android)
  target_link_options(smartllama PRIVATE -Wl,-z,max-page-size=16384)   # Android 15+ 16 KB pages
else()
  add_library(smartllama SHARED stub.cpp)   # no local LLM on 32-bit ABIs; Kotlin checks the ABI before using it
endif()
