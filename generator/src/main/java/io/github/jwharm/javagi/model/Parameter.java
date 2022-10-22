package io.github.jwharm.javagi.model;

import java.io.IOException;
import java.io.Writer;

import io.github.jwharm.javagi.generator.Conversions;

public class Parameter extends GirElement {

    public final String transferOwnership, allowNone, direction;
    public final boolean nullable;

    public boolean varargs = false;

    public Parameter(GirElement parent, String name, String transferOwnership, String nullable, String allowNone, String direction) {
        super(parent);
        this.name = Conversions.toLowerCaseJavaName(name);
        this.transferOwnership = transferOwnership;
        this.nullable = "1".equals(nullable);
        this.allowNone = allowNone;
        this.direction = direction;
    }

    /**
     * Return the parameter that is referred to by index in a GIR attribute.
     * For example: array length="2" refers to the second parameter for the 
     * length of that particular array.
     * The index counts from zero and ignores the instance-parameter.
     */
    public Parameter getParameterAt(String indexAttr) {
    	if (indexAttr == null) {
    		return null;
    	}
    	// Parse the value of the 'length' attribute
    	int index;
    	try {
        	index = Integer.valueOf(indexAttr);
    	} catch (NumberFormatException nfe) {
    		return null;
    	}
    	// Get the <parameters> node
    	Parameters params;
    	if (this instanceof ReturnValue) {
    		params = ((CallableType) parent).getParameters();
    	} else {
    		params = (Parameters) parent;
    	}
    	// Find the parameter that was specified in the value of the 'length' attribute.
    	// Ignore the instance parameter.
    	if (params.parameterList.get(0) instanceof InstanceParameter) {
    		return params.parameterList.get(index + 1);
    	} else {
    		return params.parameterList.get(index);
    	}
    }
    
    public boolean transferOwnership() {
        return "full".equals(transferOwnership);
    }
    
    public boolean isOutParameter() {
    	return direction != null && direction.contains("out");
    }

    public boolean isInstanceParameter() {
        return (this instanceof InstanceParameter);
    }

    public boolean isCallbackParameter() {
        return (type != null) && type.isCallback();
    }

    public boolean isUserDataParameter() {
        return (type != null) && name.toLowerCase().endsWith("data")
                && ("gpointer".equals(type.cType) || "gconstpointer".equals(type.cType));
    }

    public boolean isDestroyNotify() {
        return isCallbackParameter() && "DestroyNotify".equals(type.simpleJavaType);
    }

    public boolean isErrorParameter() {
        return (type != null) && "GError**".equals(type.cType);
    }

    public void generateTypeAndName(Writer writer, boolean pointerForArray) throws IOException {
        // Annotations
        writer.write(nullable ? "@Nullable " : "@NotNull ");
        
        // Arrays
        if (array != null) {
            // Out parameters
            if (isOutParameter()) {
            	writer.write("Out<" + array.type.qualifiedJavaType + "[]>");
            } else if (pointerForArray) {
                writer.write(getPointerReturnType(array.type, null));
            } else {
                writer.write(array.type.qualifiedJavaType + "[]");
            }
        
        // Out parameters
        } else if (isOutParameter()) {
        	String typeStr = pointerForArray ? getReturnType() : type.qualifiedJavaType;
        	if (type.isPrimitive) {
        		typeStr = Conversions.primitiveClassName(type.simpleJavaType);
        	}
    		writer.write("Out<" + typeStr + ">");
        
        // Also arrays
        } else if (type.cType != null && type.cType.endsWith("**")) {
            writer.write(getPointerReturnType(type, null));
        
        // Pointer to primitive type
        } else if (type.isPrimitive && type.isPointer()) {
            writer.write("Pointer" + Conversions.primitiveClassName(type.simpleJavaType));
        
        // Everything else
        } else {
            writer.write(type.qualifiedJavaType);
        }
        writer.write(" " + name);
    }

