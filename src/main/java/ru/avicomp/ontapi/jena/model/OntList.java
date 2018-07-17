/*
 * This file is part of the ONT API.
 * The contents of this file are subject to the LGPL License, Version 3.0.
 * Copyright (c) 2018, Avicomp Services, AO
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * Alternatively, the contents of this file may be used under the terms of the Apache License, Version 2.0 in which case, the provisions of the Apache License Version 2.0 are applicable instead of those above.
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package ru.avicomp.ontapi.jena.model;

import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A parameterized analogue of {@link RDFList} that behaves like collection.
 * Currently it is not an {@link OntObject ontology object} and not a personality resource.
 * The latter means you cannot view any rdf-node as this interface,
 * but you can do the opposite: cast instance of this interface to the {@link RDFList rdf:List} view
 * using command {@code ont-list.as(RDFList.class)}.
 * TODO: not fully ready (should extend OntResource with common methods).
 * Created by @szuev on 10.07.2018.
 *
 * @param <E> the type of {@link RDFNode rdf-node}s in this list
 * @since 1.2.1
 */
public interface OntList<E extends RDFNode> extends Resource {

    OntGraphModel getModel();

    /**
     * Answers {@code true} if this list is the empty list (nil).
     *
     * @return boolean
     */
    boolean isEmpty();

    /**
     * Lists all elements of type {@link E} from this list.
     * Note: the list may contain nodes with incompatible type, in this case they will be skipped.
     *
     * @return Stream of {@link E}-elements
     */
    Stream<E> members();

    /**
     * Adds the given value to the end of the list.
     *
     * @param e {@link E} rdf-node
     * @return this list instance
     */
    OntList<E> add(E e);

    /**
     * Removes the last element from this list.
     * Note: the last element can be of any type, not necessarily of type {@link E}.
     *
     * @return this list instance
     */
    OntList<E> remove();

    OntList<E> addFirst(E e);

    OntList<E> removeFirst();

    OntList<E> clear();

    OntList<E> get(int index);

    /**
     * Answers the number of {@link RDFNode rdf-node}s in the list.
     * Note: in general this operation is not equivalent to {@code this.members().count()}.
     *
     * @return the real size of the list as an integer
     */
    default int size() {
        return as(RDFList.class).size();
    }

    /**
     * Adds the given value to the end of the list.
     * This is a synonym for the {@code this.add(e)}.
     *
     * @param e {@link E} rdf-node
     * @return this list instance
     */
    default OntList<E> addLast(E e) {
        return add(e);
    }

    default OntList<E> removeLast() {
        return remove();
    }

    default Optional<E> first() {
        return members().findFirst();
    }

    default Optional<E> last() {
        return members().reduce((f, s) -> s);
    }

    default OntList<E> addAll(Collection<? extends E> c) {
        c.forEach(this::add);
        return this;
    }
}
