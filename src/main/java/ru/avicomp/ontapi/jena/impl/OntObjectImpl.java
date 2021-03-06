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
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.*;
import org.apache.jena.rdf.model.impl.ResourceImpl;
import org.apache.jena.shared.PropertyNotFoundException;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.util.iterator.WrappedIterator;
import ru.avicomp.ontapi.jena.OntJenaException;
import ru.avicomp.ontapi.jena.impl.conf.ObjectFactory;
import ru.avicomp.ontapi.jena.impl.conf.OntFilter;
import ru.avicomp.ontapi.jena.impl.conf.OntFinder;
import ru.avicomp.ontapi.jena.model.OntEntity;
import ru.avicomp.ontapi.jena.model.OntNAP;
import ru.avicomp.ontapi.jena.model.OntObject;
import ru.avicomp.ontapi.jena.model.OntStatement;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The base for any Ontology Object {@link Resource} implementation.
 * <p>
 * Created by szuev on 03.11.2016.
 */
@SuppressWarnings("WeakerAccess")
public class OntObjectImpl extends ResourceImpl implements OntObject {

    public static ObjectFactory objectFactory = Factories.createCommon(OntObjectImpl.class,
            OntFinder.ANY_SUBJECT, OntFilter.URI.or(OntFilter.BLANK));

    public OntObjectImpl(Node n, EnhGraph m) {
        super(n, m);
    }

    /**
     * Finds a root statement for the given ontology object and resource type.
     * Throws an exception if the corresponding triple is not found.
     *
     * @param subject {@link OntObjectImpl}, the subject
     * @param type    URI-{@link Resource}, the type
     * @return Optional around {@link OntStatement}, which is never empty
     * @throws OntJenaException.IllegalState in case there is no root statement
     *                                       within the graph for the specified parameters
     */
    protected static Optional<OntStatement> getRequiredRootStatement(OntObjectImpl subject, Resource type)
            throws OntJenaException.IllegalState {
        // there are no built-in named individuals:
        Optional<OntStatement> res = getOptionalRootStatement(subject, type);
        if (!res.isPresent())
            throw new OntJenaException.IllegalState("Can't find " + subject.getModel().shortForm(type.getURI()) +
                    " declaration for " + subject);
        return res;
    }

    /**
     * Finds a root statement for the given ontology object and resource type.
     * Returns an empty {@code Optional} if the corresponding triple is not found.
     *
     * @param subject {@link OntObjectImpl}, the subject
     * @param type    URI-{@link Resource}, the type
     * @return Optional around {@link OntStatement} or {@code Optional.empty()} in case
     * there is no root statement within the graph for the specified parameters
     */
    protected static Optional<OntStatement> getOptionalRootStatement(OntObjectImpl subject, Resource type) {
        if (!subject.hasProperty(RDF.type, checkNamed(type))) return Optional.empty();
        return Optional.of(subject.getModel().createStatement(subject, RDF.type, type).asRootStatement());
    }

    /**
     * Answers a short form of a given class-type.
     *
     * @param type {@code Class}-type, not {@code null}
     * @return String
     */
    public static String viewAsString(Class<? extends RDFNode> type) {
        return type.getName().replace(OntObject.class.getPackage().getName() + ".", "");
    }

    public static Node checkNamed(Node res) {
        if (OntJenaException.notNull(res, "Null node").isURI()) {
            return res;
        }
        throw new OntJenaException.IllegalArgument("Not uri node " + res);
    }

    public static Resource checkNamed(Resource res) {
        if (OntJenaException.notNull(res, "Null resource").isURIResource()) {
            return res;
        }
        throw new OntJenaException.IllegalArgument("Not uri resource " + res);
    }

