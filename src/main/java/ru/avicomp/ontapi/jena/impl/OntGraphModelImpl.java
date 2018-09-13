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

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.*;
import org.apache.jena.rdf.model.impl.InfModelImpl;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.util.iterator.WrappedIterator;
import ru.avicomp.ontapi.jena.OntJenaException;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.impl.conf.OntPersonality;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.utils.Graphs;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Base model ONT-API implementation to work through jena only.
 * This is an analogue of {@link org.apache.jena.ontology.impl.OntModelImpl} to work in accordance with OWL2 DL specification.
 * <p>
 * Created by @szuev on 27.10.2016.
 *
 * @see UnionGraph
 */
@SuppressWarnings({"WeakerAccess", "SameParameterValue"})
public class OntGraphModelImpl extends UnionModel implements OntGraphModel {

    /**
     * @param graph       {@link Graph}
     * @param personality {@link OntPersonality}
     */
    public OntGraphModelImpl(Graph graph, OntPersonality personality) {
        super(graph, OntJenaException.notNull(personality, "Null personality"));
    }

    /**
     * Synchronizes imports with graph hierarchy.
     * Underling graph tree may content named graphs which are not included to {@code owl:imports}.
     * This method tries to fix such situation by modifying base graph.
     */
    public void syncImports() {
        syncImports(getPersonality());
    }

    /**
     * Synchronizes import declarations with the graph hierarchy.
     *
     * @param personality {@link OntPersonality}
     */
    protected void syncImports(OntPersonality personality) {
        OntID id = getID();
        id.removeAll(OWL.imports);
        imports(personality).map(OntGraphModel::getID).filter(Resource::isURIResource).map(Resource::getURI).forEach(id::addImport);
    }

    @Override
    public OntPersonality getPersonality() {
        return (OntPersonality) super.getPersonality();
    }

    @Override
    public OntID getID() {
        return getNodeAs(Graphs.ontologyNode(getBaseGraph())
                .orElseGet(() -> createResource().addProperty(RDF.type, OWL.Ontology).asNode()), OntID.class);
    }

    @Override
    public OntID setID(String uri) {
        return getNodeAs(createOntologyID(getBaseModel(), uri).asNode(), OntID.class);
    }

    /**
     * Creates a fresh ontology resource (i.e. {@code @uri rdf:type owl:Ontology} triple)
     * and moves to it all content from existing ontology resources (if they present).
     *
     * @param model {@link Model} graph holder
     * @param uri   String an ontology iri, null for anonymous ontology
     * @return {@link Resource} in model
     * @throws OntJenaException if creation is not possible by some reason.
     */
    public static Resource createOntologyID(Model model, String uri) throws OntJenaException {
        return createOntologyID(model, uri == null ? NodeFactory.createBlankNode() : NodeFactory.createURI(uri));
    }

    /**
     * Creates a fresh ontology resource from the given {@link Node}
     * and moves to it all content from existing ontology resources (if they present).
     *
     * @param model {@link Model} graph holder, not {@code null}
     * @param node  {@link Node}, must be either uri or blank, not {@code null}
     * @return {@link Resource} in model
     * @throws OntJenaException         in case the given node is uri and it takes part in {@code owl:imports}
     * @throws IllegalArgumentException in case the given node is not uri or blank (i.e. literal)
     */
    public static Resource createOntologyID(Model model, Node node) throws OntJenaException, IllegalArgumentException {
        if (!Objects.requireNonNull(node, "Null node").isURI() && !node.isBlank())
            throw new IllegalArgumentException("Expected uri or blank node: " + node);
        List<Statement> prev = Iter.flatMap(model.listStatements(null, RDF.type, OWL.Ontology),
                s -> s.getSubject().listProperties()).toList();
        if (prev.stream()
                .filter(s -> OWL.imports.equals(s.getPredicate()))
                .map(Statement::getObject)
                .filter(RDFNode::isURIResource)
                .map(RDFNode::asNode).anyMatch(node::equals)) {
            throw new OntJenaException("Can't create ontology: specified uri (<" + node + ">) is present in the imports.");
        }
        model.remove(prev);
        Resource res = model.wrapAsResource(node).addProperty(RDF.type, OWL.Ontology);
        prev.forEach(s -> res.addProperty(s.getPredicate(), s.getObject()));
        return res;
    }

