#!/bin/bash

# Backend startup script
cd "$(dirname "$0")/backend"
./mvnw spring-boot:run
