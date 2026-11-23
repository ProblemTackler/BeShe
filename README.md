# BESHE

A small, dependency-free Java REPL CLI powered by the JDK's JShell engine. Designed for macOS (zsh) and also works on Linux and other Unix-like systems.

## Requirements

- JDK 17 or newer (a JRE alone is not enough).
- macOS, Linux, or another Unix-like environment with a POSIX shell.
- No `sudo`, administrator account, Homebrew, Maven, Gradle, or third-party Java libraries required.

## Install from a GitHub checkout or ZIP

```sh
unzip beshe.zip
cd beshell
./install.sh
```

Or clone your GitHub repository and run the same installer:

```sh
git clone https://github.com/YOUR-USERNAME/beshell.git
cd beshell
./install.sh
```

The installer compiles BESHE into `~/.local/share/beshe` and puts the `beshe` launcher in `~/.local/bin`. It adds that directory to `~/.zprofile` and `~/.bash_profile` if needed. This is entirely inside your home directory and does not need administrator privileges.

Open a new terminal, or activate the path in the current terminal:

```sh
export PATH="$HOME/.local/bin:$PATH"
```

Then run:

```sh
beshe
```

## Commands

| Command | Description |
|---|---|
| `:help` | Show commands |
| `:vars` | List variables |
| `:methods` | List methods |
| `:types` | List declared types |
| `:imports` | List imports |
| `:history` | Show saved history |
| `:load file.java` | Evaluate snippets from a Java source file |
| `:save file.java` | Save entered snippets as Java source |
| `:cp library.jar` | Add a JAR or directory to the session classpath |
| `:reset` | Reset the Java session |
| `:clear` | Clear the terminal |
| `:pwd` | Show current directory |
| `:abort` | Cancel multiline input |
| `:version` | Show version |
| `:exit` | Exit |

Useful imports from `java.util`, `java.io`, `java.nio`, `java.time`, `java.net`, concurrency, streams, regex, security, and logging are preloaded. Java's default `java.lang` imports are available automatically.

## CLI options

```sh
beshe --help
beshe --version
beshe --no-color
```

## Uninstall

From this repository directory, run:

```sh
./uninstall.sh
```

This removes the executable and compiled application from `~/.local`. It leaves your history file at `~/.beshell/history` untouched.

## Notes

- BESHE is an interactive Java shell, not a replacement for compiling complete multi-file Java projects. The `:load` command is intended for JShell-compatible snippets.
- Importing every Java package is not valid or desirable: it can create ambiguous type names. BESHE preloads a useful set of commonly used packages instead.
- Third-party libraries are not bundled. Add a local JAR with `:cp /path/to/library.jar`.