    /**
     * Filters the given extended iterator to contain only builtin entities of the specified type.
     *
     * @param type type of entity
     * @param from {@link ExtendedIterator} of {@link RDFNode}s
     * @param <E>  subclass of {@link OntEntity}
     * @return {@link ExtendedIterator} of {@link E}s
     */
    static <E extends OntEntity> ExtendedIterator<E> filterBuiltin(Class<E> type,
                                                                   ExtendedIterator<? extends RDFNode> from) {
        return from.filterKeep(x -> x.canAs(type) && x.as(type).isBuiltIn()).mapWith(x -> x.as(type));
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code true} if the root statement belongs to the base graph
     */
    @Override
    public boolean isLocal() {
        return findRootStatement().map(OntStatement::isLocal).orElse(false);
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link OntStatement}
     */
    @Override
    public final OntStatement getRoot() {
        return findRootStatement().orElse(null);
    }

    /**
     * {@inheritDoc}
     *
     * @return Stream of {@link OntStatement}s
     */
    @Override
    public Stream<OntStatement> spec() {
        return findRootStatement().map(Stream::of).orElse(Stream.empty());
    }

    /**
     * Finds the <b>first</b> declaration root statement.
     * The graph may contain several triples with predicate {@code rdf:type} and this ontology object as subject.
     * In this case the result is unpredictable.
     *
     * @return Optional around {@link OntStatement} that supports plain annotation assertions
     */
    public Optional<OntStatement> findRootStatement() {
        return Iter.findFirst(listObjects(RDF.type))
                .map(o -> getModel().createStatement(this, RDF.type, o).asRootStatement());
    }

    /**
     * {@inheritDoc}
     *
     * @return <b>distinct</b> Stream of {@link OntStatement}s
     */
    @Override
    public Stream<OntStatement> content() {
        return Stream.concat(spec(), statements().filter(x -> !x.isAnnotation()).collect(Collectors.toSet()).stream())
                .distinct();
    }

    /**
     * Adds or removes {@code @this rdf:type @type} statement.
     *
     * @param type URI-{@link Resource}, the type
     * @param add  if {@code true} the add operation is performed, otherwise the remove
     */
    protected void changeRDFType(Resource type, boolean add) {
        if (add) {
            addRDFType(type);
        } else {
            removeRDFType(type);
        }
    }

    /**
     * Adds {@code @this rdf:type @type} statement.
     *
     * @param type URI-{@link Resource}, the type
     * @return newly added {@link OntStatement}, that does not supports plain annotations
     */
    protected OntStatement addRDFType(Resource type) {
        return addStatement(RDF.type, OntJenaException.notNull(type, "Null rdf:type"));
    }

    /**
     * Removes {@code @this rdf:type @type} statement.
     * No-op in case no the statement is found.
     *
     * @param type URI-{@link Resource}, the type
     */
    protected void removeRDFType(Resource type) {
        remove(RDF.type, type);
    }

