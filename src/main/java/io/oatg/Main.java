package io.oatg;

import io.oatg.cli.GenerateCommand;
import io.oatg.cli.RunCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * CLI entry point. Exit codes: 0 = success, 1 = test failures, 2 = usage,
 * configuration or environment error.
 */
@Command(name = "oatg",
        version = "oatg 0.1.0",
        mixinStandardHelpOptions = true,
        description = "Schema-driven test data and request generator for OpenAPI 3.x — deterministic, AI-free.",
        subcommands = {GenerateCommand.class, RunCommand.class})
public final class Main {

    public static void main(String[] args) {
        configureLogging(args);
        System.exit(buildCommandLine().execute(args));
    }

    /** Fully configured command line — also used by integration tests. */
    public static CommandLine buildCommandLine() {
        return new CommandLine(new Main())
                .setExecutionExceptionHandler(Main::handleException);
    }

    private static int handleException(Exception ex, CommandLine cmd, CommandLine.ParseResult parseResult) {
        if (ex instanceof OatgException) {
            cmd.getErr().println("Error: " + ex.getMessage());
        } else {
            cmd.getErr().println("Unexpected error: " + ex);
            ex.printStackTrace(cmd.getErr());
        }
        return 2;
    }

    private static void configureLogging(String[] args) {
        for (String arg : args) {
            if ("-v".equals(arg) || "--verbose".equals(arg)) {
                System.setProperty("oatg.log.level", "DEBUG");
                return;
            }
        }
        if (System.getProperty("oatg.log.level") == null) {
            System.setProperty("oatg.log.level", "INFO");
        }
    }
}