    @Override
    public OntGraphModelImpl addImport(OntGraphModel m) {
        if (OntJenaException.notNull(m, "Null model specified.").getID().isAnon()) {
            throw new OntJenaException("Anonymous sub models are not allowed.");
        }
        if (hasOntologyImport(m)) {
            throw new OntJenaException("Ontology <" + m.getID().getURI() + "> is already in imports.");
        }
        getGraph().addGraph(m.getGraph());
        getID().addImport(m.getID().getURI());
        return this;
    }

    /**
     * Answers iff there is a graph with URI same as in the specified ontology.
     *
     * @param other {@link OntGraphModel}
     * @return boolean
     */
    public boolean hasOntologyImport(OntGraphModel other) {
        return hasOntologyImport(other.getID().getURI());
    }

    /**
     * Answers iff there is a graph with the specified URI.
     *
     * @param uri String
     * @return boolean
     */
    public boolean hasOntologyImport(String uri) {
        return uri != null && imports().map(OntGraphModel::getID).map(Resource::getURI).anyMatch(uri::equals);
    }

    @Override
    public OntGraphModelImpl removeImport(OntGraphModel m) {
        return removeFirst(x -> Graphs.isSameBase(x.getGraph(), m.getGraph()));
    }

    @Override
    public OntGraphModelImpl removeImport(String uri) {
        return removeFirst(x -> Objects.equals(uri, x.getID().getURI()));
    }

    protected OntGraphModelImpl removeFirst(Predicate<OntGraphModel> filter) {
        imports().filter(filter)
                .findFirst()
                .ifPresent(this::removeModel);
        return this;
    }

    protected void removeModel(OntGraphModel m) {
        getGraph().removeGraph(m.getGraph());
        getID().removeImport(m.getID().getURI());
    }

    @Override
    public Stream<OntGraphModel> imports() {
        return imports(getPersonality());
    }

    public Stream<OntGraphModel> imports(OntPersonality personality) {
        return getGraph().getUnderlying().graphs().map(g -> new OntGraphModelImpl(g, personality));
    }

    /**
     * Gets the top-level {@link OntGraphModelImpl Ontology Graph Model impl}.
     * This model may contain import declarations, but cannot contain sub-models.
     *
     * @return {@link OntGraphModelImpl}
     * @see #getBaseModel()
     * @since 1.3.0
     */
    public OntGraphModelImpl getTopModel() {
        if (getGraph().getUnderlying().hasSubGraphs()) {
            return new OntGraphModelImpl(getBaseGraph(), getPersonality());
        }
        return this;
    }

    @Override
    public InfModel getInferenceModel(Reasoner reasoner) {
        return new InfModelImpl(OntJenaException.notNull(reasoner, "Null reasoner.").bind(getGraph()));
    }

    /**
     * Retrieves the stream of {@link OntObject Ontology Object}s.
     * The result object will be cached inside model.
     *
     * @param type {@link Class} the type of {@link OntObject}, not null
     * @param <O>  subtype of {@link OntObject}
     * @return Stream of {@link OntObject}s
     */
    @Override
    public <O extends OntObject> Stream<O> ontObjects(Class<? extends O> type) {
        return Iter.asStream(listOntObjects(type));
    }

    /**
     * Lists all {@link OntObject Ontology Object}s and caches them inside this model.
     *
     * @param type {@link Class} the type of {@link OntObject}, not null
     * @param <O>  subtype of {@link OntObject}
     * @return {@link ExtendedIterator Extended Iterator} of {@link OntObject}s
     */
    public <O extends OntObject> ExtendedIterator<O> listOntObjects(Class<? extends O> type) {
        return listOntObjects(this, type);
    }

    /**
     * The same as {@link #listOntObjects(Class)}, but for the base graph.
     *
     * @param type {@link Class} the type of {@link OntObject}, not null
     * @param <O>  subtype of {@link OntObject}
     * @return {@link ExtendedIterator Extended Iterator} of {@link OntObject}s
     */
    public <O extends OntObject> ExtendedIterator<O> listLocalOntObjects(Class<? extends O> type) {
        return listOntObjects(getTopModel(), type);
    }

    /**
     * Lists all {@code OntObject}s for the given {@code OntGraphModelImpl}.
     *
     * @param m    {@link OntGraphModelImpl} the impl to cache
     * @param type {@link Class} the type of {@link OntObject}, not null
     * @param <O>  subtype of {@link OntObject}
     * @return {@link ExtendedIterator Extended Iterator} of {@link OntObject}s
     */
    public static <O extends OntObject> ExtendedIterator<O> listOntObjects(OntGraphModelImpl m, Class<? extends O> type) {
        return m.getPersonality().getOntImplementation(type).iterator(m).mapWith(e -> m.getNodeAs(e.asNode(), type));
    }

