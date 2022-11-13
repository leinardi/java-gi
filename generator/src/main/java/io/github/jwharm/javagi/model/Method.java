package io.github.jwharm.javagi.model;

import java.io.IOException;
import java.io.Writer;

import io.github.jwharm.javagi.generator.Conversions;

public class Method extends GirElement implements CallableType {

    public final String cIdentifier, deprecated, throws_;
    public ReturnValue returnValue;
    public Parameters parameters;

    public Method(GirElement parent, String name, String cIdentifier, String deprecated, String throws_) {
        super(parent);
        this.name = name;
        this.cIdentifier = cIdentifier;
        this.deprecated = deprecated;
        this.throws_ = throws_;

        // Handle empty names. (For example, GLib.g_iconv is named "".)
        if ("".equals(name)) {
            this.name = cIdentifier;
        }
    }
    
    public void generateMethodHandle(Writer writer, boolean isInterface) throws IOException {
        boolean varargs = false;
        writer.write("        \n");
        writer.write("        ");
        writer.write(isInterface ? "@ApiStatus.Internal\n        " : "private ");
        writer.write("static final MethodHandle " + cIdentifier + " = Interop.downcallHandle(\n");
        writer.write("            \"" + cIdentifier + "\",\n");
        writer.write("            FunctionDescriptor.");
        if (returnValue.type == null || "void".equals(returnValue.type.simpleJavaType)) {
            writer.write("ofVoid(");
        } else {
            writer.write("of(" + Conversions.toPanamaMemoryLayout(returnValue.type));
            if (parameters != null) {
                writer.write(", ");
            }
        }
        if (parameters != null) {
            for (int i = 0; i < parameters.parameterList.size(); i++) {
                if (parameters.parameterList.get(i).varargs) {
                    varargs = true;
                    break;
                }
                if (i > 0) {
                    writer.write(", ");
                }
                writer.write(Conversions.toPanamaMemoryLayout(parameters.parameterList.get(i).type));
            }
        }
        if (throws_ != null) {
            writer.write(", ValueLayout.ADDRESS");
        }
        writer.write("),\n");
        writer.write(varargs ? "            true\n" : "            false\n");
        writer.write("        );\n");
    }