    /**
     * {@inheritDoc}
     *
     * @param property {@link Property}
     * @return Optional around {@link OntStatement}
     */
    @Override
    public Optional<OntStatement> statement(Property property) {
        try (Stream<OntStatement> res = statements(property)) {
            return res.findFirst();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param property {@link Property}, the predicate
     * @param value    {@link RDFNode}, the object
     * @return Optional around {@link OntStatement}
     */
    @Override
    public Optional<OntStatement> statement(Property property, RDFNode value) {
        try (Stream<OntStatement> res = getModel().statements(this, property, value)) {
            return res.findFirst();
        }
    }

    /**
     * Returns an ont-statement with the given subject and property.
     * If more than one statement that match the patter exists in the model,
     * it is undefined which will be returned.
     * If none exist, an exception is thrown.
     *
     * @param property {@link Property}, the predicate
     * @return {@link OntStatement}
     * @throws PropertyNotFoundException no statement are found
     */
    @Override
    public OntStatement getRequiredProperty(Property property) throws PropertyNotFoundException {
        return Iter.findFirst(listStatements(property)).orElseThrow(() -> new PropertyNotFoundException(property));
    }

    /**
     * Lists all statements for the given predicates and this ontology object as subject.
     *
     * @param properties Array of {@link Property properties}
     * @return Stream of {@link OntStatement}s
     */
    protected Stream<OntStatement> required(Property... properties) {
        return Arrays.stream(properties).map(this::getRequiredProperty);
    }

    @Override
    public OntStatement addStatement(Property property, RDFNode value) {
        OntStatement res = getModel().createStatement(this,
                OntJenaException.notNull(property, "Null property."),
                OntJenaException.notNull(value, "Null value."));
        getModel().add(res);
        return res;
    }

    @Override
    public OntObjectImpl remove(Property property, RDFNode value) {
        getModel().removeAll(this, OntJenaException.notNull(property, "Null property."),
                OntJenaException.notNull(value, "Null value."));
        return this;
    }

    @Override
    public final Stream<OntStatement> statements(Property property) {
        return Iter.asStream(listStatements(property));
    }

    @Override
    public final Stream<OntStatement> statements() {
        return Iter.asStream(listStatements());
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link StmtIterator} which contains {@link OntStatement}s
     * @see #listStatements()
     */
    @Override
    public StmtIterator listProperties() {
        return listProperties(null);
    }

    /**
     * Returns an {@link ExtendedIterator Extended Iterator} over all the properties of this resource.
     * The model associated with this resource is search and an iterator is
     * returned which iterates over all the statements which have this resource as a subject.
     *
     * @return {@link ExtendedIterator} over all the {@link OntStatement}s about this object
     * @see #listProperties()
     * @since 1.3.0
     */
    public ExtendedIterator<OntStatement> listStatements() {
        return listStatements(null);
    }

    /**
     * {@inheritDoc}
     *
     * @param p {@link Property}, the predicate to search, can be {@code null}
     * @return {@link StmtIterator}
     * @see #listStatements(Property)
     */
    @Override
    public StmtIterator listProperties(Property p) {
        return Iter.createStmtIterator(getModel().getGraph().find(asNode(), OntGraphModelImpl.asNode(p), Node.ANY),
                t -> createOntStatement(p, t));
    }

    /**
     * Lists all the values of the property {@code p}.
     * Returns an {@link ExtendedIterator Extended Iterator} over all the statements in the associated model whose
     * subject is this resource and whose predicate is {@code p}.
     *
     * @param p {@link Property}, the predicate sought, can be {@code null}
     * @return {@link ExtendedIterator} over the {@link OntStatement}s
     * @see #listStatements(Property)
     * @since 1.3.0
     */
    public ExtendedIterator<OntStatement> listStatements(Property p) {
        return WrappedIterator.create(getModel().getGraph().find(asNode(), OntGraphModelImpl.asNode(p), Node.ANY)
                .mapWith(t -> createOntStatement(p, t)));
    }

    /**
     * Creates a new {@link OntStatement} instance using the given {@link Triple} and {@link Property}.
     * The object and (if possible) the predicate property of the new statement are cached inside model
     * Auxiliary method.
     *
     * @param p {@link Property}, can be {@code null}
     * @param t {@link Triple}, not {@code null}
     * @return new {@link OntStatement} around the triple
     */
    protected OntStatementImpl createOntStatement(Property p, Triple t) {
        OntGraphModelImpl m = getModel();
        Property property = p == null ? m.getNodeAs(t.getPredicate(), Property.class) : p;
        RDFNode object = m.getNodeAs(t.getObject(), RDFNode.class);
        return OntStatementImpl.createOntStatementImpl(this, property, object, getModel());
    }

    /**
     * Lists all annotation property assertions (so called plain annotations) attached to this object
     * plus all bulk annotations of the root statement.
     *
     * @return Stream of {@link OntStatement}s
     * @see #listAnnotations()
     */
    @Override
    public final Stream<OntStatement> annotations() {
        return Iter.asStream(listAnnotations());
    }

    /**
     * Lists all related annotation assertions.
     *
     * @return {@link ExtendedIterator} of {@link OntStatement}s
     * @see #annotations()
     * @since 1.3.0
     */
    public ExtendedIterator<OntStatement> listAnnotations() {
        ExtendedIterator<OntStatement> res = listAssertions();
        Optional<OntStatement> main = findRootStatement();
        if (!main.isPresent()) {
            return res;
        }
        OntStatementImpl s = (OntStatementImpl) main.get();
        return res.andThen(Iter.flatMap(s.listAnnotationResources(), a -> ((OntAnnotationImpl) a).listAssertions()));
    }

    /**
     * Lists all annotation property assertions (so called plain annotations) attached to this object.
     *
     * @return Stream of {@link OntStatement}s
     * @see #listAssertions()
     */
    public final Stream<OntStatement> assertions() {
        return Iter.asStream(listAssertions());
    }

    /**
     * Returns an iterator over object's annotation property assertions.
     * The annotation assertion is a statements with an {@link OntNAP annotation property} as predicate.
     *
     * @return {@link ExtendedIterator} of {@link OntStatement}s
     * @see #assertions()
     * @since 1.3.0
     */
    public ExtendedIterator<OntStatement> listAssertions() {
        return listStatements().filterKeep(OntStatement::isAnnotation);
    }

    /**
     * Returns an iterator over all literal's annotations.
     *
     * @param predicate {@link OntNAP}, not {@code null}
     * @return {@link ExtendedIterator} of {@link Literal}s
     * @see #listAnnotations()
     * @since 1.3.2
     */
    public ExtendedIterator<Literal> listAnnotationLiterals(OntNAP predicate) {
        return listAnnotations()
                .filterKeep(s -> Objects.equals(predicate, s.getPredicate()))
                .mapWith(Statement::getObject)
                .filterKeep(RDFNode::isLiteral)
                .mapWith(RDFNode::asLiteral);
    }

    @Override
    public Stream<String> annotationValues(OntNAP p, String lang) {
        if (lang == null) return Iter.asStream(listAnnotationLiterals(p).mapWith(Literal::getString));
        return Iter.asStream(listAnnotationLiterals(p))
                .sorted(Comparator.comparing(Literal::getLanguage))
                .filter(l -> {
                    String target = l.getLanguage();
                    if (lang.isEmpty())
                        return target.isEmpty();
                    String x = target.length() > lang.length() ? target.substring(0, lang.length()) : target;
                    return lang.equalsIgnoreCase(x);
                })
                .map(Literal::getString);
    }

    /**
     * Adds an annotation assertion.
     * It could be expanded to bulk form by adding sub-annotation.
     *
     * @param property {@link OntNAP}, Named annotation property.
     * @param value    {@link RDFNode} the value: uri-resource, literal or anonymous individual.
     * @return OntStatement for newly added annotation
     * @throws OntJenaException in case input is wrong
     */
    @Override
    public OntStatement addAnnotation(OntNAP property, RDFNode value) {
        return findRootStatement().map(r -> r.addAnnotation(property, value))
                .orElseGet(() -> getModel().createStatement(addProperty(property, value), property, value));
    }

    /**
     * {@inheritDoc}
     *
     * @return this instance
     */
    @Override
    public OntObjectImpl clearAnnotations() {
        // for built-ins
        assertions().peek(OntStatement::clearAnnotations).collect(Collectors.toSet())
                .forEach(a -> getModel().remove(a));
        // for others
        findRootStatement().ifPresent(OntStatement::clearAnnotations);
        return this;
    }

    /**
     * Removes all objects for predicate (if object is rdf:List removes all content)
     *
     * @param predicate Property
     */
    public void clearAll(Property predicate) {
        listProperties(predicate).mapWith(Statement::getObject)
                .filterKeep(n -> n.canAs(RDFList.class))
                .mapWith(n -> n.as(RDFList.class)).forEachRemaining(RDFList::removeList);
        removeAll(predicate);
    }

    /**
     * Returns an object from a first found statement with specified predicate.
     * Since the order in the graph is undefined
     * in case there are more then one statement for a property the result is unpredictable.
     * TODO: throw exception in case there is more than one object ?
     *
     * @param predicate {@link Property}
     * @param view      Class
     * @param <T>       {@link RDFNode} type
     * @return an object from statement
     * @throws OntJenaException in case no object by predicate has been found
     * @see #getRequiredProperty(Property)
     */
    public <T extends RDFNode> T getRequiredObject(Property predicate, Class<T> view) {
        return object(predicate, view)
                .orElseThrow(() -> new OntJenaException(
                        String.format("Can't find required object [%s @%s %s]", this, predicate, viewAsString(view))));
    }

    /**
     * Finds a <b>first</b> object with the given {@code rdf:type}
     * attached to this ontology object on the given {@code predicate}.
     * The result is unpredictable in case there more then one statement for these conditions.
     *
     * @param predicate {@link Property}
     * @param type      sub-class of {@link RDFNode}
     * @param <T>       any subtype of {@link RDFNode}
     * @return Optional around {@link T}
     */
    public <T extends RDFNode> Optional<T> object(Property predicate, Class<T> type) {
        return Iter.findFirst(listObjects(predicate, type));
    }

    /**
     * {@inheritDoc}
     *
     * @param predicate {@link Property} predicate, can be null
     * @param type      Interface to find and cast, not null
     * @param <O>       any sub-type of {@link RDFNode}
     * @return Stream of {@link RDFNode node}s of the {@link O} type
     */
    @Override
    public final <O extends RDFNode> Stream<O> objects(Property predicate, Class<O> type) {
        return Iter.asStream(listObjects(predicate, type));
    }

    /**
     * Lists all objects for the given predicate and type.
     *
     * @param predicate {@link Property}, can be null
     * @param type      Interface to find and cast, not null
     * @param <O>       subtype of {@link RDFNode rdf-node}
     * @return {@link ExtendedIterator} of {@link RDFNode node}s of the {@link O} type
     * @see #object(Property, Class)
     * @since 1.3.0
     */
    public <O extends RDFNode> ExtendedIterator<O> listObjects(Property predicate, Class<O> type) {
        OntGraphModelImpl m = getModel();
        return listProperties(predicate)
                .mapWith(s -> m.findNodeAs(s.getObject().asNode(), type))
                .filterDrop(Objects::isNull);
    }

    /**
     * Lists all objects for the given predicate.
     *
     * @param predicate {@link Property}
     * @return Stream of {@link RDFNode}s
     * @see #listObjects(Property)
     */
    public final Stream<RDFNode> objects(Property predicate) {
        return Iter.asStream(listObjects(predicate));
    }

    /**
     * Lists all objects for the given predicate.
     *
     * @param predicate {@link Property}
     * @return {@link ExtendedIterator} of {@link RDFNode}s
     * @see #objects(Property)
     * @since 1.3.0
     */
    public ExtendedIterator<RDFNode> listObjects(Property predicate) {
        return listProperties(predicate).mapWith(Statement::getObject);
    }

    @Override
    public OntGraphModelImpl getModel() {
        return (OntGraphModelImpl) enhGraph;
    }

    /**
     * Gets a public ont-object type identifier.
     *
     * @return Class, the actual type of this object
     */
    public Class<? extends OntObject> getActualClass() {
        return findActualClass(this);
    }

    /**
     * Finds a public {@link OntObject Ontology Object} class-type.
     *
     * @param o {@link OntObject}, not {@code null}
     * @return Class of the given {@link OntObject}
     */
    @SuppressWarnings("unchecked")
    public static Class<? extends OntObject> findActualClass(OntObject o) {
        return Arrays.stream(o.getClass().getInterfaces())
                .filter(OntObject.class::isAssignableFrom)
                .map(c -> (Class<? extends OntObject>) c)
                .findFirst().orElse(null);
    }

    @Override
    public String toString() {
        Class<? extends RDFNode> view = getActualClass();
        return view == null ? super.toString() : String.format("[%s]%s", viewAsString(view), asNode());
    }

}