    @Override
    public Stream<OntEntity> ontEntities() {
        /*return Iter.asStream(listSubjectsWithProperty(RDF.type))
                .filter(RDFNode::isURIResource)
                .flatMap(r -> OntEntity.entityTypes().map(t -> getOntEntity(t, r)).filter(Objects::nonNull));*/
        // this looks faster:
        return Iter.asStream(listOntEntities());
    }

    /**
     * Lists all Ontology Entities.
     * Built-ins are not included.
     *
     * @return {@link ExtendedIterator Extended Iterator} of {@link OntEntity}s
     * @see #listLocalOntEntities()
     * @see OntEntity#listEntityTypes()
     */
    @SuppressWarnings("unchecked")
    public ExtendedIterator<OntEntity> listOntEntities() {
        return Iter.flatMap(OntEntity.listEntityTypes(), this::listOntObjects);
    }

    /**
     * The same as {@link #listOntEntities()} but for the base graph.
     *
     * @return {@link ExtendedIterator Extended Iterator} of {@link OntEntity}s
     * @see #listOntEntities()
     */
    @SuppressWarnings("unchecked")
    public ExtendedIterator<OntEntity> listLocalOntEntities() {
        return Iter.flatMap(OntEntity.listEntityTypes(), this::listLocalOntObjects);
    }

    /**
     * Gets 'punnings', i.e. the {@link OntEntity}s which have not only single type.
     *
     * @param withImports if false takes into account only base model
     * @return Stream of {@link OntEntity}s.
     */
    public Stream<OntEntity> ambiguousEntities(boolean withImports) {
        Set<Class<? extends OntEntity>> types = OntEntity.listEntityTypes().toSet();
        return ontEntities().filter(e -> withImports || e.isLocal()).filter(e -> types.stream()
                .filter(view -> e.canAs(view) && (withImports || e.as(view).isLocal())).count() > 1);
    }

    @Override
    public Stream<OntIndividual> classAssertions() {
        return statements(null, RDF.type, null)
                .filter(s -> s.getObject().canAs(OntCE.class))
                .map(OntStatement::getSubject)
                .map(s -> findNodeAs(s.asNode(), OntIndividual.class))
                .filter(Objects::nonNull);
    }

    @Override
    public <E extends OntEntity> E getOntEntity(Class<E> type, String uri) {
        return findNodeAs(NodeFactory.createURI(OntJenaException.notNull(uri, "Null uri.")), type);
    }

    @Override
    public <T extends OntEntity> T createOntEntity(Class<T> type, String iri) {
        try {
            return createOntObject(type, iri);
        } catch (OntJenaException.Creation e) { // illegal punning:
            throw new OntJenaException(String.format("Can't add entity [%s: %s]: perhaps it's illegal punning.", type.getSimpleName(), iri), e);
        }
    }

    /**
     * Creates and caches an ontology object resource by the given type and uri.
     *
     * @param type Class, object type
     * @param uri  String, URI (IRI), can be {@code null} for anonymous resource
     * @param <T>  class-type of {@link OntObject}
     * @return {@link OntObject}, new instance
     */
    public <T extends OntObject> T createOntObject(Class<T> type, String uri) {
        Node key = Graphs.createNode(uri);
        T res = getPersonality().getOntImplementation(type).create(key, this).as(type);
        getNodeCache().put(key, res);
        return res;
    }

    @Override
    public OntGraphModelImpl removeOntObject(OntObject obj) {
        obj.clearAnnotations().content()
                .peek(OntStatement::clearAnnotations)
                .collect(Collectors.toSet()).forEach(this::remove);
        getNodeCache().remove(obj.asNode());
        return this;
    }

    @Override
    public OntGraphModelImpl removeOntStatement(OntStatement statement) {
        statement.clearAnnotations();
        remove(statement);
        return this;
    }

    @Override
    public Stream<OntStatement> statements() {
        return Iter.asStream(listStatements().mapWith(OntStatement.class::cast));
    }

    @Override
    public Stream<OntStatement> statements(Resource s, Property p, RDFNode o) {
        return Iter.asStream(listOntStatements(s, p, o));
    }

    @Override
    public Stream<OntStatement> localStatements(Resource s, Property p, RDFNode o) {
        return Iter.asStream(listLocalStatements(s, p, o));
    }

