find_package(Java REQUIRED)
include(UseJava)

if (NOT DEFINED $ENV{JAVA_HOME_NATIVE})
  set (JAVA_HOME_NATIVE $ENV{JAVA_HOME})
  set (JAVAC $ENV{JAVA_HOME}/bin/javac)
  set (JAR $ENV{JAVA_HOME}/bin/jar)
else ()
  set (JAVAC $ENV{JAVA_HOME_NATIVE}/bin/javac)
  set (JAR $ENV{JAVA_HOME_NATIVE}/bin/jar)
endif ()



set(CMAKE_JNI_TARGET TRUE)
file(GLOB JAVA_SOURCES "*.java")
add_jar(tinybjar ${JAVA_SOURCES}
                  MANIFEST ${CMAKE_CURRENT_BINARY_DIR}/manifest.txt
                  OUTPUT_NAME tinyb
)

set(JAVA_CLASSES ../../../../java/BluetoothAdapter.java
                 ../../../../java/BluetoothCallback.java
                 ../../../../java/BluetoothDevice.java
                 ../../../../java/BluetoothEvent.java
                 ../../../../java/BluetoothGattCharacteristic.java
                 ../../../../java/BluetoothGattService.java
                 ../../../../java/BluetoothGattDescriptor.java
                 ../../../../java/BluetoothManager.java
                 ../../../../java/BluetoothObject.java
                 ../../../../java/BluetoothType.java
                 ../../../../java/BluetoothException.java
                 ../../../../java/ObjectArgCallback.java
                 ../../../../java/ObjectArrayArgCallback.java)
add_custom_command (TARGET tinybjar
  POST_BUILD
  COMMAND ${CMAKE_COMMAND} -E echo "Generating JNI headers.."
  WORKING_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/${CMAKE_FILES_DIRECTORY}/tinybjar.dir"
  COMMAND ${Java_JAVAC_EXECUTABLE} -h jni/ ${JAVA_CLASSES}
)

set(JNI_HEADER_PATH "${CMAKE_CURRENT_BINARY_DIR}/${CMAKE_FILES_DIRECTORY}/tinybjar.dir/jni")
install (FILES ${CMAKE_CURRENT_BINARY_DIR}/tinyb.jar DESTINATION ${CMAKE_INSTALL_LIBDIR}/../lib/java)

add_subdirectory (jni)
