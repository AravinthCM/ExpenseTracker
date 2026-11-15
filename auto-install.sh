#!/bin/bash

TASK="${1:-installDebug}"
WATCH_PATH="app/src/main"

echo "🚀 ExpenseTracker auto-install started"
echo "Task: $TASK"
echo "Watching $WATCH_PATH for changes..."
echo

run_task() {
    log_file=$(mktemp)
    ./gradlew "$TASK" 2>&1 | tee "$log_file"
    status=${PIPESTATUS[0]}

    if [ $status -eq 0 ]; then
        echo "✅ $TASK successful"
        rm -f "$log_file"
        return
    fi

    if grep -q "Gradle build daemon has been stopped: stop command received" "$log_file"; then
        echo "↻ Daemon stopped externally. Retrying once with --no-daemon..."
        ./gradlew --no-daemon "$TASK"
        if [ $? -eq 0 ]; then
            echo "✅ $TASK successful (retry)"
            rm -f "$log_file"
            return
        fi
    fi

    rm -f "$log_file"
    echo "❌ $TASK failed"
}

run_task

while inotifywait -r -e modify,create,delete,move "$WATCH_PATH"; do
    echo
    echo "🔄 Change detected — running $TASK..."
    run_task
    echo
done
