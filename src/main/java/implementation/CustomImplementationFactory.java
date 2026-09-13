package implementation;

import interpreter.PrintScriptFormatter;
import interpreter.PrintScriptInterpreter;
import interpreter.PrintScriptLinter;

import cnc.ast.Statement;
import cnc.common.*;
import cnc.config.*;
import cnc.interpreter.*;
import cnc.lexer.Lexer;
import cnc.linter.*;
import cnc.parser.Parser;
import cnc.semantic.*;
import cnc.token.Token;
import kotlin.Unit;
import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CustomImplementationFactory implements PrintScriptFactory {

    @Override
    public PrintScriptInterpreter interpreter() {
        return (src, version, emitter, handler, provider) -> {
            try {
                ContentManager content = () -> new InputStreamReader(src, StandardCharsets.UTF_8);
                CharCursor cursor = ContentManagerKt.openStream(content, 8192);

                Lexer lexer = LexerConfigKt.getPrintScriptLexer();
                Sequence<Token> tokens = lexer.tokenize(cursor);

                Parser parser = ParserConfigKt.getPrintScriptParser();
                SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer(SemanticConfigKt.getSemanticContext());

                Interpreter interpreter = InterpreterPresets.INSTANCE.builder()
                        .withOutput(msg -> {
                            emitter.print(msg);
                            return Unit.INSTANCE;
                        })
                        .registerBuiltin(BuiltinMethodFactory.INSTANCE.readInput(prompt -> provider.input(prompt)))
                        .registerBuiltin(BuiltinMethodFactory.INSTANCE.readEnv(name -> System.getenv(name)))
                        .build();

                Environment environment = new Environment(null);

                Iterator<Result<Statement>> parseIt = parser.parse(tokens).iterator();
                while (parseIt.hasNext()) {
                    Result<Statement> parseRes = parseIt.next();
                    if (parseRes instanceof Failure) {
                        handler.reportError(((Failure<Statement>) parseRes).getMsg());
                        return;
                    }
                    Statement stmt = ((Success<Statement>) parseRes).getData();

                    Sequence<Statement> singleSeq = SequencesKt.sequenceOf(stmt);
                    Iterator<Result<Statement>> semIt = semanticAnalyzer.analyze(singleSeq).iterator();
                    if (semIt.hasNext()) {
                        Result<Statement> semRes = semIt.next();
                        if (semRes instanceof Failure) {
                            handler.reportError(((Failure<Statement>) semRes).getMsg());
                            return;
                        }
                    }

                    Result<Unit> execRes = interpreter.interpret(stmt, environment);
                    if (execRes instanceof Failure) {
                        handler.reportError(((Failure<Unit>) execRes).getMsg());
                        return;
                    }
                }
            } catch (Exception e) {
                handler.reportError(e.getMessage() != null ? e.getMessage() : e.toString());
            }
        };
    }

    @Override
    public PrintScriptFormatter formatter() {
        return (src, version, config, writer) -> {
            throw new NotImplementedException("Formatter not implemented yet");
        };
    }

    @Override
    public PrintScriptLinter linter() {
        return (src, version, config, handler) -> {
            try {
                String jsonConfig = new String(config.readAllBytes(), StandardCharsets.UTF_8);
                List<LinterRule> rules = new ArrayList<>();

                String lower = jsonConfig.toLowerCase();
                if (lower.contains("camel")) {
                    rules.add(new NamingConventionRule("camelCase", new CamelCaseValidator()));
                } else if (lower.contains("snake")) {
                    rules.add(new NamingConventionRule("snake_case", new SnakeCaseValidator()));
                }

                if (lower.contains("println") && lower.contains("true")) {
                    rules.add(new SimplePrintlnRule());
                }

                CNCLinter cncLinter = new CNCLinter(rules);

                ContentManager content = () -> new InputStreamReader(src, StandardCharsets.UTF_8);
                CharCursor cursor = ContentManagerKt.openStream(content, 8192);

                Lexer lexer = LexerConfigKt.getPrintScriptLexer();
                Sequence<Token> tokens = lexer.tokenize(cursor);

                Parser parser = ParserConfigKt.getPrintScriptParser();
                Iterator<Result<Statement>> parseIt = parser.parse(tokens).iterator();

                while (parseIt.hasNext()) {
                    Result<Statement> res = parseIt.next();
                    if (res instanceof Failure) {
                        handler.reportError(((Failure<Statement>) res).getMsg());
                        return;
                    } else if (res instanceof Success) {
                        Statement stmt = ((Success<Statement>) res).getData();
                        List<String> warnings = cncLinter.lint(SequencesKt.sequenceOf(stmt));
                        for (String warning : warnings) {
                            handler.reportError(warning);
                        }
                    }
                }
            } catch (Exception e) {
                handler.reportError(e.getMessage() != null ? e.getMessage() : e.toString());
            }
        };
    }
}