/*
 * This file is part of the ONT API.
 * The contents of this file are subject to the LGPL License, Version 3.0.
 * Copyright (c) 2017, Avicomp Services, AO
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * Alternatively, the contents of this file may be used under the terms of the Apache License, Version 2.0 in which case, the provisions of the Apache License Version 2.0 are applicable instead of those above.
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package ru.avicomp.ontapi;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.jena.graph.Graph;
import org.semanticweb.owlapi.io.FileDocumentSource;
import org.semanticweb.owlapi.io.IRIDocumentSource;
import org.semanticweb.owlapi.io.OWLOntologyDocumentSource;
import org.semanticweb.owlapi.io.StreamDocumentSource;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.OntologyCopy;

import ru.avicomp.ontapi.config.OntConfig;
import ru.avicomp.ontapi.config.OntLoaderConfiguration;
import ru.avicomp.ontapi.config.OntWriterConfiguration;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import uk.ac.manchester.cs.owl.owlapi.OWLOntologyManagerImpl;


/**
 * Ontology manager.
 * Base class: {@link OWLOntologyManager}
 * It is the main point for creating, loading and accessing {@link OntologyModel}s models.
 * New (ONT-API) methods:
 * - {@link #addOntology(Graph)}
 * - {@link #createGraphModel(String)}
 * - {@link #createGraphModel(String, String)}
 * - {@link #models()}
 * - {@link #getGraphModel(String)}
 * - {@link #getGraphModel(String, String)}
 * - {@link #addDocumentSourceMapper(DocumentSourceMapping)}
 * - {@link #removeDocumentSourceMapper(DocumentSourceMapping)}
 * - {@link #documentSourceMappers()}
 * <p>
 * Created by szuev on 24.10.2016.
 */
public interface OntologyManager extends OWLOntologyManager {

    /**
     * Returns the loading config.
     * Extended immutable {@link OWLOntologyLoaderConfiguration}.
     * Be warned: this is a read only accessor, to change configuration create a new config (using any its setter)
     * and pass it to the {@link #setOntologyLoaderConfiguration(OWLOntologyLoaderConfiguration)} method.
     *
     * @return {@link OntLoaderConfiguration}
     */
    @Override
    OntLoaderConfiguration getOntologyLoaderConfiguration();

    /**
     * Returns the writer config.
     * see note for {@link this#getOntologyLoaderConfiguration()} method.
     *
     * @return {@link OntWriterConfiguration}
     */
    @Override
    OntWriterConfiguration getOntologyWriterConfiguration();

    /**
     * Returns the copy of global config (extended {@link OntologyConfigurator}) and
     * also access point to the {@link OWLOntologyLoaderConfiguration} and {@link OWLOntologyWriterConfiguration}
     *
     * @return {@link OntConfig}
     */
    @Override
    OntConfig getOntologyConfigurator();

    /**
     * Adds document-source-mapping to the inner collection.
     * New (ONT-API) method.
     *
     * @param mapper {@link DocumentSourceMapping}
     * @since 1.0.1
     */
    void addDocumentSourceMapper(DocumentSourceMapping mapper);

    /**
     * Removes document-source-mapping from the inner collection.
     * New (ONT-API) method.
     *
     * @param mapper {@link DocumentSourceMapping}
     * @since 1.0.1
     */
    void removeDocumentSourceMapper(DocumentSourceMapping mapper);

    /**
     * Returns document-source-mappings as stream.
     * New (ONT-API) method.
     *
     * @return Stream of {@link DocumentSourceMapping}
     * @since 1.0.1
     */
    Stream<DocumentSourceMapping> documentSourceMappers();

    /**
     * Contrary to the original description this method works with version IRI also if it fails with ontology IRI.
     *
     * @param iri {@link IRI} ontology IRI or version IRI as can be seen from the OWL-API implementation
     *            (see {@link uk.ac.manchester.cs.owl.owlapi.OWLOntologyManagerImpl#getOntology(IRI)})
     * @return {@link OntologyModel} or null
     */
    @Override
    OntologyModel getOntology(@Nonnull IRI iri);

