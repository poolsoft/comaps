project(feature_helpers)

set(SRC
  feature_charge_sockets.cpp
  feature_charge_sockets.hpp
)

omim_add_library(${PROJECT_NAME} ${SRC})
target_link_libraries(${PROJECT_NAME}
  PRIVATE
    base
)

# omim_add_test_subdirectory(feature_helpers_tests)
