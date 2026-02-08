#!/bin/bash

# Backend startup script
cd "$(dirname "$0")/backend"
mvn spring-boot:run
