#!/bin/bash

# Frontend startup script
# Note: Backend must be running first!
cd "$(dirname "$0")/frontend"
./mvnw compile exec:java