    public void generateInterop(Writer writer, String identifier, boolean checkForOutParameter) throws IOException {
        // Arrays
        if (array != null) {
            generateArrayInterop(writer, identifier, array.type);
        
        // This should not happen
        } else if (type == null) {
            writer.write(identifier);
        
        // Out parameters
        } else if (checkForOutParameter && isOutParameter()) {
        	writer.write("(Addressable) " + identifier + "POINTER.address()");
        
        // Pointers
        } else if (type.cType != null && type.cType.endsWith("**")) {
            writer.write(identifier + ".handle()");
        
        // Strings: allocate utf8 string
        } else if (type.qualifiedJavaType.equals("java.lang.String")) {
            writer.write("Interop.allocateNativeString(" + identifier + ")");
        
        // Pointer to primitive type: get memory address
        } else if (type.isPrimitive && type.isPointer()) {
            writer.write(identifier + ".handle()");
        
        // Convert boolean to int
        } else if (type.isBoolean()) {
            writer.write(identifier + " ? 1 : 0");
        
        // Objects and ValueWrappers
        } else if (type.girElementInstance != null) {
            writer.write(type.girElementInstance.getInteropString(identifier, type.isPointer(), transferOwnership()));
        
        // Primitive types
        } else {
            writer.write(identifier);
        }
    }
    
    private void generateArrayInterop(Writer writer, String identifier, Type type) throws IOException {
        // This should not happen
        if (type == null) {
            writer.write("MemoryAddress.NULL");
        
        } else if (isOutParameter()) {
            writer.write("(Addressable) " + identifier + "POINTER.address()");

        // Convert array of ValueWrapper types to an array of the wrapped values
        } else if (type.isEnum() || type.isBitfield() || type.isAliasForPrimitive()) {
            String typename = "";
            if (type.isAliasForPrimitive()) {
                typename = Conversions.primitiveClassName(type.girElementInstance.type.qualifiedJavaType);
            }
            writer.write("Interop.allocateNativeArray(" + type.qualifiedJavaType + ".get" + typename + "Values(" + identifier + "))");

        // Automatically use the right allocateNativeArray() method for this type
        } else {
            writer.write("Interop.allocateNativeArray(" + identifier + ")");
        }
    }
    
    public String getNewInstanceString(Type type, String identifier, boolean generatePointerProxy) {
        // This should not happen
    	if (type == null) {
    		return identifier;
    	}
    	// Create Pointer to an object
    	if (array == null && type.cType != null && type.cType.endsWith("**") && generatePointerProxy) {
    		return "new PointerProxy<" + type.qualifiedJavaType + ">(" + identifier + ", " + type.qualifiedJavaType + ".class)";
    	}
        // Create Pointer to primitive value
    	if (type.isPrimitive && type.isPointer()) {
        	return "new Pointer" + Conversions.primitiveClassName(type.simpleJavaType) + "(" + identifier + ")";
    	}
        // Create Java String from UTF8 memorysegment
    	if (type.qualifiedJavaType.equals("java.lang.String")) {
    		return identifier + ".getUtf8String(0)";
    	}
        // Create ValueWrapper object
    	if (type.isBitfield() || type.isEnum() || type.isAliasForPrimitive()) {
    		return "new " + type.qualifiedJavaType + "(" + identifier + ")";
    	}
        // I don't think this situation exists
    	if (type.isCallback()) {
    		return "null /* Unsupported parameter type */";
    	}
        // Convert int back to boolean
    	if (type.isBoolean()) {
    		return identifier + " != 0";
    	}
        // Create an Impl object when we only know the interface but not the class
    	if (type.isInterface()) {
    		return "new " + type.qualifiedJavaType + "." + type.simpleJavaType + "Impl(Refcounted.get(" + identifier + ", " + (transferOwnership() ? "true" : "false") + "))";
    	}
        // Objects
    	if (type.isClass() || type.isAlias() || type.isUnion()) {
    		return "new " + type.qualifiedJavaType + "(Refcounted.get(" + identifier + ", " + (transferOwnership() ? "true" : "false") + "))";
    	}
        // Primitive values remain as-is
    	return identifier;
    }

    public void generateReverseInterop(Writer writer, String identifier, boolean pointerForArrays) throws IOException {
        if (array != null) {
            generateReverseArrayInterop(writer, identifier, pointerForArrays);
        } else {
        	writer.write(getNewInstanceString(type, identifier, true));
        }
    }
    
