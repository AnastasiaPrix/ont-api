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

package ru.avicomp.ontapi.jena.impl;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.*;
import org.apache.jena.rdf.model.impl.ModelCom;
import org.apache.jena.rdf.model.impl.PropertyImpl;
import org.apache.jena.rdf.model.impl.ResourceImpl;
import org.apache.jena.rdf.model.impl.StatementImpl;
import ru.avicomp.ontapi.jena.OntJenaException;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An implementation of {@link OntStatement OntStatement}.
 * This is an extended Jena {@link StatementImpl} with possibility to add annotations in the same form of  OntStatement.
 * Annotations can be plain (annotation assertion) or bulk (anonymous resource with rdf:type {@code owl:Axiom} or {@code owl:Annotation},
 * see {@link OntAnnotation}).
 * The examples of how to write bulk-annotations in RDF-graph see here:
 * <a href='https://www.w3.org/TR/owl2-mapping-to-rdf/#Translation_of_Annotations'>2.2 Translation of Annotations</a>.
 * <p>
 * Created by @szuev on 12.11.2016.
 */
@SuppressWarnings("WeakerAccess")
public class OntStatementImpl extends StatementImpl implements OntStatement {

    public OntStatementImpl(Resource subject, Property predicate, RDFNode object, OntGraphModel model) {
        super(subject, predicate, object, (ModelCom) model);
    }

    public OntStatementImpl(Statement statement) throws ClassCastException, NullPointerException {
        this(statement.getSubject(), statement.getPredicate(), statement.getObject(), (OntGraphModel) statement.getModel());
    }

    /**
     * Creates an OntStatement impl from the given Triple.
     * The OntStatement has subject, predicate, and object corresponding to those of Triple.
     *
     * @param t {@link Triple} not null
     * @param m {@link OntGraphModelImpl} model
     * @return {@link OntStatementImpl} fresh instance
     */
    public static OntStatementImpl createOntStatementImpl(Triple t, OntGraphModelImpl m) {
        return createOntStatementImpl(new ResourceImpl(t.getSubject(), m), t.getPredicate(), t.getObject(), m);
    }

    /**
     * Creates an OntStatement impl with the given SPO.
     *
     * @param s {@link Resource} subject
     * @param p {@link Node Graph RDF URI Node} predicate
     * @param o {@link Node Graph RDF Node} object
     * @param m {@link OntGraphModelImpl} model
     * @return {@link OntStatementImpl} fresh instance
     */
    public static OntStatementImpl createOntStatementImpl(Resource s, Node p, Node o, OntGraphModelImpl m) {
        return createOntStatementImpl(s, new PropertyImpl(p, m), o, m);
    }

    /**
     * Creates an OntStatement impl with the given SPO.
     *
     * @param s {@link Resource} subject
     * @param p {@link Property} predicate
     * @param o {@link Node Graph RDF Node} object
     * @param m {@link OntGraphModelImpl} model
     * @return {@link OntStatementImpl} fresh instance
     */
    public static OntStatementImpl createOntStatementImpl(Resource s, Property p, Node o, OntGraphModelImpl m) {
        return createOntStatementImpl(s, p, createObject(o, m), m);
    }

    /**
     * Creates an OntStatement impl with the given SPO.
     *
     * @param s {@link Resource} subject
     * @param p {@link Property} predicate
     * @param o {@link RDFNode Model RDF Node} object
     * @param m {@link OntGraphModelImpl} model
     * @return {@link OntStatementImpl} fresh instance
     */
    public static OntStatementImpl createOntStatementImpl(Resource s, Property p, RDFNode o, OntGraphModelImpl m) {
        return new OntStatementImpl(s, p, o, m);
    }

    /**
     * Creates a wrapper for the given ont-statement with in-memory caches.
     * Currently just for debugging.
     *
     * @param delegate {@link OntStatement}
     * @return {@link OntStatement}
     */
    public static OntStatement createCachedOntStatementImpl(OntStatement delegate) {
        return delegate instanceof CachedStatementImpl ? delegate : new CachedStatementImpl(delegate);
    }

    /**
     * Creates an ont-statement that does not support sub-annotations.
     * The method does not change the model.
     *
     * @param s {@link Resource} subject
     * @param p {@link Property} predicate
     * @param o {@link RDFNode} object
     * @param m {@link OntGraphModelImpl} model
     * @return {@link OntStatementImpl}
     */
    public static OntStatementImpl createNotAnnotatedOntStatementImpl(Resource s, Property p, RDFNode o, OntGraphModelImpl m) {
        return new OntStatementImpl(s, p, o, m) {
            @Override
            public OntStatement addAnnotation(OntNAP property, RDFNode value) {
                throw new OntJenaException.Unsupported("Sub-annotations are not supported (attempt to add " + Models.toString(this) + ")");
            }
        };
    }