    /**
     * {@inheritDoc}
     *
     * @param s {@link Resource} the subject sought, can be {@code null}
     * @param p {@link Property} the predicate sought, can be {@code null}
     * @param o {@link RDFNode} the object sought, can be {@code null}
     * @return {@link StmtIterator} of {@link OntStatement}s
     */
    @Override
    public StmtIterator listStatements(Resource s, Property p, RDFNode o) {
        return Iter.createStmtIterator(getGraph().find(asNode(s), asNode(p), asNode(o)), this::asStatement);
    }

    /**
     * Returns an {@link ExtendedIterator extended iterator} over all the statements in the model that match a pattern.
     * The statements selected are those whose subject matches the {@code s} argument,
     * whose predicate matches the {@code p} argument
     * and whose object matches the {@code o} argument.
     * If an argument is {@code null} it matches anything.
     * The method is equivalent to the expression {@code listStatements(s, p, o).mapWith(OntStatement.class::cast)}.
     *
     * @param s {@link Resource} the subject sought, can be {@code null}
     * @param p {@link Property} the predicate sought, can be {@code null}
     * @param o {@link RDFNode} the object sought, can be {@code null}
     * @return {@link ExtendedIterator} of {@link OntStatement}s
     * @see #listStatements(Resource, Property, RDFNode)
     * @since 1.3.0
     */
    public ExtendedIterator<OntStatement> listOntStatements(Resource s, Property p, RDFNode o) {
        return WrappedIterator.create(getGraph().find(asNode(s), asNode(p), asNode(o)).mapWith(this::asStatement));
    }

    /**
     * Lists all statements in the <b>base</b> model that match a pattern
     * in the form of {@link ExtendedIterator Extended Iterator}.
     * The method is equivalent to the expression
     * {@code listStatements(s, p, o).mapWith(OntStatement.class::cast).filterKeep(OntStatement::isLocal)}.
     *
     * @param s {@link Resource} the subject sought, can be {@code null}
     * @param p {@link Property} the predicate sought, can be {@code null}
     * @param o {@link RDFNode} the object sought, can be {@code null}
     * @return {@link ExtendedIterator} of {@link OntStatement}s, which are local to the base graph
     * @see #listStatements(Resource, Property, RDFNode)
     * @since 1.3.0
     */
    public ExtendedIterator<OntStatement> listLocalStatements(Resource s, Property p, RDFNode o) {
        return WrappedIterator.create(getBaseGraph().find(asNode(s), asNode(p), asNode(o)).mapWith(this::asStatement));
    }

    @Override
    public OntStatementImpl createStatement(Resource s, Property p, RDFNode o) {
        return OntStatementImpl.createOntStatementImpl(s, p, o, this);
    }

    @Override
    public OntStatement asStatement(Triple triple) {
        return OntStatementImpl.createOntStatementImpl(triple, this);
    }

    @Override
    public OntDisjoint.Classes createDisjointClasses(Collection<OntCE> classes) {
        return OntDisjointImpl.createDisjointClasses(this, classes.stream());
    }

    @Override
    public OntDisjoint.Individuals createDifferentIndividuals(Collection<OntIndividual> individuals) {
        return OntDisjointImpl.createDifferentIndividuals(this, individuals.stream());
    }

    @Override
    public OntDisjoint.ObjectProperties createDisjointObjectProperties(Collection<OntOPE> properties) {
        return OntDisjointImpl.createDisjointObjectProperties(this, properties.stream());
    }

    @Override
    public OntDisjoint.DataProperties createDisjointDataProperties(Collection<OntNDP> properties) {
        return OntDisjointImpl.createDisjointDataProperties(this, properties.stream());
    }

    @Override
    public <T extends OntFR> T createFacetRestriction(Class<T> view, Literal literal) {
        return OntFRImpl.create(this, view, literal);
    }

    @Override
    public OntDR.OneOf createOneOfDataRange(Collection<Literal> values) {
        return OntDRImpl.createOneOf(this, values.stream());
    }

    @Override
    public OntDR.Restriction createRestrictionDataRange(OntDT datatype, Collection<OntFR> values) {
        return OntDRImpl.createRestriction(this, datatype, values.stream());
    }

    @Override
    public OntDR.ComplementOf createComplementOfDataRange(OntDR other) {
        return OntDRImpl.createComplementOf(this, other);
    }