    public void generate(Writer writer, boolean isInterface, boolean isStatic) throws IOException {
        writer.write("    \n");
        
        // Documentation
        if (doc != null) {
            doc.generate(writer, 1);
        }

        // Deprecation
        if ("1".equals(deprecated)) {
            writer.write("    @Deprecated\n");
        }

        if (isInterface && !isStatic) {
            // Default interface methods
            writer.write("    default ");
        } else {
            // Visibility
            writer.write("    public ");
        }

        // Static methods (functions)
        if (isStatic) {
            writer.write("static ");
        }

        // Annotations
        if ((getReturnValue().type != null && !getReturnValue().type.isPrimitive && !getReturnValue().type.isVoid())
                || getReturnValue().array != null) {
            writer.write(getReturnValue().nullable ? "@Nullable " : "@NotNull ");
        }

        // Return type
        writer.write(getReturnValue().getReturnType());

        // Method name
        String methodName = Conversions.toLowerCaseJavaName(name);
        if (isInterface) { // Overriding toString() in a default method is not allowed.
            methodName = Conversions.replaceJavaObjectMethodNames(methodName);
        }
        writer.write(" ");
        writer.write(methodName);

        // Parameters
        if (getParameters() != null) {
            writer.write("(");
            getParameters().generateJavaParameters(writer, false);
            writer.write(")");
        } else {
            writer.write("()");
        }

        // Exceptions
        if (throws_ != null) {
            writer.write(" throws io.github.jwharm.javagi.GErrorException");
        }
        writer.write(" {\n");
        
        // Currently unsupported method: throw an exception
        if (! isSafeToBind()) {
            writer.write("        throw new UnsupportedOperationException(\"Operation not supported yet\");\n");
            writer.write("    }\n");
            return;
        }
        
        // Generate preprocessing statements for all parameters
        if (parameters != null) {
            parameters.generatePreprocessing(writer);
        }

        // Allocate GError pointer
        if (throws_ != null) {
            writer.write("        MemorySegment GERROR = Interop.getAllocator().allocate(ValueLayout.ADDRESS);\n");
        }
        
        // Variable declaration for return value
        String panamaReturnType = Conversions.toPanamaJavaType(getReturnValue().type);
        if (! (returnValue.type != null && returnValue.type.isVoid())) {
            writer.write("        " + panamaReturnType + " RESULT;\n");
        }
        
        // The method call is wrapped in a try-catch block
        writer.write("        try {\n");
        writer.write("            ");
        
        // Generate the return type
        if (! (returnValue.type != null && returnValue.type.isVoid())) {
            writer.write("RESULT = (");
            writer.write(panamaReturnType);
            writer.write(") ");
        }

        // Invoke to the method handle
        writer.write("DowncallHandles." + cIdentifier + ".invokeExact");
        
        // Marshall the parameters to the native types
        if (parameters != null) {
            writer.write("(");
            parameters.generateCParameters(writer, throws_);
            writer.write(")");
        } else {
            writer.write("()");
        }
        writer.write(";\n");
        
        // If something goes wrong in the invokeExact() call
        writer.write("        } catch (Throwable ERR) {\n");
        writer.write("            throw new AssertionError(\"Unexpected exception occured: \", ERR);\n");
        writer.write("        }\n");

        // Throw GErrorException
        if (throws_ != null) {
            writer.write("        if (GErrorException.isErrorSet(GERROR)) {\n");
            writer.write("            throw new GErrorException(GERROR);\n");
            writer.write("        }\n");
        }
        
        // Generate post-processing actions for parameters
        if (parameters != null) {
            parameters.generatePostprocessing(writer);
        }
        
        // If the return value is an array, try to convert it to a Java array
        if (returnValue.array != null) {
            String len = returnValue.array.size();
            if (len != null) {
                if (getReturnValue().nullable) {
                    switch (panamaReturnType) {
                        case "MemoryAddress" -> writer.write("        if (RESULT.equals(MemoryAddress.NULL)) return null;\n");
                        case "MemorySegment" -> writer.write("        if (RESULT.address().equals(MemoryAddress.NULL)) return null;\n");
                        default -> System.err.println("Unexpected nullable return type: " + panamaReturnType);
                    }
                }
                String valuelayout = Conversions.getValueLayout(returnValue.array.type);
                if (returnValue.array.type.isPrimitive && (! returnValue.array.type.isBoolean())) {
                    // Array of primitive values
                    writer.write("        return MemorySegment.ofAddress(RESULT.get(ValueLayout.ADDRESS, 0), " + len + " * " + valuelayout + ".byteSize(), Interop.getScope()).toArray(" + valuelayout + ");\n");
                } else {
                    // Array of proxy objects
                    writer.write("        " + returnValue.array.type.qualifiedJavaType + "[] resultARRAY = new " + returnValue.array.type.qualifiedJavaType + "[" + len + "];\n");
                    writer.write("        for (int I = 0; I < " + len + "; I++) {\n");
                    writer.write("            var OBJ = RESULT.get(" + valuelayout + ", I);\n");
                    writer.write("            resultARRAY[I] = " + returnValue.getNewInstanceString(returnValue.array.type, "OBJ", false) + ";\n");
                    writer.write("        }\n");
                    writer.write("        return resultARRAY;\n");
                }
            } else {
                returnValue.generateReturnStatement(writer, 2);
            }
        } else {
            returnValue.generateReturnStatement(writer, 2);
        }
        
        writer.write("    }\n");
    }

    @Override
    public Parameters getParameters() {
        return parameters;
    }

    @Override
    public void setParameters(Parameters ps) {
        this.parameters = ps;
    }

    @Override
    public ReturnValue getReturnValue() {
        return returnValue;
    }

    @Override
    public void setReturnValue(ReturnValue rv) {
        this.returnValue = rv;
    }
}
