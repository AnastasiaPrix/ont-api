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

package ru.avicomp.ontapi;

import org.apache.jena.graph.Graph;
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration;
import org.semanticweb.owlapi.model.OWLOntologyWriterConfiguration;
import ru.avicomp.ontapi.config.OntLoaderConfiguration;
import ru.avicomp.ontapi.config.OntWriterConfiguration;
import ru.avicomp.ontapi.internal.*;
import ru.avicomp.ontapi.jena.impl.conf.OntPersonality;

import java.io.Serializable;
import java.util.Objects;

/**
 * An implementation of {@link InternalConfig} with a reference to a manager,
 * which is necessary to provide access to its settings (loader and writer configurations).
 * Also it is a facility to create {@link InternalModel} instance that gets this config and all its links implicitly.
 *
 * @see OWLOntologyLoaderConfiguration
 * @see OWLOntologyWriterConfiguration
 */
@SuppressWarnings("WeakerAccess")
public class ModelConfig implements InternalConfig, Serializable {
    private static final long serialVersionUID = 3681978037818003272L;

    protected OntLoaderConfiguration readConf;
    protected OntWriterConfiguration writerConf;
    protected OntologyManagerImpl manager;

    public ModelConfig(OntologyManagerImpl m) {
        this.manager = Objects.requireNonNull(m);
    }

    /**
     * Creates an {@link InternalModel} instance.
     *
     * @param graph {@link Graph}, not {@code null}
     * @return {@link InternalModel}
     */
    public InternalModel createInternalModel(Graph graph) {
        return InternalModelHolder.createInternalModel(graph, getPersonality(), this::createObjectFactory, this);
    }

    /**
     * Returns the manager.
     *
     * @return {@link OntologyManagerImpl}
     */
    public OntologyManagerImpl getManager() {
        return manager;
    }

    /**
     * Assigns new manager.
     *
     * @param other {@link OntologyManagerImpl}, new manager
     * @return {@link OntologyManagerImpl}, previous manager
     */
    public OntologyManagerImpl setManager(OntologyManagerImpl other) {
        OntologyManagerImpl res = this.manager;
        this.manager = other;
        return res;
    }

    public void setLoaderConf(OntLoaderConfiguration conf) {
        if (Objects.equals(getLoaderConfig(), conf)) return;
        this.readConf = conf;
    }

    /**
     * Returns loader-configuration settings,
     * which can be global (belong to the manager) or specific to the ontology instance.
     *
     * @return {@link OntLoaderConfiguration}, not {@code null}
     */
    public OntLoaderConfiguration getLoaderConfig() {
        return this.readConf == null ? manager.getOntologyLoaderConfiguration() : this.readConf;
    }

    /**
     * Returns writer-configuration settings,
     * which can be global (belong to the manager) or specific to the ontology instance.
     *
     * @return {@link OntWriterConfiguration}, not {@code null}
     */
    public OntWriterConfiguration getWriterConfig() {
        return this.writerConf == null ? manager.getOntologyWriterConfiguration() : this.writerConf;
    }

    protected <K, V> InternalCache<K, V> createCache() {
        return InternalCache.createBounded(manager.isConcurrent(), CacheObjectFactory.CACHE_SIZE);
    }

    public InternalObjectFactory createObjectFactory() {
        return new CacheObjectFactory(manager.dataFactory, this::createCache, manager.iris) {
        };
    }

    public OntPersonality getPersonality() {
        return getLoaderConfig().getPersonality();
    }

    @Override
    public boolean isLoadAnnotationAxioms() {
        return getLoaderConfig().isLoadAnnotationAxioms();
    }

    @Override
    public boolean isAllowBulkAnnotationAssertions() {
        return getLoaderConfig().isAllowBulkAnnotationAssertions();
    }

    @Override
    public boolean isIgnoreAnnotationAxiomOverlaps() {
        return getLoaderConfig().isIgnoreAnnotationAxiomOverlaps();
    }

    @Override
    public boolean isAllowReadDeclarations() {
        return getLoaderConfig().isAllowReadDeclarations();
    }

    @Override
    public boolean isSplitAxiomAnnotations() {
        return getLoaderConfig().isSplitAxiomAnnotations();
    }

    @Override
    public boolean isIgnoreAxiomsReadErrors() {
        return getLoaderConfig().isIgnoreAxiomsReadErrors();
    }

    @Override
    public boolean parallel() {
        return manager.isConcurrent();
    }

    /**
     * Answers {@code true} if the specified config differs from this model config in important parameters.
     *
     * @param other {@link OntLoaderConfiguration} to test
     * @return boolean
     */
    public boolean hasChanges(OntLoaderConfiguration other) {
        return hasChanges(getLoaderConfig(), other);
    }

    /**
     * Answers whether the specified configs are different in the settings concerning axioms reading.
     *
     * @param left  {@link OntLoaderConfiguration}, can be {@code null}
     * @param right {@link OntLoaderConfiguration}, can be {@code null}
     * @return {@code true} if configs have equivalent axioms settings
     */
    public static boolean hasChanges(OntLoaderConfiguration left, OntLoaderConfiguration right) {
        if (left == null && right != null) return true;
        if (left != null && right == null) return true;
        if (left == right)
            return false;
        if (left.isLoadAnnotationAxioms() != right.isLoadAnnotationAxioms()) return true;
        if (left.isAllowBulkAnnotationAssertions() != right.isAllowBulkAnnotationAssertions()) return true;
        if (left.isIgnoreAnnotationAxiomOverlaps() != right.isIgnoreAnnotationAxiomOverlaps()) return true;
        if (left.isAllowReadDeclarations() != right.isAllowReadDeclarations()) return true;
        if (left.isSplitAxiomAnnotations() != right.isSplitAxiomAnnotations()) return true;
        return left.isIgnoreAxiomsReadErrors() != right.isIgnoreAxiomsReadErrors();
    }
}