import jdk.jshell.JShell;
import jdk.jshell.Snippet;
import jdk.jshell.SnippetEvent;
import jdk.jshell.SourceCodeAnalysis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** BESHE — a dependency-free Java REPL powered by the JDK's JShell engine. */
public final class BESHELL implements AutoCloseable {
    private static final String VERSION = "1.0.0";
    private static final String RESET = "\033[0m";
    private static final String DIM = "\033[2m";
    private static final String GREEN = "\033[92m";
    private static final String RED = "\033[91m";
    private static final String CYAN = "\033[96m";
    private static final String YELLOW = "\033[93m";
    private static final String PURPLE = "\033[95m";
    private static final String HISTORY_SEPARATOR = "\n\u001e\n";

    private static final Path CONFIG_DIR = Path.of(
        System.getProperty("user.home"), ".beshell"
    );
    private static final Path HISTORY_FILE = CONFIG_DIR.resolve("history");

    private final BufferedReader input = new BufferedReader(
        new InputStreamReader(System.in, StandardCharsets.UTF_8)
    );
    private final List<String> history = new ArrayList<>();
    private final StringBuilder pending = new StringBuilder();
    private JShell shell;
    private SourceCodeAnalysis analysis;
    private boolean running = true;
    private boolean colors = System.console() != null;

    public static void main(String[] args) {
        BESHELL app = new BESHELL();
        for (String arg : args) {
            switch (arg) {
                case "--no-color" -> app.colors = false;
                case "--version", "-V" -> {
                    System.out.println("BESHE " + VERSION);
                    return;
                }
                case "--help", "-h" -> {
                    app.usage();
                    return;
                }
                default -> {
                    System.err.println("Unknown option: " + arg);
                    app.usage();
                    return;
                }
            }
        }

        try {
            Files.createDirectories(CONFIG_DIR);
            app.loadHistory();
            app.initializeShell();
            app.run();
        } catch (Exception e) {
            System.err.println("BESHE: " + e.getMessage());
            System.exit(1);
        } finally {
            app.close();
        }
    }

    private void initializeShell() {
        shell = JShell.builder().out(System.out).err(System.err).build();
        analysis = shell.sourceCodeAnalysis();

        // Useful JDK packages are available immediately. This intentionally
        // avoids importing every package, which causes ambiguous type names.
        String[] defaults = {
            "java.util.*", "java.io.*", "java.nio.*", "java.nio.file.*",
            "java.time.*", "java.time.format.*", "java.time.temporal.*",
            "java.math.*", "java.net.*", "java.net.http.*",
            "java.util.concurrent.*", "java.util.concurrent.atomic.*",
            "java.util.function.*", "java.util.regex.*", "java.util.stream.*",
            "java.security.*", "java.util.zip.*", "java.util.logging.*"
        };
        for (String item : defaults) shell.eval("import " + item + ";");
    }

    private void run() throws IOException {
        printBanner();
        while (running) {
            System.out.print(color(pending.isEmpty() ? GREEN : DIM,
                pending.isEmpty() ? "beshe > " : "       ... "));
            System.out.flush();

            String line = input.readLine();
            if (line == null) break;

            if (pending.isEmpty() && line.startsWith(":")) {
                handleCommand(line.trim());
                continue;
            }
            if (!pending.isEmpty() && line.equals(":abort")) {
                pending.setLength(0);
                println(YELLOW, "Input cancelled.");
                continue;
            }

            pending.append(line).append('\n');
            processPending();
        }
        if (!pending.isEmpty()) println(YELLOW, "Incomplete input discarded.");
    }

    private void processPending() {
        while (!pending.isEmpty()) {
            SourceCodeAnalysis.CompletionInfo info =
                analysis.analyzeCompletion(pending.toString());
            SourceCodeAnalysis.Completeness state = info.completeness();

            if (state == SourceCodeAnalysis.Completeness.DEFINITELY_INCOMPLETE ||
                state == SourceCodeAnalysis.Completeness.CONSIDERED_INCOMPLETE) {
                return;
            }

            String source = info.source();
            String remaining = info.remaining();
            pending.setLength(0);
            if (source != null && !source.isBlank()) evaluate(source);
            if (remaining != null && !remaining.isBlank()) pending.append(remaining);
            else return;
        }
    }