    @Override
    public OntDR.UnionOf createUnionOfDataRange(Collection<OntDR> values) {
        return OntDRImpl.createUnionOf(this, values.stream());
    }

    @Override
    public OntDR.IntersectionOf createIntersectionOfDataRange(Collection<OntDR> values) {
        return OntDRImpl.createIntersectionOf(this, values.stream());
    }

    @Override
    public OntCE.ObjectSomeValuesFrom createObjectSomeValuesFrom(OntOPE onProperty, OntCE other) {
        return OntCEImpl.createComponentRestrictionCE(this, OntCE.ObjectSomeValuesFrom.class, onProperty, other, OWL.someValuesFrom);
    }

    @Override
    public OntCE.DataSomeValuesFrom createDataSomeValuesFrom(OntNDP onProperty, OntDR other) {
        return OntCEImpl.createComponentRestrictionCE(this, OntCE.DataSomeValuesFrom.class, onProperty, other, OWL.someValuesFrom);
    }

    @Override
    public OntCE.ObjectAllValuesFrom createObjectAllValuesFrom(OntOPE onProperty, OntCE other) {
        return OntCEImpl.createComponentRestrictionCE(this, OntCE.ObjectAllValuesFrom.class, onProperty, other, OWL.allValuesFrom);
    }

    @Override
    public OntCE.DataAllValuesFrom createDataAllValuesFrom(OntNDP onProperty, OntDR other) {
        return OntCEImpl.createComponentRestrictionCE(this, OntCE.DataAllValuesFrom.class, onProperty, other, OWL.allValuesFrom);
    }

    @Override
    public OntCE.ObjectHasValue createObjectHasValue(OntOPE onProperty, OntIndividual other) {
        return OntCEImpl.createComponentRestrictionCE(this, OntCE.ObjectHasValue.class, onProperty, other, OWL.hasValue);
    }

    @Override
    public OntCE.DataHasValue createDataHasValue(OntNDP onProperty, Literal other) {
        return OntCEImpl.createComponentRestrictionCE(this, OntCE.DataHasValue.class, onProperty, other, OWL.hasValue);
    }

    @Override
    public OntCE.ObjectMinCardinality createObjectMinCardinality(OntOPE onProperty, int cardinality, OntCE onObject) {
        return OntCEImpl.createCardinalityRestrictionCE(this, OntCE.ObjectMinCardinality.class, onProperty, cardinality, onObject);
    }

    @Override
    public OntCE.DataMinCardinality createDataMinCardinality(OntNDP onProperty, int cardinality, OntDR onObject) {
        return OntCEImpl.createCardinalityRestrictionCE(this, OntCE.DataMinCardinality.class, onProperty, cardinality, onObject);
    }

    @Override
    public OntCE.ObjectMaxCardinality createObjectMaxCardinality(OntOPE onProperty, int cardinality, OntCE onObject) {
        return OntCEImpl.createCardinalityRestrictionCE(this, OntCE.ObjectMaxCardinality.class, onProperty, cardinality, onObject);
    }

    @Override
    public OntCE.DataMaxCardinality createDataMaxCardinality(OntNDP onProperty, int cardinality, OntDR onObject) {
        return OntCEImpl.createCardinalityRestrictionCE(this, OntCE.DataMaxCardinality.class, onProperty, cardinality, onObject);
    }

    @Override
    public OntCE.ObjectCardinality createObjectCardinality(OntOPE onProperty, int cardinality, OntCE onObject) {
        return OntCEImpl.createCardinalityRestrictionCE(this, OntCE.ObjectCardinality.class, onProperty, cardinality, onObject);
    }

    @Override
    public OntCE.DataCardinality createDataCardinality(OntNDP onProperty, int cardinality, OntDR onObject) {
        return OntCEImpl.createCardinalityRestrictionCE(this, OntCE.DataCardinality.class, onProperty, cardinality, onObject);
    }

    @Override
    public OntCE.UnionOf createUnionOf(Collection<OntCE> classes) {
        return OntCEImpl.createComponentsCE(this, OntCE.UnionOf.class, OWL.unionOf, classes.iterator());
    }

    @Override
    public OntCE.IntersectionOf createIntersectionOf(Collection<OntCE> classes) {
        return OntCEImpl.createComponentsCE(this, OntCE.IntersectionOf.class, OWL.intersectionOf, classes.iterator());
    }

    @Override
    public OntCE.OneOf createOneOf(Collection<OntIndividual> individuals) {
        return OntCEImpl.createComponentsCE(this, OntCE.OneOf.class, OWL.oneOf, individuals.iterator());
    }

