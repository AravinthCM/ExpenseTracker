#!/bin/bash

echo "🚀 ExpenseTracker auto-install started"
echo "Watching app/src/main for changes..."
echo

./gradlew installDebug

while inotifywait -r -e modify,create,delete,move app/src/main; do
    echo
    echo "🔄 Change detected — building and installing..."
    ./gradlew installDebug

    if [ $? -eq 0 ]; then
        echo "✅ Installed successfully"
    else
        echo "❌ Build/install failed"
    fi

    echo
done
