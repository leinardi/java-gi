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

import java.util.List;
import java.util.Map;

public interface Node {
    Namespace namespace();
    Node parent();
    void setParent(Node parent);

    List<Node> children();
    Map<String, String> attributes();

    String attr(String key);
    int attrInt(String key);
    boolean attrBool(String key, boolean defaultValue);

    <T extends Node> T withAttribute(String attrName, String newValue);
    <T extends Node> T withChildren(GirElement... newChildren);
    <T extends Node> T withChildren(List<Node> newChildren);
}
