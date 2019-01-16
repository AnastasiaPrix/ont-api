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

package ru.avicomp.ontapi.jena.impl;

import org.apache.jena.enhanced.EnhGraph;
import org.apache.jena.graph.FrontsNode;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.util.iterator.ExtendedIterator;
import ru.avicomp.ontapi.jena.OntJenaException;
import ru.avicomp.ontapi.jena.impl.conf.*;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntIndividual;
import ru.avicomp.ontapi.jena.model.OntObject;
import ru.avicomp.ontapi.jena.model.OntStatement;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * An {@link OntIndividual} implementation, both for anonymous and named individuals.
 * <p>
 * Created by szuev on 09.11.2016.
 */
@SuppressWarnings("WeakerAccess")
public class OntIndividualImpl extends OntObjectImpl implements OntIndividual {

    public static final OntFilter ANONYMOUS_FILTER = OntIndividualImpl::testAnonymousIndividual;

    public static final Set<Node> ALLOWED_IN_SUBJECT_PREDICATES =
            Stream.concat(Entities.BUILTIN.properties().stream(), Stream.of(OWL.sameAs, OWL.differentFrom))
                    .map(FrontsNode::asNode).collect(Iter.toUnmodifiableSet());
    public static final Set<Node> ALLOWED_IN_OBJECT_PREDICATES =
            Stream.concat(Entities.BUILTIN.properties().stream(),
                    Stream.of(OWL.sameAs, OWL.differentFrom, OWL.sourceIndividual, OWL.hasValue, RDF.first))
                    .map(FrontsNode::asNode).collect(Iter.toUnmodifiableSet());

    public static final Set<Property> RESERVED = Entities.BUILTIN.reservedProperties();
    public static final Set<Node> BUILT_IN_SUBJECT_PREDICATE_SET = RESERVED.stream()
            .map(FrontsNode::asNode)
            .filter(n -> !ALLOWED_IN_SUBJECT_PREDICATES.contains(n))
            .collect(Iter.toUnmodifiableSet());
    public static final Set<Node> BUILT_IN_OBJECT_PREDICATE_SET = RESERVED.stream()
            .map(FrontsNode::asNode)
            .filter(n -> !ALLOWED_IN_OBJECT_PREDICATES.contains(n))
            .collect(Iter.toUnmodifiableSet());

    public static OntFinder FINDER = OntFinder.ANY_SUBJECT_AND_OBJECT;
    public static OntObjectFactory anonymousIndividualFactory =
            new CommonOntObjectFactory(new OntMaker.Default(AnonymousImpl.class), FINDER, ANONYMOUS_FILTER);

    public static Configurable<OntObjectFactory> abstractIndividualFactory = buildMultiFactory(FINDER, null,
            Entities.INDIVIDUAL, anonymousIndividualFactory);

    public OntIndividualImpl(Node n, EnhGraph m) {
        super(n, m);
    }

    public static boolean testAnonymousIndividual(Node node, EnhGraph eg) {
        if (!node.isBlank()) {
            return false;
        }
        boolean hasType = false;
        // class-assertion:
        ExtendedIterator<Node> types = eg.asGraph().find(node, RDF.Nodes.type, Node.ANY).mapWith(Triple::getObject);
        try {
            while (types.hasNext()) {
                if (OntObjectImpl.canAs(OntCE.class, types.next(), eg)) return true;
                hasType = true;
            }
        } finally {
            types.close();
        }
        // any other typed statement:
        if (hasType) {
            return false;
        }
        // _:x @built-in-predicate @any:
        ExtendedIterator<Node> bySubject = eg.asGraph().find(node, Node.ANY, Node.ANY).mapWith(Triple::getPredicate);
        try {
            while (bySubject.hasNext()) {
                if (BUILT_IN_SUBJECT_PREDICATE_SET.contains(bySubject.next()))
                    return false;
            }
        } finally {
            bySubject.close();
        }
        // @any @built-in-predicate _:x
        ExtendedIterator<Node> byObject = eg.asGraph().find(Node.ANY, Node.ANY, node).mapWith(Triple::getPredicate);
        try {
            while (byObject.hasNext()) {
                if (BUILT_IN_OBJECT_PREDICATE_SET.contains(byObject.next()))
                    return false;
            }
        } finally {
            byObject.close();
        }
        // tolerantly allow any other blank node to be treated as anonymous individual:
        return true;
    }

    public static OntIndividual.Anonymous createAnonymousIndividual(RDFNode node) {
        if (OntJenaException.notNull(node, "Null node.").canAs(OntIndividual.Anonymous.class))
            return node.as(OntIndividual.Anonymous.class);
        if (node.isAnon()) {
            return new AnonymousImpl(node.asNode(), (EnhGraph) node.getModel());
        }
        throw new OntJenaException.Conversion(node + " could not be " + OntIndividual.Anonymous.class);
    }

    @Override
    public OntStatement attachClass(OntCE clazz) {
        return addRDFType(clazz);
    }

    @Override
    public void detachClass(OntCE clazz) {
        removeRDFType(clazz);
    }

    public static class NamedImpl extends OntIndividualImpl implements OntIndividual.Named {
        public NamedImpl(Node n, EnhGraph m) {
            super(OntObjectImpl.checkNamed(n), m);
        }

        @Override
        public boolean isBuiltIn() {
            return false;
        }

        @Override
        public Optional<OntStatement> findRootStatement() {
            // there are no built-in named individuals, the root is required:
            return getRequiredRootStatement(this, OWL.NamedIndividual);
        }

        @Override
        public Class<? extends OntObject> getActualClass() {
            return OntIndividual.Named.class;
        }
    }

    /**
     * See description to the interface {@link OntIndividual.Anonymous}.
     * The current implementation allows treating b-node as anonymous individual
     * in any case with exception of the following:
     * <ul>
     * <li>it is a subject in statement "_:x rdf:type s", where "s" is not a class expression ("C").</li>
     * <li>it is a subject in statement "_:x @predicate @any", where @predicate is from reserved vocabulary
     * but not object, data or annotation built-in property
     * and not owl:sameAs and owl:differentFrom.</li>
     * <li>it is an object in statement "@any @predicate _:x", where @predicate is from reserved vocabulary
     * but not object, data or annotation built-in property
     * and not owl:sameAs, owl:differentFrom, owl:hasValue, owl:sourceIndividual and rdf:first.</li>
     * </ul>
     * <p>
     * for notations and self-education see our main <a href='https://www.w3.org/TR/owl2-quick-reference/'>OWL2 Quick Refs</a>
     */
    public static class AnonymousImpl extends OntIndividualImpl implements OntIndividual.Anonymous {

        public AnonymousImpl(Node n, EnhGraph m) {
            super(n, m);
        }

        @Override
        public Optional<OntStatement> findRootStatement() {
            return Optional.empty();
        }

        @Override
        public Class<? extends OntObject> getActualClass() {
            return OntIndividual.Anonymous.class;
        }

        @Override
        public void detachClass(OntCE clazz) {
            if (classes().allMatch(clazz::equals)) {
                // otherwise the anonymous individual could be lost.
                // use another way for removing the single class-assertion.
                throw new OntJenaException("Can't detach class " + clazz + ": it is a single for individual " + this);
            }
            super.detachClass(clazz);
        }

    }
}
