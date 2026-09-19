package implementation;

import interpreter.PrintScriptFormatter;
import interpreter.PrintScriptInterpreter;
import interpreter.PrintScriptLinter;

import cnc.common.Failure;
import cnc.common.Result;
import cnc.printscript.PrintScriptFacade;
import cnc.printscript.PsVersioner;
import kotlin.Unit;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class CustomImplementationFactory implements PrintScriptFactory {

    @Override
    public PrintScriptInterpreter interpreter() {
        return (src, version, emitter, handler, provider) -> {
            try {
                PrintScriptFacade printScript = PsVersioner.INSTANCE.version(version);
                Result<Unit> result = printScript.execute(
                        src,
                        provider::input,
                        System::getenv,
                        msg -> {
                            emitter.print(msg);
                            return Unit.INSTANCE;
                        }
                );
                if (result instanceof Failure) {
                    handler.reportError(((Failure<Unit>) result).getMsg());
                }
            } catch (Throwable t) {
                handler.reportError(t.getMessage() != null ? t.getMessage() : t.toString());
            }
        };
    }

    @Override
    public PrintScriptFormatter formatter() {
        return (src, version, config, writer) -> {
            try {
                PrintScriptFacade printScript = PsVersioner.INSTANCE.version(version);
                String formatted = printScript.format(src);
                writer.write(formatted);
            } catch (Throwable t) {
                // handle error
            }
        };
    }

    @Override
    public PrintScriptLinter linter() {
        return (src, version, config, handler) -> {
            try {
                PrintScriptFacade printScript = PsVersioner.INSTANCE.version(version);
                String configJson = new String(config.readAllBytes(), StandardCharsets.UTF_8);
                List<String> warnings = printScript.lint(src, configJson);
                for (String warning : warnings) {
                    handler.reportError(warning);
                }
            } catch (Throwable t) {
                handler.reportError(t.getMessage() != null ? t.getMessage() : t.toString());
            }
        };
    }
}