    @Override
    public OntCE.HasSelf createHasSelf(OntOPE onProperty) {
        return OntCEImpl.createHasSelf(this, onProperty);
    }

    @Override
    public OntCE.NaryDataAllValuesFrom createDataAllValuesFrom(Collection<OntNDP> onProperties, OntDR other) {
        //todo
        throw new OntJenaException.Unsupported("TODO: " + OntCE.NaryDataAllValuesFrom.class);
    }

    @Override
    public OntCE.NaryDataSomeValuesFrom createDataSomeValuesFrom(Collection<OntNDP> onProperties, OntDR other) {
        //todo
        throw new OntJenaException.Unsupported("TODO: " + OntCE.NaryDataSomeValuesFrom.class);
    }

    @Override
    public OntCE.ComplementOf createComplementOf(OntCE other) {
        return OntCEImpl.createComplementOf(this, other);
    }

    @Override
    public OntSWRL.Variable createSWRLVariable(String uri) {
        return OntSWRLImpl.createVariable(this, uri);
    }

    @Override
    public OntSWRL.Atom.BuiltIn createBuiltInSWRLAtom(Resource predicate, Collection<OntSWRL.DArg> arguments) {
        return OntSWRLImpl.createBuiltInAtom(this, predicate, arguments);
    }

    @Override
    public OntSWRL.Atom.OntClass createClassSWRLAtom(OntCE clazz, OntSWRL.IArg arg) {
        return OntSWRLImpl.createClassAtom(this, clazz, arg);
    }

    @Override
    public OntSWRL.Atom.DataRange createDataRangeSWRLAtom(OntDR range, OntSWRL.DArg arg) {
        return OntSWRLImpl.createDataRangeAtom(this, range, arg);
    }

    @Override
    public OntSWRL.Atom.DataProperty createDataPropertySWRLAtom(OntNDP dataProperty, OntSWRL.IArg firstArg, OntSWRL.DArg secondArg) {
        return OntSWRLImpl.createDataPropertyAtom(this, dataProperty, firstArg, secondArg);
    }

    @Override
    public OntSWRL.Atom.ObjectProperty createObjectPropertySWRLAtom(OntOPE dataProperty, OntSWRL.IArg firstArg, OntSWRL.IArg secondArg) {
        return OntSWRLImpl.createObjectPropertyAtom(this, dataProperty, firstArg, secondArg);
    }

    @Override
    public OntSWRL.Atom.DifferentIndividuals createDifferentIndividualsSWRLAtom(OntSWRL.IArg firstArg, OntSWRL.IArg secondArg) {
        return OntSWRLImpl.createDifferentIndividualsAtom(this, firstArg, secondArg);
    }

    @Override
    public OntSWRL.Atom.SameIndividuals createSameIndividualsSWRLAtom(OntSWRL.IArg firstArg, OntSWRL.IArg secondArg) {
        return OntSWRLImpl.createSameIndividualsAtom(this, firstArg, secondArg);
    }

    @Override
    public OntSWRL.Imp createSWRLImp(Collection<OntSWRL.Atom> head, Collection<OntSWRL.Atom> body) {
        return OntSWRLImpl.createImp(this, head, body);
    }

    public PrefixMapping getPrefixMapping() {
        return getGraph().getPrefixMapping();
    }

    @Override
    public OntGraphModelImpl setNsPrefix(String prefix, String uri) {
        getPrefixMapping().setNsPrefix(prefix, uri);
        return this;
    }

    @Override
    public OntGraphModelImpl removeNsPrefix(String prefix) {
        getPrefixMapping().removeNsPrefix(prefix);
        return this;
    }

    @Override
    public OntGraphModelImpl clearNsPrefixMap() {
        getPrefixMapping().clearNsPrefixMap();
        return this;
    }

    @Override
    public OntGraphModelImpl setNsPrefixes(PrefixMapping pm) {
        getPrefixMapping().setNsPrefixes(pm);
        return this;
    }

    @Override
    public OntGraphModelImpl setNsPrefixes(Map<String, String> map) {
        getPrefixMapping().setNsPrefixes(map);
        return this;
    }

    @Override
    public OntGraphModelImpl withDefaultMappings(PrefixMapping other) {
        getPrefixMapping().withDefaultMappings(other);
        return this;
    }

    @Override
    public String toString() {
        return String.format("OntGraphModel{%s}", getID());
    }

}
