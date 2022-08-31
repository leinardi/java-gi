package girparser.generator;

import girparser.model.Function;
import girparser.model.RegisteredType;
import girparser.model.Repository;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class BindingsGenerator {

    public static StringBuilder signalCallbackFunctions;

    public BindingsGenerator() {
    }

    public void generate(Repository gir) throws IOException {
        signalCallbackFunctions = new StringBuilder();
        String basePath = "src/" + gir.namespace.pathName;

        new File(basePath).mkdirs();

        for (RegisteredType rt : gir.namespace.registeredTypeMap.values()) {
            try (FileWriter writer = new FileWriter(basePath + rt.javaName + ".java")) {
                rt.generate(writer);
            }
        }
        generateSignalCallbacks(gir, basePath);
        generateGlobals(gir, basePath);
    }

    public void generateSignalCallbacks(Repository gir, String basePath) throws IOException {
        try (FileWriter writer = new FileWriter(basePath + "JVMCallbacks.java")) {
            writer.write("package " + gir.namespace.packageName + ";\n");
            writer.write("\n");
            writer.write("import jdk.incubator.foreign.*;\n");
            writer.write("import java.util.HashMap;\n");
            writer.write("import static org.gtk.interop.jextract.gtk_h.C_INT;\n");
            writer.write("\n");
            writer.write("public final class JVMCallbacks {\n");
            writer.write("    \n");
            writer.write("    public static final HashMap<Integer, Object> signalRegistry = new HashMap<>();\n");
            writer.write("    \n");
            writer.write(signalCallbackFunctions.toString());
            writer.write("}\n");
        }
    }

    public void generateGlobals(Repository gir, String basePath) throws IOException {
        String className = Conversions.toSimpleJavaType(gir.namespace.name);
        try (FileWriter writer = new FileWriter(basePath + className + ".java")) {
            writer.write("package " + gir.namespace.packageName + ";\n");
            writer.write("\n");
            RegisteredType.generateImportStatements(writer);
            writer.write("public final class " + className + " {\n");
            writer.write("    \n");

            for (Function function : gir.namespace.functionList) {
                if (function.isSafeToBind()) {
                    function.generate(writer, false, true);
                }
            }

            writer.write("}\n");
        }
    }
}