    public void generateReverseArrayInterop(Writer writer, String identifier, boolean pointerForArrays) throws IOException {
    	Type type = array != null ? array.type : this.type;
    	String len = null;
    	if (array != null) {
    		len = array.size();
    	}
    	
        // Array of arrays - this is not supported yet
        if (array != null && array.array != null) {
            writer.write("");
        
        // This should not happen
        } else if (type == null) {
            writer.write("null");
        
        } else if ((! pointerForArrays) && len != null) {
			String valuelayout = Conversions.getValueLayout(type);
			writer.write("MemorySegment.ofAddress(" + identifier + ".get(ValueLayout.ADDRESS, 0), " + len + " * " + valuelayout + ".byteSize(), Interop.getScope()).toArray(" + valuelayout + ")");
        
        // Pointer to enumeration
        } else if (type.isEnum()) {
            writer.write("new PointerEnumeration<" + type.qualifiedJavaType + ">(" + identifier + ", " + type.qualifiedJavaType + ".class)");
        
        // Pointer to bitfield
        } else if (type.isBitfield()) {
            writer.write("new PointerBitfield<" + type.qualifiedJavaType + ">(" + identifier + ", " + type.qualifiedJavaType + ".class)");
        
        // Pointer to wrapped primitive value
        } else if (type.isAliasForPrimitive()) {
            writer.write("new Pointer" + Conversions.primitiveClassName(type.girElementInstance.type.qualifiedJavaType) + "(" + identifier + ")");
            
        // Pointer to primitive value
        } else if (type.isPrimitive) {
            writer.write("new Pointer" + Conversions.primitiveClassName(type.qualifiedJavaType) + "(" + identifier + ")");
        
        // Pointer to UTF8 memorysegment
        } else if (type.qualifiedJavaType.equals("java.lang.String")) {
            writer.write("new PointerString(" + identifier + ")");
        
        // Pointer to memorysegment
        } else if (type.qualifiedJavaType.equals("java.lang.foreign.MemoryAddress")) {
            writer.write("new PointerAddress(" + identifier + ")");
            
        // Pointer to object
        } else {
            writer.write("new PointerProxy<" + type.qualifiedJavaType + ">(" + identifier + ", " + type.qualifiedJavaType + ".class)");
        }
    }
    
    public String getReturnType() {
        // Arrays
        if (array != null) {
        	return getPointerReturnType(array.type, array.size());
        }
        // Pointers
        if (type.cType != null && type.cType.endsWith("**")) {
        	return getPointerReturnType(type, null);
        }
        // Create Pointer object
        if (type.isPrimitive && type.isPointer()) {
            return "Pointer" + Conversions.primitiveClassName(type.simpleJavaType);
        }
        // Anything else
        return type.qualifiedJavaType;
    }
    
    public String getPointerReturnType(Type type, String size) {
        // This should not happen
        if (type == null) {
            return "void";
        }
        // Size is known?
        if (size != null) {
        	return type.qualifiedJavaType + "[]";
        }
        // Pointer to enumeration
        if (type.isEnum()) {
            return "PointerEnumeration";
        }
        // Pointer to bitfield
        if (type.isEnum()) {
            return "PointerBitfield";
        }
        // Pointer to wrapped primitive value
        if (type.isAliasForPrimitive()) {
            return "Pointer" + Conversions.primitiveClassName(type.girElementInstance.type.qualifiedJavaType);
        }    
        // Pointer to primitive value
        if (type.isPrimitive) {
            return "Pointer" + Conversions.primitiveClassName(type.qualifiedJavaType);
        }
        // Pointer to UTF8 memorysegment
        if (type.qualifiedJavaType.equals("java.lang.String")) {
            return "PointerString";
        }
        // Pointer to pointer
        if (type.qualifiedJavaType.equals("java.lang.foreign.MemoryAddress")) {
            return "PointerAddress";
        }    
        // Pointer to object
        return "PointerProxy<" + type.qualifiedJavaType + ">";
    }
}
