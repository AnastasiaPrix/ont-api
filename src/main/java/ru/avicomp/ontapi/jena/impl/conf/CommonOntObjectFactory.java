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

package ru.avicomp.ontapi.jena.impl.conf;

import org.apache.jena.enhanced.EnhGraph;
import org.apache.jena.enhanced.EnhNode;
import org.apache.jena.graph.Node;
import org.apache.jena.ontology.ConversionException;
import ru.avicomp.ontapi.jena.OntJenaException;

import java.util.stream.Stream;

/**
 * Default implementation of {@link OntObjectFactory}.
 * This is a designer that consists of three modules:
 * <ul>
 * <li>{@link OntMaker} for initialization and physical creation a node {@link EnhNode} in the graph {@link EnhGraph}.</li>
 * <li>{@link OntFilter} to test the presence of a node in the graph.</li>
 * <li>{@link OntFinder} to search for nodes in the graph.</li>
 * </ul>
 * <p>
 * Created by szuev on 07.11.2016.
 */
public class CommonOntObjectFactory extends OntObjectFactory {
    private final OntMaker maker;
    private final OntFinder finder;
    private final OntFilter filter;

    public CommonOntObjectFactory(OntMaker maker, OntFinder finder, OntFilter primary, OntFilter... additional) {
        this.maker = OntJenaException.notNull(maker, "Null maker.");
        this.finder = OntJenaException.notNull(finder, "Null finder.");
        this.filter = OntJenaException.notNull(primary, "Null primary filter.").accumulate(additional);
    }

    public OntMaker getMaker() {
        return maker;
    }

    public OntFinder getFinder() {
        return finder;
    }

    public OntFilter getFilter() {
        return filter;
    }

    @Override
    public EnhNode wrap(Node node, EnhGraph eg) {
        if (!canWrap(node, eg))
            throw new ConversionException(String.format("Can't wrap node %s to %s", node, maker.getImpl()));
        return doWrap(node, eg);
    }

    @Override
    public boolean canWrap(Node node, EnhGraph eg) {
        return filter.test(node, eg);
    }

    @Override
    public EnhNode create(Node node, EnhGraph eg) {
        if (!canCreate(node, eg))
            throw new OntJenaException.Creation(String.format("Can't modify graph for %s (%s)", node, maker.getImpl()));
        maker.make(node, eg);
        return doWrap(node, eg);
    }

    @Override
    public boolean canCreate(Node node, EnhGraph eg) {
        return maker.getTester().test(node, eg);
    }

    @Override
    public Stream<EnhNode> find(EnhGraph eg) {
        return finder.restrict(filter).find(eg).map(n -> maker.instance(n, eg));
    }

    @Override
    protected EnhNode doWrap(Node node, EnhGraph eg) {
        return maker.instance(node, eg);
    }
}
