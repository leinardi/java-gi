/* Java-GI - Java language bindings for GObject-Introspection-based libraries
 * Copyright (C) 2022-2024 the Java-GI developers
 *
 * SPDX-License-Identifier: LGPL-2.1-or-later
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, see <http://www.gnu.org/licenses/>.
 */

package io.github.jwharm.javagi.gir;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.TypeName;
import io.github.jwharm.javagi.util.Conversions;

import static io.github.jwharm.javagi.util.CollectionUtils.*;
import static io.github.jwharm.javagi.util.Conversions.toJavaIdentifier;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class Array extends GirElement implements AnyType {

    public Array(Map<String, String> attributes, List<Node> children) {
        super(attributes, children);
    }

    @Override
    public TypeName typeName() {
        return ArrayTypeName.of(anyType().typeName());
    }

    public boolean zeroTerminated() {
        // If zero-terminated is missing, there's no length, there's no fixed size,
        // and the name attribute is unset, then zero-terminated is true.
        if (attr("zero-terminated") != null)
            return attrBool("zero-terminated", true);
        return Stream.of("length", "fixed-size", "name").noneMatch(attributes()::containsKey);
    }

    public int fixedSize() {
        return attrInt("fixed-size");
    }

    public boolean introspectable() {
        return attrBool("introspectable", true);
    }

    public TypedValue length() {
        int index = attrInt("length");
        if (index == -1) return null;
        return switch (parent()) {
            case Field _ -> ((FieldContainer) parent().parent()).getAtIndex(index);
            case Parameter p -> p.parent().getAtIndex(index);
            case ReturnValue rv -> ((Callable) rv.parent()).parameters().getAtIndex(index);
            default -> throw new AssertionError("Parent is not a Field, Parameter or ReturnValue");
        };
    }

    public boolean unknownSize() {
        return fixedSize() <= 0 && !zeroTerminated() && length() == null;
    }

    public String sizeExpression(boolean upcall) {
        if (attr("fixed-size") != null) return attr("fixed-size");
        TypedValue length = length();
        if (length instanceof Parameter lp) {
            String name = toJavaIdentifier(lp.name());
            if (upcall && lp.isOutParameter())
                return "_" + name + "Out.get()";
            if (lp.anyType() instanceof Type type) {
                if (type.isPointer() || lp.isOutParameter())
                    return name + ".get().intValue()";
                if (type.get() instanceof Alias a && a.type().isPrimitive())
                    return name + ".getValue()";
            }
            return name;
        } else if (length instanceof Field lf) {
            if (lf.anyType() instanceof Type type && (!type.isPointer()))
                return "read" + Conversions.toCamelCase(lf.name(), true) + "()";
        }
        return null;
    }

    public AnyType anyType() {
        return findAny(children(), AnyType.class);
    }
}