    /**
     * Finds ontology by the specified {@code id} (could be anonymous).
     * If there is no such ontology it tries to find the first with the same ontology IRI as in the specified {@code id}.
     *
     * @param id {@link OWLOntologyID} ID
     * @return {@link OntologyModel} or {@code null}
     * @see OWLOntologyManagerImpl#getOntology(OWLOntologyID)
     * @see #contains(OWLOntologyID)
     */
    @Override
    OntologyModel getOntology(@Nonnull OWLOntologyID id);

    /**
     * See description for {@link #getOntology(IRI)} and be warned!
     *
     * @param iri {@link IRI} the ontology iri or version iri
     * @return true if ontology exists
     * @see uk.ac.manchester.cs.owl.owlapi.OWLOntologyManagerImpl#contains(IRI)
     */
    @Override
    boolean contains(@Nonnull IRI iri);

    /**
     * Be warned: this method returns always false for any anonymous id.
     * For non-anonymous id it performs searching by ontology iri ignoring version iri.
     * This is in order to make the behaviour the same as OWL-API method.
     *
     * @param id {@link OWLOntologyID} the id
     * @return true if {@code id} is not anonymous and there is an ontology with the same iri as in the specified {@code id}
     * @see uk.ac.manchester.cs.owl.owlapi.OWLOntologyManagerImpl#contains(OWLOntologyID)
     */
    @Override
    boolean contains(@Nonnull OWLOntologyID id);

    /**
     * @see OWLOntologyManager#getImportedOntology(OWLImportsDeclaration)
     */
    @Nullable
    OntologyModel getImportedOntology(@Nonnull OWLImportsDeclaration declaration);

    /**
     * Creates an ontology.
     * <p>
     * Note: this method doesn't throw a checked exception {@link OWLOntologyCreationException} like OWL-API.
     * Instead it there is an unchecked exception {@link OntApiException} which may wrap {@link OWLOntologyCreationException}.
     * OWL-API and ONT-API work in different ways.
     * So i see it is impossible to save the same places for exceptions.
     * For example in OWL-API you could expect some kind of exception during saving ontology,
     * but in ONT-API there would be another kind of exception during adding axiom.
     * I believe there is no reasons to save the same behaviour with exceptions everywhere.
     * The return type is also changed from {@link org.semanticweb.owlapi.model.OWLOntology} to our class {@link OntologyModel}.
     *
     * @param id {@link OWLOntologyID}
     * @return ontology {@link OntologyModel}
     * @throws OntApiException in case something wrong.
     */
    @Override
    OntologyModel createOntology(@Nonnull OWLOntologyID id);

    /**
     * Puts a graph to this manager.
     * This is a new (ONT-API) method.
     * Note: graph transformation are not performed!
     *
     * @param graph {@link Graph}
     * @return {@link OntologyModel}
     * @see OntGraphDocumentSource
     * @since 1.0.1
     */
    OntologyModel addOntology(@Nonnull Graph graph);

    /**
     * @see OWLOntologyManager#copyOntology(OWLOntology, OntologyCopy)
     */
    @Override
    OntologyModel copyOntology(@Nonnull OWLOntology toCopy, @Nonnull OntologyCopy settings) throws OWLOntologyCreationException;

    /**
     * @see OWLOntologyManager#loadOntology(IRI)
     */
    @Override
    OntologyModel loadOntology(@Nonnull IRI source) throws OWLOntologyCreationException;

    /**
     * @see OWLOntologyManager#loadOntologyFromOntologyDocument(OWLOntologyDocumentSource, OWLOntologyLoaderConfiguration)
     */
    @Override
    OntologyModel loadOntologyFromOntologyDocument(@Nonnull OWLOntologyDocumentSource source,
                                                   @Nonnull OWLOntologyLoaderConfiguration config) throws OWLOntologyCreationException;

    /**
     * @see OWLOntologyManager#createOntology()
     */
    default OntologyModel createOntology() {
        return createOntology(new OWLOntologyID());
    }

    /**
     * @see OWLOntologyManager#createOntology(IRI)
     */
    default OntologyModel createOntology(@Nullable IRI iri) {
        return createOntology(new OWLOntologyID(Optional.ofNullable(iri), Optional.empty()));
    }