    @Override
    public OntGraphModelImpl getModel() {
        return (OntGraphModelImpl) super.getModel();
    }

    @Override
    public boolean isRoot() {
        return isRootStatement();
    }

    public boolean isRootStatement() {
        return false;
    }

    public OntStatement asRootStatement() {
        return isRootStatement() ? this : new OntStatementImpl(getSubject(), getPredicate(), getObject(), getModel()) {

            @Override
            public boolean isRootStatement() {
                return true;
            }
        };
    }

    @Override
    public boolean isLocal() {
        return !getModel().getGraph().getUnderlying().hasSubGraphs() || getModel().isLocal(this);
    }

    @Override
    public OntObject getSubject() {
        return subject instanceof OntObject ? (OntObject) subject : subject.as(OntObject.class);
    }

    @Override
    public OntStatement addAnnotation(OntNAP property, RDFNode value) {
        OntJenaException.notNull(property, "Null property.");
        OntJenaException.notNull(value, "Null value.");
        if (isRootStatement()) {
            model.add(getSubject(), OntJenaException.notNull(property, "Null property."), OntJenaException.notNull(value, "Null value."));
            return getModel().createStatement(getSubject(), property, value);
        }
        return asAnnotationResource()
                .orElseGet(() -> createAnnotationObject(getModel(), OntStatementImpl.this, getAnnotationResourceType()))
                .addAnnotation(property, value);
    }

    @Override
    public Stream<OntStatement> annotations() {
        Stream<OntStatement> res = annotationResources().flatMap(OntAnnotation::assertions);
        if (isRootStatement()) {
            return Stream.concat(getSubject().statements().filter(OntStatement::isAnnotation), res);
        }
        return res;
    }

    @Override
    public void deleteAnnotation(OntNAP property, RDFNode value) {
        OntJenaException.notNull(property, "Null property.");
        OntJenaException.notNull(value, "Null value.");
        if (isRootStatement()) {
            model.removeAll(getSubject(), property, value);
        }
        Set<OntStatement> candidates = annotationResources()
                .flatMap(OntAnnotation::assertions)
                .filter(s -> Objects.equals(property, s.getPredicate()))
                .filter(s -> Objects.equals(value, s.getObject()))
                .collect(Collectors.toSet());
        if (candidates.isEmpty()) {
            return;
        }
        Set<OntStatement> delete = candidates.stream()
                .filter(s -> !s.hasAnnotations()).collect(Collectors.toSet());
        if (delete.isEmpty()) {
            throw new OntJenaException("Can't delete [*, " + property + ", " + value + "]: " +
                    "candidates have their own annotations which should be deleted first.");
        }
        OntGraphModelImpl model = getModel();
        delete.forEach(model::remove);
        Set<OntAnnotation> empty = annotationResources()
                .filter(f -> Objects.equals(f.listProperties().toSet().size(), OntAnnotationImpl.SPEC.size()))
                .collect(Collectors.toSet());
        empty.forEach(a -> model.removeAll(a, null, null));
    }

    protected Stream<Statement> properties() {
        return Iter.asStream(subject.listProperties());
    }

    @Override
    public Stream<OntAnnotation> annotationResources() {
        return listOntAnnotationResources(this, getAnnotationResourceType(), AttachedAnnotationImpl::new);
    }

    @Override
    public Optional<OntAnnotation> asAnnotationResource() {
        try (Stream<OntAnnotation> res = annotationResources().sorted(OntAnnotationImpl.DEFAULT_ANNOTATION_COMPARATOR)) {
            return res.findFirst();
        }
    }

    public List<OntAnnotation> getSortedAnnotations() {
        return annotationResources().sorted(OntAnnotationImpl.DEFAULT_ANNOTATION_COMPARATOR).collect(Collectors.toList());
    }

    /**
     * Warning: returns not lazy stream
     *
     * @return Stream of ont-statements.
     */
    public Stream<OntStatement> split() {
        List<OntAnnotation> res = getSortedAnnotations();
        if (res.size() < 2) {
            return Stream.of(this);
        }
        if (isRootStatement()) {
            OntAnnotation first = res.remove(0);
            OntStatementImpl r = new OntStatementImpl(this) {
                @Override
                public boolean isRootStatement() {
                    return true;
                }

                @Override
                public Stream<OntAnnotation> annotationResources() {
                    return Stream.of(first);
                }
            };
            return Stream.concat(Stream.of(r), res.stream().map(OntAnnotation::getBase));
        }
        return res.stream().map(OntAnnotation::getBase);
    }


