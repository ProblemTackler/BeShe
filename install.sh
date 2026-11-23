#!/bin/sh
# Install BESHE into the current user's home directory. No sudo/admin required.
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
APP_HOME="$HOME/.local/share/beshe"
BIN_DIR="$HOME/.local/bin"
CLASSES="$APP_HOME/classes"

find_jdk() {
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
    printf '%s\n' "$JAVA_HOME"
    return
  fi
  if [ "$(uname -s)" = "Darwin" ] && [ -x /usr/libexec/java_home ]; then
    DETECTED="$(/usr/libexec/java_home -v 17+ 2>/dev/null || true)"
    if [ -n "$DETECTED" ] && [ -x "$DETECTED/bin/javac" ]; then
      printf '%s\n' "$DETECTED"
      return
    fi
  fi
  JAVAC="$(command -v javac || true)"
  if [ -n "$JAVAC" ]; then
    dirname "$(dirname "$JAVAC")"
    return
  fi
  return 1
}

if ! JDK_HOME="$(find_jdk)"; then
  echo "BESHE needs a JDK 17 or newer (not just a JRE)." >&2
  echo "Install a JDK, then run this installer again." >&2
  echo "macOS: https://adoptium.net/temurin/releases/" >&2
  exit 1
fi

"$JDK_HOME/bin/javac" -version >/dev/null 2>&1 || {
  echo "Could not run javac from $JDK_HOME" >&2
  exit 1
}

mkdir -p "$CLASSES" "$BIN_DIR"
rm -rf "$CLASSES"/*
"$JDK_HOME/bin/javac" --add-modules jdk.jshell -encoding UTF-8 \
  -d "$CLASSES" "$ROOT/src/BESHELL.java"

cp "$ROOT/bin/beshe" "$BIN_DIR/beshe"
chmod 755 "$BIN_DIR/beshe"

# Add the user-local executable directory to common macOS/Linux shell profiles.
PATH_LINE='export PATH="$HOME/.local/bin:$PATH"'
add_path_line() {
  PROFILE="$1"
  if [ -f "$PROFILE" ] && grep -Fq '.local/bin' "$PROFILE"; then
    return
  fi
  printf '\n# BESHE user-local CLI\n%s\n' "$PATH_LINE" >> "$PROFILE"
}
add_path_line "$HOME/.zprofile"
add_path_line "$HOME/.bash_profile"

printf '\nBESHE installed successfully.\n'
printf 'Executable: %s\n' "$BIN_DIR/beshe"
printf '\nOpen a new terminal, or run:\n  export PATH="$HOME/.local/bin:$PATH"\n\nThen start it with:\n  beshe\n'
