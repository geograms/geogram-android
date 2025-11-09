#!/bin/bash

# Script to comment out all imports from old packages
# and stub out code that uses them

FILES=$(find app/src/main/java -name "*.java" -type f)

for file in $FILES; do
    # Comment out imports from old packages
    sed -i 's/^\(import offgrid\.geogram\.old\)/\/\/ Removed (legacy Google Play Services code) - \1/' "$file"
    sed -i 's/^\(import static offgrid\.geogram\.old\)/\/\/ Removed (legacy Google Play Services code) - \1/' "$file"
done

echo "Fixed old imports in all Java files"