    /**
     * Returns the rdf:type of attached annotation objects.
     *
     * @return {@link OWL#Axiom {@code owl:Axiom}} or {@link OWL#Annotation {@code owl:Annotation}}
     */
    protected Resource getAnnotationResourceType() {
        return detectAnnotationRootType(getSubject());
    }

    /**
     * Returns annotation objects corresponding to the given statement and rdfs-type
     *
     * @param base  base ont-statement
     * @param type  owl:Axiom or owl:Annotation
     * @param maker BiFunction to produce OntAnnotation resource
     * @return Stream of {@link OntAnnotation}
     */
    protected static Stream<OntAnnotation> listOntAnnotationResources(OntStatementImpl base, Resource type,
                                                                      BiFunction<Resource, OntStatementImpl, OntAnnotation> maker) {
        return listAnnotations(base.getModel(), type, base.getSubject(), base.getPredicate(), base.getObject())
                .map(r -> maker.apply(r, base));
    }

    /**
     * Lists all (bulk) annotations anonymous resources form the specified model.
     *
     * @param m {@link Model}
     * @param t {@link Resource} either {@link OWL#Axiom owl:Axiom} or {@link OWL#Annotation owl:Annotation}
     * @param s {@link Resource} subject
     * @param p {@link Property} predicate
     * @param o {@link RDFNode} object
     * @return Stream of {@link Resource}s
     */
    protected static Stream<Resource> listAnnotations(Model m, Resource t, Resource s, Property p, RDFNode o) {
        return Iter.asStream(m.listResourcesWithProperty(OWL.annotatedSource, s))
                .filter(r -> r.hasProperty(RDF.type, t))
                .filter(r -> r.hasProperty(OWL.annotatedProperty, p))
                .filter(r -> r.hasProperty(OWL.annotatedTarget, o));
    }

    /**
     * Creates new annotation section (anonymous resource with the given type).
     *
     * @param model {@link Model}
     * @param base  base ont-statement
     * @param type  owl:Axiom or owl:Annotation
     * @return {@link OntAnnotation} the anonymous resource with specified type.
     */
    protected static OntAnnotation createAnnotationObject(Model model, Statement base, Resource type) {
        Resource res = Objects.requireNonNull(model).createResource();
        if (!model.contains(Objects.requireNonNull(base))) {
            throw new OntJenaException.IllegalArgument("Can't find " + Models.toString(base));
        }
        res.addProperty(RDF.type, type);
        res.addProperty(OWL.annotatedSource, base.getSubject());
        res.addProperty(OWL.annotatedProperty, base.getPredicate());
        res.addProperty(OWL.annotatedTarget, base.getObject());
        return res.as(OntAnnotation.class);
    }

    /**
     * Determines the annotation type.
     * Root annotations (including some anon-axioms bodies) go with the type owl:Axiom {@link OWL#Axiom},
     * sub-annotations have type owl:Annotation.
     *
     * @param s {@link Resource} the subject resource to test
     * @return {@link OWL#Axiom} or {@link OWL#Annotation}
     */
    protected static Resource detectAnnotationRootType(OntObject s) {
        if (s.isAnon() && s.types()
                .anyMatch(t -> OWL.Axiom.equals(t) || OWL.Annotation.equals(t) || OntAnnotationImpl.EXTRA_ROOT_TYPES.contains(t))) {
            return OWL.Annotation;
        }
        return OWL.Axiom;
    }

    /**
     * An {@link OntAnnotationImpl} with reference to itself.
     */
    protected static class AttachedAnnotationImpl extends OntAnnotationImpl {
        private final OntStatementImpl base;

        public AttachedAnnotationImpl(Resource subject, OntStatementImpl base) {
            super(subject.asNode(), base.getModel());
            this.base = base;
        }

        @Override
        public OntStatement getBase() {
            return new OntStatementImpl(base.getSubject(), base.getPredicate(), base.getObject(), getModel()) {

                @Override
                public Stream<OntStatement> annotations() {
                    return assertions();
                }

                @Override
                public Optional<OntAnnotation> asAnnotationResource() {
                    return Optional.of(AttachedAnnotationImpl.this);
                }
            };
        }
    }

}
