project(feature_helpers_tests)

set(SRC
  feature_charge_sockets_test.cpp
)

omim_add_test(${PROJECT_NAME} ${SRC})

target_link_libraries(${PROJECT_NAME}
  feature_helpers
)
