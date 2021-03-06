/*
 * This file is part of the ONT API.
 * The contents of this file are subject to the LGPL License, Version 3.0.
 * Copyright (c) 2019, Avicomp Services, AO
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * Alternatively, the contents of this file may be used under the terms of the Apache License, Version 2.0 in which case, the provisions of the Apache License Version 2.0 are applicable instead of those above.
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package ru.avicomp.ontapi.config;

/**
 * A common interface to access cache settings.
 * <p>
 * Created by @ssz on 15.03.2019.
 *
 * @since 1.4.0
 */
public interface CacheSettings {

    /**
     * Returns the maximum size of nodes cache,
     * which is used as optimization while reading OWLObjects from a graph
     * (see {@link ru.avicomp.ontapi.internal.SearchModel}).
     * The system default size is {@code 50_000}.
     * <p>
     * Each {@link ru.avicomp.ontapi.jena.impl.conf.ObjectFactory object factory}
     * has its own nodes cache with the same size, but, as a rule, only a few factories have many nodes in their cache.
     * Average {@link org.apache.jena.graph.Node Node} (uri and blank) size is about 160 bytes (internal string ~ 150byte),
     * Experiments show that for the limit = 100_000, the total number of cached nodes is not more than 190_000
     * (it is for teleost and galen, significantly less for the rest tested ontologies),
     * The number 190_000 uri or blank nodes means about 30 MB.
     * Here the list of tested ontologies:
     * <ul>
     * <li>teleost(59mb, 336_291 axioms, 650_339 triples)</li>
     * <li>hp(38mb, 143_855 axioms, 367_315 triples)</li>
     * <li>galen(33mb, 96_463 axioms, 281_492 triples)</li>
     * <li>psychology(4mb, 38_872 axioms, 38_873 triples)</li>
     * <li>family(0.2mb, 2_845 axioms)</li>
     * <li>pizza(0.1mb, 945 axioms)</li>
     * </ul>
     *
     * @return int
     * @see OntSettings#ONT_API_LOAD_CONF_CACHE_NODES
     * @see CacheControl#setLoadNodesCacheSize(int)
     */
    int getLoadNodesCacheSize();

    /**
     * Returns the maximum size of objects cache,
     * which is used as optimization while reading OWLObjects from a graph
     * (see {@link ru.avicomp.ontapi.internal.CacheObjectFactory}).
     * The system default size is {@code 2048}.
     * This is magic number from OWL-API impl, which has also similar caches.
     *
     * @return int
     * @see OntSettings#ONT_API_LOAD_CONF_CACHE_OBJECTS
     * @see CacheControl#setLoadObjectsCacheSize(int)
     */
    int getLoadObjectsCacheSize();

    /**
     * Answers whether an internal model content cache is enabled, that is {@code true} by default.
     * An internal model content cache speedups axiom listing and controls add/remove components behaviour.
     * In case it is turned off,
     * then the direct graph traversal is used for retrieving axioms and ontology header (annotations).
     * Warning: in that case the adding and removing axioms is disabled in the model level.
     * But this concerns only the top-level {@link ru.avicomp.ontapi.OntologyModel OWL Model} interface.
     * A graph is editable, if it is not restricted in some other place.
     * So it is possible to modify model using {@link ru.avicomp.ontapi.jena.model.OntGraphModel} interface
     * (see the method {@link ru.avicomp.ontapi.OntologyModel#asGraphModel()}).
     * Also, to add axiom a {@link ru.avicomp.ontapi.internal.AxiomTranslator} mechanism can be used,
     * e.g. to add the axiom {@code A} into the RDF Model {@code m},
     * the expression {@code AxiomParserProvider.get(A).writeAxiom(A, m)} can be used.
     * Disabling internal content cache may be useful in case the ontology is too large to fit in memory.
     * In the normal case, it is better not to turn off this cache.
     *
     * @return boolean
     * @see OntSettings#ONT_API_LOAD_CONF_CACHE_CONTENT
     * @see CacheControl#setUseContentCache(boolean)
     */
    boolean isContentCacheEnabled();

    /**
     * Answers {@code true} if nodes cache is enabled.
     *
     * @return boolean
     */
    default boolean useLoadNodesCache() {
        return getLoadNodesCacheSize() > 0;
    }

    /**
     * Answers {@code true} if objects cache is enabled.
     *
     * @return boolean
     */
    default boolean useLoadObjectsCache() {
        return getLoadObjectsCacheSize() > 0;
    }

}