    private void evaluate(String source) {
        String code = source.trim();
        if (code.isEmpty()) return;
        history.add(code);
        try {
            List<SnippetEvent> events = shell.eval(code);
            for (SnippetEvent event : events) {
                Snippet snippet = event.snippet();
                if (event.status() == Snippet.Status.REJECTED) {
                    shell.diagnostics(snippet).forEach(d ->
                        println(RED, "error: " + d.getMessage(Locale.ROOT)));
                } else if (event.exception() != null) {
                    println(RED, "exception: " + event.exception());
                } else if (event.value() != null) {
                    println(GREEN, "=> " + event.value());
                }
            }
        } catch (RuntimeException e) {
            println(RED, "execution failed: " + e);
        }
    }

    private void handleCommand(String raw) {
        String[] parts = raw.split("\\s+", 2);
        String command = parts[0];
        String argument = parts.length > 1 ? parts[1].trim() : "";
        try {
            switch (command) {
                case ":help", ":h" -> help();
                case ":exit", ":quit", ":q" -> running = false;
                case ":clear", ":cls" -> clear();
                case ":reset" -> {
                    shell.close();
                    initializeShell();
                    pending.setLength(0);
                    println(GREEN, "Java session reset; history preserved.");
                }
                case ":vars" -> shell.variables().forEach(v ->
                    println(CYAN, v.typeName() + " " + v.name() + " = " + shell.varValue(v)));
                case ":methods" -> shell.methods().forEach(m ->
                    println(CYAN, m.name() + m.signature()));
                case ":types" -> shell.types().forEach(t ->
                    println(CYAN, t.kind() + " " + t.name()));
                case ":imports" -> shell.imports().forEach(i -> println(CYAN, i.name()));
                case ":history" -> showHistory();
                case ":load" -> loadFile(argument);
                case ":save" -> saveFile(argument);
                case ":cp", ":classpath" -> addClasspath(argument);
                case ":pwd" -> println(CYAN, Path.of("").toAbsolutePath().toString());
                case ":version" -> println(CYAN, "BESHE " + VERSION);
                case ":abort" -> {
                    pending.setLength(0);
                    println(YELLOW, "Input cancelled.");
                }
                default -> {
                    println(RED, "Unknown command: " + command);
                    println(DIM, "Type :help for commands.");
                }
            }
        } catch (Exception e) {
            println(RED, "command failed: " + e.getMessage());
        }
    }

    private void loadFile(String filename) throws IOException {
        if (filename.isBlank()) {
            println(YELLOW, "Usage: :load path/to/snippets.java");
            return;
        }
        Path file = Path.of(filename).toAbsolutePath();
        if (!Files.isRegularFile(file)) throw new IOException("File not found: " + file);
        String source = Files.readString(file, StandardCharsets.UTF_8);
        StringBuilder chunk = new StringBuilder();
        for (String line : source.split("\\R", -1)) {
            chunk.append(line).append('\n');
            SourceCodeAnalysis.CompletionInfo info =
                analysis.analyzeCompletion(chunk.toString());
            SourceCodeAnalysis.Completeness state = info.completeness();
            if (state == SourceCodeAnalysis.Completeness.DEFINITELY_INCOMPLETE ||
                state == SourceCodeAnalysis.Completeness.CONSIDERED_INCOMPLETE) continue;
            String ready = info.source();
            if (ready != null && !ready.isBlank()) evaluate(ready);
            chunk.setLength(0);
            if (info.remaining() != null) chunk.append(info.remaining());
        }
        if (!chunk.toString().isBlank()) evaluate(chunk.toString());
        println(GREEN, "Loaded " + file);
    }

