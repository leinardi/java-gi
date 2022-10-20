package io.github.jwharm.javagi.generator;

import io.github.jwharm.javagi.model.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public class BindingsGenerator {

    public static StringBuilder signalCallbackFunctions;

    public BindingsGenerator() {
    }

    public void generate(Repository gir, Set<String> natives, Path basePath) throws IOException {
        signalCallbackFunctions = new StringBuilder();

        Files.createDirectories(basePath);

        for (RegisteredType rt : gir.namespace.registeredTypeMap.values()) {
            
            if (rt instanceof io.github.jwharm.javagi.model.Record rec
                    && rec.isEmpty()) {
                continue;
            }
            // No support for callbacks with out parameters or arrays for now
            if (rt instanceof io.github.jwharm.javagi.model.Callback cb
                    && cb.parameters != null
                    && cb.parameters.parameterList.stream().anyMatch(Parameter::isOutParameter)) {
                continue;
            }
            if (rt instanceof io.github.jwharm.javagi.model.Callback cb
                    && cb.parameters != null
                    && cb.parameters.parameterList.stream().anyMatch(p -> p.array != null)) {
                continue;
            }

            try (Writer writer = Files.newBufferedWriter(basePath.resolve(rt.javaName + ".java"))) {
                rt.generate(writer);
            }
        }
        generateGlobals(gir, natives, basePath);
    }

    public void generateGlobals(Repository gir, Set<String> natives, Path basePath) throws IOException {
        String className = Conversions.toSimpleJavaType(gir.namespace.name);
        try (Writer writer = Files.newBufferedWriter(basePath.resolve(className + ".java"))) {
            writer.write("package " + gir.namespace.packageName + ";\n");
            writer.write("\n");
            RegisteredType.generateImportStatements(writer);
            writer.write("public final class " + className + " {\n");
            writer.write("    \n");
            if (!natives.isEmpty()) {
                writer.write("    static {\n");
                for (String libraryName : natives) {
                    writer.write("        System.loadLibrary(\"" + libraryName + "\");\n");
                }
                writer.write("    }\n");
                writer.write("    \n");
            }
            writer.write("    @ApiStatus.Internal static void javagi$ensureInitialized() {}\n");
            writer.write("    \n");

            for (Constant constant : gir.namespace.constantList) {
                constant.generate(writer);
            }

            for (Function function : gir.namespace.functionList) {
                if (function.isSafeToBind()) {
                    function.generate(writer, function.parent instanceof Interface, true);
                }
            }
            
            writer.write(signalCallbackFunctions.toString());

            writer.write("}\n");
        }
    }
}
