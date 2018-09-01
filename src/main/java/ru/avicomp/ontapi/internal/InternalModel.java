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

package ru.avicomp.ontapi.internal;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.JenaException;
import org.apache.jena.shared.Lock;
import org.apache.jena.sparql.util.graph.GraphListenerBase;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDFS;
import org.semanticweb.owlapi.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.ontapi.DataFactory;
import ru.avicomp.ontapi.OntApiException;
import ru.avicomp.ontapi.OwlObjects;
import ru.avicomp.ontapi.jena.OntJenaException;
import ru.avicomp.ontapi.jena.RWLockedGraph;
import ru.avicomp.ontapi.jena.impl.OntGraphModelImpl;
import ru.avicomp.ontapi.jena.impl.conf.OntPersonality;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.vocabulary.OWL;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A Buffer Graph OWL model, which supports both listing OWL-API objects (OWL Axioms, Entities and Annotations)
 * and Jena interfaces (through the {@link OntGraphModel} view of RDF Graph).
 * <p>
 * It is an analogue of <a href='https://github.com/owlcs/owlapi/blob/version5/impl/src/main/java/uk/ac/manchester/cs/owl/owlapi/Internals.java'>uk.ac.manchester.cs.owl.owlapi.Internals</a>.
 * This model is used by the facade model (i.e. by {@link ru.avicomp.ontapi.OntologyModel}) while reading and writing
 * the structural (axiomatic) representation of ontology.
 * <p>
 * Notice that this model is a non-serializable (while everything in OWL-API is serializable),
 * since it is part of ONT-API internal implementation.
 * <p>
 * Unlike native OWL-API (see {@code owl-api-impl}) implementation,
 * this model does not store all possible {@link OWLObject OWL object}s in memory.
 * Instead, there are several caches.
 * The main cache is divided into buckets by OWL objects types (39 OWL Axioms types plus Header Annotations).
 * And each of these buckets is indivisible.
 * It means that only the whole bucket can be loaded or invalidated, not just part of it.
 * This is the major restriction of the current implementation:
 * if you have such a large ontology that can not even be partially placed in memory
 * (that is, the set of axioms of any type, but with the exclusion of their components, exceeds the memory limit),
 * then this solution won't work.
 * To work with such large ontologies, it is recommended to use {@link AxiomParserProvider#get(AxiomType)} directly.
 * <p>
 * TODO: Should it return {@link ONTObject}s, not just naked {@link OWLObject}s? It seems it would be very convenient and could make this class useful not only as part of inner implementation.
 * <p>
 * Created by @szuev on 26.10.2016.
 */
@SuppressWarnings({"WeakerAccess"})
public class InternalModel extends OntGraphModelImpl implements OntGraphModel {
    private static final Logger LOGGER = LoggerFactory.getLogger(InternalModel.class);

    // Configuration settings
    private final InternalConfig config;
    // The main axioms & header annotations cache.
    // Used to work through OWL-API interfaces. The use of jena model methods must clear this cache.
    protected LoadingCache<Class<? extends OWLObject>, ObjectTriplesMap<? extends OWLObject>> components =
            Caffeine.newBuilder().softValues().build(this::readObjectTriples);

    // Temporary cache for collecting axioms, should be reset after axioms getting.
    protected final InternalDataFactory cacheDataFactory;
    // OWL objects store to improve performance (working with OWL-API 'signature' methods)
    // Any change in the graph must reset these caches.
    // TODO: better to remove this cache at all (replace with cacheDataFactory)
    protected LoadingCache<Class<? extends OWLObject>, Set<? extends OWLObject>> objects =
            Caffeine.newBuilder().softValues().build(this::readOWLObjects);

    /**
     * Creates an RDF Graph Buffer Model instance.
     * For internal usage only.
     *
     * @param base        {@link Graph}
     * @param personality {@link OntPersonality}
     * @param factory     {@link DataFactory}
     * @param config      {@link InternalConfig}
     */
    public InternalModel(Graph base, OntPersonality personality, DataFactory factory, InternalConfig config) {
        super(base, personality);
        this.config = config;
        this.cacheDataFactory = new CacheDataFactory(factory);
        //new NoCacheDataFactory(config);
        //new MapDataFactory(config);
        getGraph().getEventManager().register(new DirectListener());
    }

    /**
     * Returns the model config instance.
     *
     * @return {@link InternalConfig}
     */
    public InternalConfig getConfig() {
        return config;
    }

    public InternalDataFactory getDataFactory() {
        return cacheDataFactory;
    }

    /**
     * Jena model method.
     * Since in ONT-API we use another kind of lock this method is disabled (i.e. R/W Lock inside manager).
     *
     * @see RWLockedGraph
     */
    @Override
    public Lock getLock() {
        throw new OntApiException.Unsupported();
    }

    /**
     * Jena model method to work with embedded lock-mechanism.
     * Disabled since in OWL-API there is a different approach (i.e. R/W Lock inside manager).
     *
     * @see #getLock()
     */
    @Override
    public void enterCriticalSection(boolean requestReadLock) {
        throw new OntApiException.Unsupported();
    }

    /**
     * Jena model method to work with embedded lock-mechanism.
     * Disabled since in the OWL-API there is a different approach (i.e. R/W Lock inside manager).
     *
     * @see #getLock()
     */
    @Override
    public void leaveCriticalSection() {
        throw new OntApiException.Unsupported();
    }

    /**
     * Lists all owl import-declarations.
     *
     * @return Stream of {@link OWLImportsDeclaration}s
     */
    public Stream<OWLImportsDeclaration> listOWLImportDeclarations() {
        return getID().imports().map(cacheDataFactory::toIRI)
                .map(i -> cacheDataFactory.getOWLDataFactory().getOWLImportsDeclaration(i));
    }

    /**
     * Answers {@code true} if the ontology is empty (in the axiomatic point of view).
     *
     * @return {@code true}  if ontology does not contain any axioms and annotations,
     * the encapsulated graph still may contain some triples.
     */
    public boolean isOntologyEmpty() {
        return listOWLAxioms().count() == 0 && listOWLAnnotations().count() == 0;
    }

    /**
     * Lists {@link OWLEntity OWL Entity} for the specified IRI.
     *
     * @param iri {@link IRI}
     * @return List of {@link OWLEntity}s.
     */
    public Stream<OWLEntity> listOWLEntities(IRI iri) {
        if (iri == null) return Stream.empty();
        OntEntity e = getOntEntity(OntEntity.class, iri.getIRIString());
        List<ONTObject<? extends OWLEntity>> res = new ArrayList<>();
        if (e.canAs(OntClass.class)) {
            res.add(cacheDataFactory.get(e.as(OntClass.class)));
        }
        if (e.canAs(OntDT.class)) {
            res.add(cacheDataFactory.get(e.as(OntDT.class)));
        }
        if (e.canAs(OntNAP.class)) {
            res.add(cacheDataFactory.get(e.as(OntNAP.class)));
        }
        if (e.canAs(OntNDP.class)) {
            res.add(cacheDataFactory.get(e.as(OntNDP.class)));
        }
        if (e.canAs(OntNOP.class)) {
            res.add(cacheDataFactory.get(e.as(OntNOP.class)));
        }
        if (e.canAs(OntIndividual.Named.class)) {
            res.add(cacheDataFactory.get(e.as(OntIndividual.Named.class)));
        }
        return res.stream().map(ONTObject::getObject);
    }

    /**
     * Lists all anonymous individuals in the form of OWL-API objects.
     *
     * @return Stream of {@link OWLAnonymousIndividual}s
     */
    public Stream<OWLAnonymousIndividual> listOWLAnonymousIndividuals() {
        return listOWLObjects(OWLAnonymousIndividual.class);
    }

    /**
     * Lists all named individuals in the form of OWL-API objects.
     *
     * @return Stream of {@link OWLNamedIndividual}s
     */
    public Stream<OWLNamedIndividual> listOWLNamedIndividuals() {
        return listOWLObjects(OWLNamedIndividual.class);
    }

    /**
     * Lists all OWL classes in the form of OWL-API objects.
     *
     * @return Stream of {@link OWLClass}es.
     */
    public Stream<OWLClass> listOWLClasses() {
        return listOWLObjects(OWLClass.class);
    }

    /**
     * Lists all data properties in the form of OWL-API objects.
     *
     * @return Stream of {@link OWLDataProperty}s
     */
    public Stream<OWLDataProperty> listOWLDataProperties() {
        return listOWLObjects(OWLDataProperty.class);
    }

    /**
     * Lists all object properties in the form of OWL-API objects.
     *
     * @return Stream of {@link OWLObjectProperty}s
     */
    public Stream<OWLObjectProperty> listOWLObjectProperties() {
        return listOWLObjects(OWLObjectProperty.class);
    }

    /**
     * Lists all annotation properties in the form of OWL-API objects.
     *
     * @return Stream of {@link OWLAnnotationProperty}s
     */
    public Stream<OWLAnnotationProperty> listOWLAnnotationProperties() {
        return listOWLObjects(OWLAnnotationProperty.class);
    }

    /**
     * Lists all named data-ranges (i.e. datatypes) in the form of OWL-API objects.
     *
     * @return Stream of {@link OWLDatatype}s.
     */
    public Stream<OWLDatatype> listOWLDatatypes() {
        return listOWLObjects(OWLDatatype.class);
    }

    /**
     * Lists all OWL-objects of the specified class-type from the axioms and annotations cache-collections.
     *
     * @param type Class type of owl-object.
     * @param <O>  type of owl-object
     * @return Stream of {@link OWLObject}s.
     */
    @SuppressWarnings("unchecked")
    protected <O extends OWLObject> Stream<O> listOWLObjects(Class<O> type) {
        return (Stream<O>) Objects.requireNonNull(objects.get(type), "Nothing found. Type: " + type).stream();
    }

    /**
     * Extracts the Set of OWL-objects of the specified class-type from the ontology header and axioms cache-collections.
     *
     * @param type Class type
     * @param <O>  subtype of {@link OWLObject}
     * @return Set of object
     */
    protected <O extends OWLObject> Set<O> readOWLObjects(Class<O> type) {
        return Stream.concat(
                listOWLAnnotations().flatMap(a -> OwlObjects.objects(type, a)),
                listOWLAxioms().flatMap(a -> OwlObjects.objects(type, a)))
                .collect(Collectors.toSet());
    }

    /**
     * Adds the given annotation to the ontology header of the model.
     *
     * @param annotation {@link OWLAnnotation}
     * @see #add(OWLAxiom)
     */
    public void add(OWLAnnotation annotation) {
        add(annotation, getAnnotationTripleStore(), a -> WriteHelper.addAnnotations(getID(), Stream.of(annotation)));
    }

    /**
     * Removes the given ontology header annotation from the model.
     *
     * @param annotation {@link OWLAnnotation}
     * @see #remove(OWLAxiom)
     */
    public void remove(OWLAnnotation annotation) {
        remove(annotation, getAnnotationTripleStore());
        // todo: there is no need to invalidate whole objects cache
        clearObjectsCaches();
    }

    /**
     * Gets all ontology header annotations.
     *
     * @return Stream of {@link OWLAnnotation}
     * @see #listOWLAxioms(Set)
     */
    @SuppressWarnings("unchecked")
    public Stream<OWLAnnotation> listOWLAnnotations() {
        return getAnnotationTripleStore().objects();
    }

    /**
     * Lists {@link OWLDeclarationAxiom Declaration Axiom}s for the specified {@link OWLEntity entity}.
     *
     * @param e {@link OWLEntity}, not null
     * @return Stream of {@link OWLDeclarationAxiom}s
     */
    public Stream<OWLDeclarationAxiom> listOWLDeclarationAxioms(OWLEntity e) {
        InternalConfig conf = getConfig().snapshot();
        if (!conf.isAllowReadDeclarations()) return Stream.empty();
        // even there are no changes in OWLDeclarationAxioms, they can be affected by some other user-defined axiom,
        // so need check whole cache:
        if (hasManuallyAddedAxioms()) {
            return listOWLAxioms(OWLDeclarationAxiom.class).filter(a -> e.equals(a.getEntity()));
        }
        // in the case of a large ontology, the direct traverse over the graph works significantly faster:
        DeclarationTranslator t = (DeclarationTranslator) AxiomParserProvider.get(OWLDeclarationAxiom.class);
        OntEntity res = findNodeAs(WriteHelper.toResource(e).asNode(), WriteHelper.getEntityView(e));
        if (res == null) return Stream.empty();
        OntStatement s = res.getRoot();
        return s == null ? Stream.empty() : Stream.of(t.toAxiom(s, cacheDataFactory, conf).getObject());
    }

    /**
     * Lists {@link OWLAnnotationAssertionAxiom Annotation Assertion Axiom}s
     * with the given {@link OWLAnnotationSubject subject}.
     *
     * @param s {@link OWLAnnotationSubject}, not null
     * @return Stream of {@link OWLAnnotationAssertionAxiom}s
     */
    public Stream<OWLAnnotationAssertionAxiom> listOWLAnnotationAssertionAxioms(OWLAnnotationSubject s) {
        InternalConfig conf = getConfig().snapshot();
        if (!conf.isLoadAnnotationAxioms()) return Stream.empty();
        if (hasManuallyAddedAxioms()) {
            return listOWLAxioms(OWLAnnotationAssertionAxiom.class).filter(a -> s.equals(a.getSubject()));
        }
        AxiomTranslator<OWLAnnotationAssertionAxiom> t = AxiomParserProvider.get(OWLAnnotationAssertionAxiom.class);
        ExtendedIterator<OntStatement> res = listLocalStatements(WriteHelper.toResource(s), null, null)
                .filterKeep(x -> t.testStatement(x, conf));
        return Iter.asStream(t.translate(res, cacheDataFactory, conf).mapWith(ONTObject::getObject));
    }

    /**
     * Lists {@link OWLSubClassOfAxiom SubClassOf Axiom}s by the given sub {@link OWLClass class}.
     *
     * @param sub {@link OWLClass}, not null
     * @return Stream of {@link OWLSubClassOfAxiom}s
     */
    public Stream<OWLSubClassOfAxiom> listOWLSubClassOfAxioms(OWLClass sub) {
        if (hasManuallyAddedAxioms()) {
            return listOWLAxioms(OWLSubClassOfAxiom.class).filter(a -> Objects.equals(a.getSubClass(), sub));
        }
        SubClassOfTranslator t = (SubClassOfTranslator) AxiomParserProvider.get(OWLSubClassOfAxiom.class);
        ExtendedIterator<OntStatement> res = listLocalStatements(WriteHelper.toResource(sub), RDFS.subClassOf, null)
                .filterKeep(t::filter);
        InternalConfig conf = getConfig().snapshot();
        return Iter.asStream(t.translate(res, cacheDataFactory, conf).mapWith(ONTObject::getObject));
    }

    /**
     * Lists {@link OWLEquivalentClassesAxiom EquivalentClasses Axiom}s by the given {@link OWLClass class}-component.
     *
     * @param c {@link OWLClass}, not null
     * @return Stream of {@link OWLEquivalentClassesAxiom}s
     * @see AbstractNaryTranslator#axioms(OntGraphModel)
     */
    public Stream<OWLEquivalentClassesAxiom> listOWLEquivalentClassesAxioms(OWLClass c) {
        if (hasManuallyAddedAxioms()) {
            return listOWLAxioms(OWLEquivalentClassesAxiom.class).filter(a -> a.operands().anyMatch(c::equals));
        }
        EquivalentClassesTranslator t = (EquivalentClassesTranslator) AxiomParserProvider.get(OWLEquivalentClassesAxiom.class);
        Resource r = WriteHelper.toResource(c);
        InternalConfig conf = getConfig().snapshot();
        ExtendedIterator<OntStatement> res = listLocalStatements(r, OWL.equivalentClass, null)
                .andThen(listLocalStatements(null, OWL.equivalentClass, r))
                .filterKeep(s -> t.testStatement(s, conf));
        return Iter.asStream(t.translate(res, cacheDataFactory, conf).mapWith(ONTObject::getObject));
    }

    /**
     * Lists all axioms.
     *
     * @return Stream of {@link OWLAxiom}s
     */
    public Stream<OWLAxiom> listOWLAxioms() {
        return listOWLAxioms(AxiomType.AXIOM_TYPES);
    }

    /**
     * Lists axioms for the specified types.
     *
     * @param types Set of {@link AxiomType}s
     * @return Stream of {@link OWLAxiom}
     * @see #listOWLAnnotations()
     */
    public Stream<OWLAxiom> listOWLAxioms(Set<AxiomType<? extends OWLAxiom>> types) {
        return types.stream()
                .map(t -> getAxiomTripleStore(t.getActualClass()))
                .flatMap(ObjectTriplesMap::objects)
                .map(OWLAxiom.class::cast);
    }

    /**
     * Lists axioms of the given class-type.
     *
     * @param view Class
     * @param <A>  type of axiom
     * @return Stream of {@link OWLAxiom}s.
     */
    public <A extends OWLAxiom> Stream<A> listOWLAxioms(Class<A> view) {
        return listOWLAxioms(AxiomType.getTypeForClass(OntApiException.notNull(view, "Null axiom class type.")));
    }

    /**
     * Lists axioms of the given axiom-type.
     *
     * @param type {@link AxiomType}
     * @param <A>  type of axiom
     * @return Stream of {@link OWLAxiom}s.
     */
    @SuppressWarnings("unchecked")
    public <A extends OWLAxiom> Stream<A> listOWLAxioms(AxiomType<A> type) {
        return (Stream<A>) getAxiomTripleStore(type).objects();
    }

    /**
     * Adds the specified axiom to the model.
     *
     * @param axiom {@link OWLAxiom}
     * @see #add(OWLAnnotation)
     */
    public void add(OWLAxiom axiom) {
        add(axiom, getAxiomTripleStore(axiom.getAxiomType()), a -> AxiomParserProvider.getByType(a.getAxiomType())
                .write(a, InternalModel.this));
    }

    /**
     * Removes the given axiom from the model.
     * Also, clears the cache for the entity type, if the entity has been belonged to the removed axiom.
     *
     * @param axiom {@link OWLAxiom}
     * @see #remove(OWLAnnotation)
     */
    public void remove(OWLAxiom axiom) {
        remove(axiom, getAxiomTripleStore(axiom.getAxiomType()));
        Stream.of(OWLClass.class,
                OWLDatatype.class,
                OWLAnnotationProperty.class,
                OWLDataProperty.class,
                OWLObjectProperty.class,
                OWLNamedIndividual.class,
                OWLAnonymousIndividual.class)
                .filter(entityType -> OwlObjects.objects(entityType, axiom).findAny().isPresent())
                .forEach(type -> objects.invalidate(type));
    }

    /**
     * Answers {@code true} if the given axiom is present within this buffer-model.
     * It is equivalent to the expression {@code this.axioms().anyMatch(a::equals)}.
     *
     * @param a {@link OWLAxiom}, not {@code null}
     * @return {@code true} if axiom is present within model
     */
    public boolean contains(OWLAxiom a) {
        if (!getCacheMap().containsKey(a.getClass())) { // as a hack: make sure cache is initialized
            AxiomType.AXIOM_TYPES.forEach(t -> getAxiomTripleStore(t.getActualClass()));
        }
        return getAxiomTripleStore(a.getAxiomType()).contains(a);
    }

    /**
     * Loads (if needed) and returns a map of axioms of the specified {@link AxiomType OWLAxiom type}.
     * Auxiliary method.
     *
     * @param type {@link AxiomType}
     * @param <A>  {@link OWLAxiom}
     * @return {@link ObjectTriplesMap cache bucket} of {@link OWLAxiom}s of the given axiom-type
     */
    @SuppressWarnings("unchecked")
    protected <A extends OWLAxiom> ObjectTriplesMap<A> getAxiomTripleStore(AxiomType<? extends OWLAxiom> type) {
        return getAxiomTripleStore((Class<A>) type.getActualClass());
    }

    /**
     * Loads (if needed) and returns a map of axioms (i.e. cache bucket) of the specified OWLAxiom class-type.
     * Auxiliary method.
     *
     * @param type Class type of {@link OWLAxiom OWLAxiom}.
     * @param <A>  real type of OWLAxiom
     * @return {@link ObjectTriplesMap cache bucket} of {@link OWLAxiom}s of the given class-type
     */
    @SuppressWarnings("unchecked")
    protected <A extends OWLAxiom> ObjectTriplesMap<A> getAxiomTripleStore(Class<A> type) {
        return (ObjectTriplesMap<A>) components.get(type);
    }

    /**
     * Reads the OWL objects of the given class-type in the form of{@link ObjectTriplesMap internal object-triple map}.
     *
     * @param type Class type
     * @param <O>  {@link OWLAnnotation} or subtype of {@link OWLAxiom}
     * @return {@link ObjectTriplesMap cache bucket} of {@link OWLObject} of the given class-type
     */
    @SuppressWarnings("unchecked")
    protected <O extends OWLObject> ObjectTriplesMap<O> readObjectTriples(Class<? extends OWLObject> type) {
        ObjectTriplesMap<O> res;
        Instant start = null;
        if (LOGGER.isDebugEnabled()) {
            start = Instant.now();
        }
        if (OWLAnnotation.class.equals(type)) {
            res = (ObjectTriplesMap<O>) readAnnotationTriples();
        } else {
            res = (ObjectTriplesMap<O>) readAxiomTriples((Class<? extends OWLAxiom>) type);
        }
        if (start != null) {
            Duration d = Duration.between(start, Instant.now());
            // commons-lang3 is included in jena-arq (3.6.0)
            LOGGER.debug("[{}]{}:::{}s{}", getID(),
                    StringUtils.rightPad("[" + type.getSimpleName() + "]", 42),
                    String.format(Locale.ENGLISH, "%.3f", d.toMillis() / 1000.0),
                    res.size() != 0 ? "(" + res.size() + ")" : ""
            );
        }
        return res;
    }

    @Override
    public <N extends RDFNode> N fetchNodeAs(Node node, Class<N> view) {
        try {
            return super.fetchNodeAs(node, view);
        } catch (OntJenaException e) {
            if (!getConfig().isIgnoreAxiomsReadErrors()) {
                throw new OntApiException(e);
            }
            LOGGER.warn("Found a problem inside ontology <{}>: {}", getID(), e.getMessage());
            return null;
        }
    }

    /**
     * Reads the axioms of the specified class-type and their associated triples from the encapsulated graph in the form of
     * {@link ObjectTriplesMap iternal object-triple map} (i.e. cache bucket).
     *
     * @param type Class type
     * @param <A>  subtype of {@link OWLAxiom}
     * @return {@link ObjectTriplesMap cache bucket} of {@link OWLAxiom}s of the given class-type
     */
    protected <A extends OWLAxiom> ObjectTriplesMap<A> readAxiomTriples(Class<A> type) {
        return ObjectTriplesMap.create(type, AxiomParserProvider.get(type).axioms(InternalModel.this));
    }

    /**
     * Reads the ontology header from the encapsulated graph.
     *
     * @return {@link ObjectTriplesMap cache bucket} of ontology {@link OWLAnnotation annotation}s
     */
    protected ObjectTriplesMap<OWLAnnotation> readAnnotationTriples() {
        return ObjectTriplesMap.create(OWLAnnotation.class, ReadHelper.objectAnnotations(getID(), cacheDataFactory));
    }

    /**
     * Loads (if needed) and returns the triples-map of Ontology {@link OWLAnnotation OWL Annotation}s.
     * Auxiliary method.
     *
     * @return {@link ObjectTriplesMap cache bucket} of ontology {@link OWLAnnotation annotation}s.
     */
    @SuppressWarnings("unchecked")
    protected ObjectTriplesMap<OWLAnnotation> getAnnotationTripleStore() {
        return (ObjectTriplesMap<OWLAnnotation>) components.get(OWLAnnotation.class);
    }

    /**
     * Adds the OWL object to the model.
     *
     * @param object either {@link OWLAxiom} or {@link OWLAnnotation}
     * @param store  {@link ObjectTriplesMap}
     * @param writer {@link Consumer} to process writing.
     * @param <O>    type of owl-object
     */
    protected <O extends OWLObject> void add(O object, ObjectTriplesMap<O> store, Consumer<O> writer) {
        OwlObjectListener<O> listener = createListener(store, object);
        clearObjectsCaches();
        try {
            getGraph().getEventManager().register(listener);
            store.manual = true;
            writer.accept(object);
        } catch (Exception e) {
            throw new OntApiException(String.format("OWLObject: %s, message: %s", object, e.getMessage()), e);
        } finally {
            getGraph().getEventManager().unregister(listener);
        }
    }

    /**
     * Removes the {@code component} from the given {@link ObjectTriplesMap map} and the model.
     * Note: need also remove associated objects from the {@link #objects} cache!
     *
     * @param component either {@link OWLAxiom} or {@link OWLAnnotation}
     * @param map       {@link ObjectTriplesMap}
     * @param <O>       the type of OWLObject
     * @see #clearObjectsCaches()
     */
    protected <O extends OWLObject> void remove(O component, ObjectTriplesMap<O> map) {
        Set<Triple> triples = map.getTripleSet(component);
        map.remove(component);
        triples.stream().filter(t -> !containsTriple(t)).forEach(this::delete);
    }

    protected boolean containsTriple(Triple triple) {
        return getComponents().stream().anyMatch(c -> c.contains(triple));
    }

    /**
     * Deletes a triple from the base graph and clears the standard jena model cache for it.
     *
     * @param triple {@link Triple}
     */
    protected void delete(Triple triple) {
        getNodeCache().remove(triple.getSubject());
        getBaseGraph().delete(triple);
    }

    /**
     * The overridden base method.
     * Makes this ontology empty given its caches.
     *
     * @return {@link Model}
     */
    @Override
    public InternalModel removeAll() {
        clearCache();
        getNodeCache().clear();
        super.removeAll();
        return this;
    }

    /**
     * Clears the cache for the specified triple.
     * This method is called if work directly through jena model interface.
     *
     * @param triple {@link Triple}
     */
    protected void clearCacheOnDelete(Triple triple) {
        getComponents().stream()
                .filter(map -> findObjectsToInvalidate(map, triple).findAny().isPresent())
                .forEach(o -> components.invalidate(o.type()));
        // todo: there is no need to invalidate *whole* objects cache
        clearObjectsCaches();
    }

    protected static <O extends OWLObject> Stream<O> findObjectsToInvalidate(ObjectTriplesMap<O> map, Triple triple) {
        return map.objects().filter(o -> {
            try {
                Set<Triple> res = map.get(o); // the triple set is not expected to be null, but just in case there is checking for null also.
                return res == null || res.contains(triple);
            } catch (JenaException j) { // may occur in case previous operation broke object structure
                return true;
            }
        });
    }

    /**
     * Gets all cache buckets.
     *
     * @return Collection of {@link ObjectTriplesMap}s
     */
    protected Collection<ObjectTriplesMap<? extends OWLObject>> getComponents() {
        return getCacheMap().values();
    }

    /**
     * Invalidates the cache if needed.
     * <p>
     * The OWL-API serialization may not work correctly without explicit expansion of axioms into
     * a strictly defined form. The cache cleaning encourages repeated reading of the encapsulated graph,
     * and, thus, leads the axioms to a uniform view.
     * Without this operation, the axiomatic representation would look slightly different
     * and the reload test (loading/saving in different formats) would not passed.
     * Also, absence of uniformed axiomatic view may lead to exceptions,
     * since some of the OWL-storers require explicit declarations, which may not be present,
     * if the ontology was assembled manually.
     * It is important to invalidate whole the cache, since user-defined axioms may content parts of other axioms,
     * such as annotation assertions, declarations, data-range definitions, etc.
     */
    public void clearCacheIfNeeded() {
        if (hasManuallyAddedAxioms()) {
            clearCache();
        }
    }

    /**
     * Answers whether there are manually added axioms in the cache.
     * For optimization: if the graph has not been changed after reading,
     * then the cache is in a state strictly fixed by internal mechanisms, so there is no need to reset the cache.
     *
     * @return boolean
     */
    public boolean hasManuallyAddedAxioms() {
        return getCacheMap().values().stream().anyMatch(m -> m.manual);
    }

    /**
     * Gets objects cache as {@code Map}.
     *
     * @return Map, Class-types as keys, {@link ObjectTriplesMap}s as values
     */
    protected Map<Class<? extends OWLObject>, ObjectTriplesMap<? extends OWLObject>> getCacheMap() {
        return components.asMap();
    }

    /**
     * Invalidates {@link #objects} and {@link #cacheDataFactory} caches.
     * Auxiliary method.
     */
    protected void clearObjectsCaches() {
        objects.invalidateAll();
        cacheDataFactory.clear();
    }

    /**
     * Invalidates all caches.
     */
    public void clearCache() {
        components.invalidateAll();
        clearObjectsCaches();
    }

    @Override
    public String toString() {
        return String.format("[%s]%s", getClass().getSimpleName(), getID());
    }

    /**
     * An auxiliary class-container to provide a common way for working with {@link OWLObject}s and {@link Triple}s all together.
     * It is logically based on the {@link ONTObject} container,
     * which is a wrapper around {@link OWLObject OWLObject} with the reference to get all associated {@link Triple RDF triple}s.
     * This class is used by the {@link InternalModel internal model} cache as indivisible bucket.
     *
     * @param <O> Component type: a subtype of {@link OWLAxiom} or {@link OWLAnnotation}
     */
    public static class ObjectTriplesMap<O extends OWLObject> {
        protected final Class<O> type;
        protected final Map<O, ONTObject<O>> map;
        protected LoadingCache<O, Set<Triple>> triples = Caffeine.newBuilder().softValues().build(this::loadTripleSet);
        // a state flag that responds whether some axioms have been manually added to this map
        protected boolean manual;

        public ObjectTriplesMap(Class<O> type, Map<O, ONTObject<O>> map) {
            this.type = type;
            this.map = map;
        }

        /**
         * Creates an {@link ObjectTriplesMap} instance.
         *
         * @param type   Class-type
         * @param stream Stream of {@link ONTObject}s that contain {@link OWLObject}s and a way to get {@link Triple}s
         * @param <R>    any {@link OWLObject} subtype
         * @return {@link ObjectTriplesMap} instance for the given type
         */
        protected static <R extends OWLObject> ObjectTriplesMap<R> create(Class<R> type, Stream<ONTObject<R>> stream) {
            return new ObjectTriplesMap<>(type, stream.collect(Collectors.toMap(ONTObject::getObject,
                    Function.identity(),
                    ONTObject::append, HashMap::new)));
        }

        public Class<O> type() {
            return type;
        }

        private Optional<ONTObject<O>> find(O key) {
            return Optional.ofNullable(map.get(key));
        }

        @Nonnull
        private Set<Triple> loadTripleSet(O key) {
            return find(key).map(s -> s.triples()
                    .collect(Collectors.toSet())).orElse(Collections.emptySet());
        }

        /**
         * Adds the object-triple pair to this map.
         * If there is no triple-container for the specified object, or it is empty, or it is in-memory,
         * then a triple will be added to the inner set, otherwise appended to existing stream.
         *
         * @param key    OWLObject (axiom or annotation)
         * @param triple {@link Triple}
         */
        public void add(O key, Triple triple) {
            ONTObject<O> res = find(key).map(o -> o.isEmpty() ? new TripleSet<>(o) : o).orElseGet(() -> new TripleSet<>(key));
            map.put(key, res.add(triple));
            fromCache(key).ifPresent(set -> set.add(triple));
        }

        /**
         * Removes the object-triple pair from the map.
         *
         * @param key    OWLObject (axiom or annotation)
         * @param triple {@link Triple}
         */
        public void remove(O key, Triple triple) {
            find(key).ifPresent(o -> map.put(o.getObject(), o.delete(triple)));
            fromCache(key).ifPresent(set -> set.remove(triple));
        }

        /**
         * Removes the given object and all associated triples.
         *
         * @param key OWLObject (axiom or annotation)
         */
        public void remove(O key) {
            triples.invalidate(key);
            map.remove(key);
        }

        protected Optional<Set<Triple>> fromCache(O key) {
            return Optional.ofNullable(triples.getIfPresent(key));
        }

        protected Set<Triple> get(O key) {
            return triples.get(key);
        }

        public Set<Triple> getTripleSet(O key) {
            return Objects.requireNonNull(get(key));
        }

        public boolean contains(Triple triple) {
            return objects().anyMatch(o -> getTripleSet(o).contains(triple));
        }

        public boolean contains(O o) {
            return map.containsKey(o);
        }

        public Stream<O> objects() {
            return map.keySet().stream();
        }

        public int size() {
            return map.size();
        }

        /**
         * An {@link ONTObject} which holds triples in memory.
         * Used in caches.
         * Note: it is mutable object while the base is immutable.
         *
         * @param <V>
         */
        private class TripleSet<V extends O> extends ONTObject<V> {
            private final Set<Triple> triples;

            protected TripleSet(V object) { // empty
                this(object, new HashSet<>());
            }

            protected TripleSet(ONTObject<V> object) {
                this(object.getObject(), object.triples().collect(Collectors.toCollection(HashSet::new)));
            }

            private TripleSet(V object, Set<Triple> triples) {
                super(object);
                this.triples = triples;
            }

            @Override
            public Stream<Triple> triples() {
                return triples.stream();
            }

            @Override
            protected boolean isEmpty() {
                return triples.isEmpty();
            }

            @Override
            public ONTObject<V> add(Triple triple) {
                triples.add(triple);
                return this;
            }

            @Override
            public ONTObject<V> delete(Triple triple) {
                triples.remove(triple);
                return this;
            }
        }
    }

    /**
     * Creates a listener that handle adding/removing axioms and ontology header annotations through OWL-API interfaces
     * (e.g. through {@link OWLOntology}).
     *
     * @param map {@link ObjectTriplesMap}
     * @param obj {@link OWLObject}
     * @param <O> either {@link OWLAnnotation} or {@link OWLAxiom}
     * @return {@link OwlObjectListener}
     */
    public <O extends OWLObject> OwlObjectListener<O> createListener(ObjectTriplesMap<O> map, O obj) {
        return new OwlObjectListener<>(map, obj);
    }

    /**
     * The listener to monitor the addition and deletion of axioms and ontology annotations.
     *
     * @param <O> {@link OWLAxiom} in our case.
     */
    public static class OwlObjectListener<O extends OWLObject> extends GraphListenerBase {
        private final ObjectTriplesMap<O> store;
        private final O object;

        public OwlObjectListener(ObjectTriplesMap<O> store, O object) {
            this.store = store;
            this.object = object;
        }

        @Override
        protected void addEvent(Triple t) {
            store.add(object, t);
        }

        @Override
        protected void deleteEvent(Triple t) {
            store.remove(object, t);
        }
    }

    /**
     * The direct listener to synchronize caches while working through OWL-API and jena at the same time.
     */
    public class DirectListener extends GraphListenerBase {
        private boolean hasObjectListener() {
            return getGraph().getEventManager().hasListeners(OwlObjectListener.class);
        }

        private void invalidate() {
            if (hasObjectListener()) return;
            clearCache();
        }

        /**
         * if at the moment there is an {@link OwlObjectListener} then it's called from {@link InternalModel#add(OWLAxiom)} =&gt; don't clear cache;
         * otherwise it is direct call and cache must be reset to have correct list of axioms.
         *
         * @param t {@link Triple}
         */
        @Override
        protected void addEvent(Triple t) {
            // we don't know which axiom would own this triple, so we clear whole cache.
            invalidate();
        }

        @Override
        protected void deleteEvent(Triple t) {
            if (hasObjectListener()) return;
            clearCacheOnDelete(t);
        }

        @Override
        public void notifyAddGraph(Graph g, Graph other) {
            invalidate();
        }

        @Override
        public void notifyDeleteGraph(Graph g, Graph other) {
            invalidate();
        }
    }

    /**
     * The internal cache holder which is using while reading owl-objects.
     * Currently it is based on caffeine cache since it is used widely by OWL-API.
     */
    public static class CacheDataFactory extends NoCacheDataFactory {
        protected final LoadingCache<OntCE, ONTObject<? extends OWLClassExpression>> classExpressions;
        protected final LoadingCache<OntDR, ONTObject<? extends OWLDataRange>> dataRanges;
        protected final LoadingCache<OntNAP, ONTObject<OWLAnnotationProperty>> annotationProperties;
        protected final LoadingCache<OntNDP, ONTObject<OWLDataProperty>> datatypeProperties;
        protected final LoadingCache<OntOPE, ONTObject<? extends OWLObjectPropertyExpression>> objectProperties;
        protected final LoadingCache<OntIndividual, ONTObject<? extends OWLIndividual>> individuals;
        protected final LoadingCache<Literal, ONTObject<OWLLiteral>> literals;
        protected final LoadingCache<String, IRI> iris;

        /**
         * @param factory {@link DataFactory}
         * @see <a href='https://github.com/owlcs/owlapi/blob/version5/impl/src/main/java/uk/ac/manchester/cs/owl/owlapi/OWLDataFactoryInternalsImpl.java#L63'>uk.ac.manchester.cs.owl.owlapi.OWLDataFactoryInternalsImpl#builder(CacheLoader)</a>
         */
        public CacheDataFactory(DataFactory factory) {
            super(factory);
            // '2048' is from OWL-API DataFactory impl:
            int size = 2048;
            this.classExpressions = buildSync(size, super::get);
            this.dataRanges = buildSync(size, super::get);
            this.annotationProperties = build(size, super::get);
            this.datatypeProperties = build(size, super::get);
            this.objectProperties = build(size, super::get);
            this.individuals = build(size, super::get);
            this.literals = build(size, super::get);
            this.iris = build(size, IRI::create);
        }

        @Override
        public void clear() {
            classExpressions.invalidateAll();
            dataRanges.invalidateAll();
            annotationProperties.invalidateAll();
            datatypeProperties.invalidateAll();
            objectProperties.invalidateAll();
            individuals.invalidateAll();
            literals.invalidateAll();
        }

        @Override
        public ONTObject<? extends OWLClassExpression> get(OntCE ce) {
            return classExpressions.get(ce);
        }

        @Override
        public ONTObject<? extends OWLDataRange> get(OntDR dr) {
            return dataRanges.get(dr);
        }

        @Override
        public ONTObject<OWLAnnotationProperty> get(OntNAP nap) {
            return annotationProperties.get(nap);
        }

        @Override
        public ONTObject<OWLDataProperty> get(OntNDP ndp) {
            return datatypeProperties.get(ndp);
        }

        @Override
        public ONTObject<? extends OWLObjectPropertyExpression> get(OntOPE ope) {
            return objectProperties.get(ope);
        }

        @Override
        public ONTObject<OWLLiteral> get(Literal literal) {
            return literals.get(literal);
        }

        @Override
        public ONTObject<? extends OWLIndividual> get(OntIndividual i) {
            return individuals.get(i);
        }

        @Override
        public IRI toIRI(String str) { // use global cache
            return iris.get(str);
        }

        /**
         * Builds a synchronized caffeine LoadingCache since
         * <a href='https://github.com/ben-manes/caffeine/issues/209'>a recursive computation is not supported in Java’s maps</a>.
         *
         * @param maxSize, int, the maximum size of the cache
         * @param loader   {@link CacheLoader}
         * @param <K>      key type
         * @param <V>      value type
         * @return {@link LoadingCache}
         */
        private static <K, V> LoadingCache<K, V> buildSync(long maxSize, CacheLoader<K, V> loader) {
            Caffeine<Object, Object> res = Caffeine.newBuilder();
            if (maxSize > 0) {
                res = res.maximumSize(maxSize);
            }
            return res.buildAsync(loader).synchronous();
        }

        /**
         * Builds a standard caffeine LoadingCache.
         *
         * @param maxSize, int, the maximum size of the cache
         * @param loader   {@link CacheLoader}
         * @param <K>      key type
         * @param <V>      value type
         * @return {@link LoadingCache}
         */
        private static <K, V> LoadingCache<K, V> build(long maxSize, CacheLoader<K, V> loader) {
            Caffeine<Object, Object> res = Caffeine.newBuilder();
            if (maxSize > 0) {
                res = res.maximumSize(maxSize);
            }
            return res.build(loader);
        }

        @Override
        public SimpleMap<OntCE, ONTObject<? extends OWLClassExpression>> classExpressionStore() {
            return new CacheMap<>(classExpressions);
        }

        @Override
        public SimpleMap<OntDR, ONTObject<? extends OWLDataRange>> dataRangeStore() {
            return new CacheMap<>(dataRanges);
        }

        public class CacheMap<K, V> implements SimpleMap<K, V> {
            public CacheMap(LoadingCache<K, V> cache) {
                this.cache = cache;
            }

            private final LoadingCache<K, V> cache;

            @Override
            public V get(K key) {
                return cache.getIfPresent(key);
            }

            @Override
            public void put(K key, V value) {
                cache.put(key, value);
            }
        }
    }

    /**
     * Impl for debug.
     */
    public static class MapDataFactory extends NoCacheDataFactory {
        private Map<OntCE, ONTObject<? extends OWLClassExpression>> classExpressions = new HashMap<>();
        private Map<OntDR, ONTObject<? extends OWLDataRange>> dataRanges = new HashMap<>();
        private Map<OntNAP, ONTObject<OWLAnnotationProperty>> annotationProperties = new HashMap<>();
        private Map<OntNDP, ONTObject<OWLDataProperty>> datatypeProperties = new HashMap<>();
        private Map<OntOPE, ONTObject<? extends OWLObjectPropertyExpression>> objectProperties = new HashMap<>();
        private Map<OntIndividual, ONTObject<? extends OWLIndividual>> individuals = new HashMap<>();
        private Map<Literal, ONTObject<OWLLiteral>> literals = new HashMap<>();

        public MapDataFactory(DataFactory factory) {
            super(factory);
        }

        @Override
        public void clear() {
            classExpressions.clear();
            dataRanges.clear();
            annotationProperties.clear();
            objectProperties.clear();
            datatypeProperties.clear();
            individuals.clear();
            literals.clear();
        }

        @Override
        public ONTObject<? extends OWLClassExpression> get(OntCE ce) {
            return classExpressions.computeIfAbsent(ce, super::get);
        }

        @Override
        public ONTObject<? extends OWLDataRange> get(OntDR dr) {
            return dataRanges.computeIfAbsent(dr, super::get);
        }

        @Override
        public ONTObject<OWLAnnotationProperty> get(OntNAP nap) {
            return annotationProperties.computeIfAbsent(nap, super::get);
        }

        @Override
        public ONTObject<OWLDataProperty> get(OntNDP ndp) {
            return datatypeProperties.computeIfAbsent(ndp, super::get);
        }

        @Override
        public ONTObject<? extends OWLObjectPropertyExpression> get(OntOPE ope) {
            return objectProperties.computeIfAbsent(ope, super::get);
        }

        @Override
        public ONTObject<OWLLiteral> get(Literal l) {
            return literals.computeIfAbsent(l, super::get);
        }

        @Override
        public ONTObject<? extends OWLIndividual> get(OntIndividual i) {
            return individuals.computeIfAbsent(i, super::get);
        }

        @Override
        public SimpleMap<OntCE, ONTObject<? extends OWLClassExpression>> classExpressionStore() {
            return SimpleMap.fromMap(classExpressions);
        }

        @Override
        public SimpleMap<OntDR, ONTObject<? extends OWLDataRange>> dataRangeStore() {
            return SimpleMap.fromMap(dataRanges);
        }
    }

}