    private void saveFile(String filename) throws IOException {
        if (filename.isBlank()) {
            println(YELLOW, "Usage: :save session.java");
            return;
        }
        Path file = Path.of(filename).toAbsolutePath();
        Files.writeString(file, String.join("\n\n", history) + "\n",
            StandardCharsets.UTF_8);
        println(GREEN, "Saved snippet history to " + file);
    }

    private void addClasspath(String value) {
        if (value.isBlank()) {
            println(YELLOW, "Usage: :cp path/to/library.jar");
            return;
        }
        Path path = Path.of(value).toAbsolutePath();
        if (!Files.exists(path)) {
            println(RED, "Path does not exist: " + path);
            return;
        }
        shell.addToClasspath(path.toString());
        println(GREEN, "Added to classpath: " + path);
    }

    private void showHistory() {
        for (int i = 0; i < history.size(); i++) {
            System.out.printf("%4d  %s%n", i + 1,
                history.get(i).replace('\n', ' '));
        }
        if (history.isEmpty()) println(DIM, "History is empty.");
    }

    private void loadHistory() throws IOException {
        if (!Files.exists(HISTORY_FILE)) return;
        String saved = Files.readString(HISTORY_FILE, StandardCharsets.UTF_8);
        if (!saved.isBlank()) {
            for (String item : saved.split(java.util.regex.Pattern.quote(HISTORY_SEPARATOR), -1)) {
                if (!item.isBlank()) history.add(item);
            }
        }
    }

    private void saveHistory() {
        try {
            Files.createDirectories(CONFIG_DIR);
            Files.writeString(HISTORY_FILE, String.join(HISTORY_SEPARATOR, history),
                StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("BESHE: could not save history: " + e.getMessage());
        }
    }

    private void printBanner() {
        System.out.println();
        println(PURPLE, "  ###########   #############  ###########   ##        ##   ############");
        println(PURPLE, "  ##        ##  ##             ##        ##  ##        ##   #");
        println(PURPLE, "  ###########   ###########    ########      ############   ##########");
        println(PURPLE, "  ##       ###  ##                   ######  ##        ##   ##");
        println(PURPLE, "  ##        ##  ##            ##         ##  ##        ##   #");
        println(PURPLE, "  ###########    ############  ###########   ##        ##   ############");
        println(DIM, "  BESHE " + VERSION + " | Java REPL | JDK " + Runtime.version().feature());
        println(DIM, "  Type :help for commands. Type :exit to leave.");
        System.out.println();
    }

    private void help() {
        System.out.println();
        println(CYAN, "COMMANDS");
        String[][] rows = {
            {":help", "Show this help"}, {":vars", "List variables"},
            {":methods", "List methods"}, {":types", "List declared types"},
            {":imports", "List imports"}, {":history", "Show persistent history"},
            {":load <file>", "Evaluate Java snippets from a file"},
            {":save <file>", "Save entered snippets as Java source"},
            {":cp <jar/path>", "Add a JAR or directory to the classpath"},
            {":reset", "Reset the Java session"}, {":clear", "Clear the terminal"},
            {":pwd", "Show current directory"}, {":abort", "Cancel multiline input"},
            {":version", "Show version"}, {":exit", "Exit BESHE"}
        };
        for (String[] row : rows) System.out.printf("  %-22s %s%n", color(CYAN, row[0]), row[1]);
        System.out.println();
        println(DIM, "Everything else is evaluated as Java code.");
        System.out.println();
    }

    private void usage() {
        System.out.println("BESHE " + VERSION + " - interactive Java shell");
        System.out.println("Usage: beshe [--help] [--version] [--no-color]");
    }

    private void clear() {
        System.out.print(colors ? "\033[2J\033[H" : "\n\n\n\n\n");
        System.out.flush();
    }

    private String color(String code, String text) {
        return colors ? code + text + RESET : text;
    }

    private void println(String code, String text) {
        System.out.println(color(code, text));
    }

    @Override
    public void close() {
        saveHistory();
        if (shell != null) shell.close();
    }
}
