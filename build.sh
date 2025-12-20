#!/bin/bash

# Build script for NekoPlugin

# Check if Gradle is installed
if ! command -v gradle &> /dev/null; then
    echo "Error: Gradle is not installed or not in your PATH."
    echo "Please install Gradle to build this plugin."
    exit 1
fi

echo "Building NekoPlugin with Gradle..."
gradle clean build

if [ $? -eq 0 ]; then
    echo "Build successful! The plugin jar is located in the build/libs directory."
else
    echo "Build failed."
    exit 1
fi