    /**
     * @see OWLOntologyManager#loadOntologyFromOntologyDocument(OWLOntologyDocumentSource)
     */
    @Override
    default OntologyModel loadOntologyFromOntologyDocument(@Nonnull OWLOntologyDocumentSource source)
            throws OWLOntologyCreationException {
        return loadOntologyFromOntologyDocument(source, getOntologyLoaderConfiguration());
    }

    /**
     * @see OWLOntologyManager#loadOntologyFromOntologyDocument(IRI)
     */
    @Override
    default OntologyModel loadOntologyFromOntologyDocument(@Nonnull IRI iri) throws OWLOntologyCreationException {
        return loadOntologyFromOntologyDocument(new IRIDocumentSource(iri, null, null));
    }

    /**
     * @see OWLOntologyManager#loadOntologyFromOntologyDocument(File)
     */
    @Override
    default OntologyModel loadOntologyFromOntologyDocument(@Nonnull File file) throws OWLOntologyCreationException {
        return loadOntologyFromOntologyDocument(new FileDocumentSource(file));
    }

    /**
     * @see OWLOntologyManager#loadOntologyFromOntologyDocument(InputStream)
     */
    @Override
    default OntologyModel loadOntologyFromOntologyDocument(@Nonnull InputStream input) throws OWLOntologyCreationException {
        return loadOntologyFromOntologyDocument(new StreamDocumentSource(input));
    }

    /**
     * Gets {@link OntGraphModel} by the ontology and version iris.
     *
     * @param iri     String, nullable
     * @param version String, nullable
     * @return {@link OntGraphModel}
     */
    default OntGraphModel getGraphModel(@Nullable String iri, @Nullable String version) {
        OWLOntologyID id = new OWLOntologyID(Optional.ofNullable(iri).map(IRI::create), Optional.ofNullable(version).map(IRI::create));
        OntologyModel res = getOntology(id);
        return res == null ? null : res.asGraphModel();
    }

    /**
     * Gets {@link OntGraphModel} by the ontology iri
     *
     * @param iri String, nullable
     * @return {@link OntGraphModel}
     */
    default OntGraphModel getGraphModel(@Nullable String iri) {
        return getGraphModel(iri, null);
    }

    /**
     * Creates {@link OntGraphModel} with specified ontology-iri and version-iri
     *
     * @param iri     String, nullable
     * @param version String, nullable
     * @return {@link OntGraphModel}
     */
    default OntGraphModel createGraphModel(@Nullable String iri, @Nullable String version) {
        OWLOntologyID id = new OWLOntologyID(Optional.ofNullable(iri).map(IRI::create), Optional.ofNullable(version).map(IRI::create));
        return createOntology(id).asGraphModel();
    }

    /**
     * Creates {@link OntGraphModel} with specified iri
     *
     * @param iri String, nullable
     * @return {@link OntGraphModel}
     */
    default OntGraphModel createGraphModel(@Nullable String iri) {
        return createGraphModel(iri, null);
    }

    /**
     * Returns all {@link OntGraphModel}s as stream.
     *
     * @return Stream of {@link OntGraphModel}
     */
    default Stream<OntGraphModel> models() {
        return ontologies().map(OntologyModel.class::cast).map(OntologyModel::asGraphModel);
    }

    /**
     * The core of the manager.
     * The factory to create and load ontologies.
     */
    interface Factory extends OWLOntologyFactory {

        @Override
        OntologyModel createOWLOntology(@Nonnull OWLOntologyManager manager,
                                        @Nonnull OWLOntologyID id,
                                        @Nonnull IRI documentIRI,
                                        @Nonnull OWLOntologyCreationHandler handler);

        @Override
        OntologyModel loadOWLOntology(@Nonnull OWLOntologyManager manager,
                                      @Nonnull OWLOntologyDocumentSource source,
                                      @Nonnull OWLOntologyCreationHandler handler,
                                      @Nonnull OWLOntologyLoaderConfiguration config) throws OWLOntologyCreationException;
    }

    /**
     * The Document Source mapping.
     * To customize ontology loading.
     *
     * @see OWLOntologyDocumentSource
     * @since 1.0.1
     */
    @FunctionalInterface
    interface DocumentSourceMapping extends Serializable {
        OWLOntologyDocumentSource map(OWLOntologyID id);
    }
}
