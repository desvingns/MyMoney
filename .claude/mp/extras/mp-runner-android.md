# mp-runner-android - MyMoney deterministic runner preference

This role should be a deterministic execution step whenever possible.

- Prefer the plugin script `scripts/mp-runner-android.sh` when Bash is available.
- If the plugin script is unavailable, run the smallest explicit Gradle commands from the context
  capsule and return JSON with pass/fail, commands, and errors.
- Do not read monthly progress archives.
- Do not edit source files.
- Connected tests are not this role. Use `mp-runner-instrumented-android` or
  `scripts/mp-runner-instrumented-android.ps1` for a single device class.

