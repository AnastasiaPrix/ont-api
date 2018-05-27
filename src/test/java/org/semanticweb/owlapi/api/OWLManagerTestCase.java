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

package org.semanticweb.owlapi.api;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import ru.avicomp.owlapi.OWLManager;

import java.lang.reflect.Field;
import java.util.concurrent.locks.ReadWriteLock;


/**
 * Matthew Horridge Stanford Center for Biomedical Informatics Research 10/04/15
 */

@SuppressWarnings("javadoc")
public class OWLManagerTestCase {

    private OWLOntologyManager manager;
    private OWLOntology ontology;

    @Before
    public void setUp() throws Exception {
        manager = OWLManager.createConcurrentOWLOntologyManager();
        ontology = manager.createOntology();
    }

    @Test
    public void shouldCreateOntologyWithCorrectManager() {
        Assert.assertThat(ontology.getOWLOntologyManager(), CoreMatchers.is(manager));
    }

    @Test
    public void shouldCreateConcurrentOntologyByDefault() {
        Assert.assertThat(ontology, CoreMatchers.is(CoreMatchers.instanceOf(getConcurrentOWLOntologyImplType())));
    }

    public static Class<?> getConcurrentOWLOntologyImplType() {
        return OWLManager.DEBUG_USE_OWL ?
                OWLManager.findClass("uk.ac.manchester.cs.owl.owlapi.concurrent.ConcurrentOWLOntologyImpl") :
                ru.avicomp.ontapi.OntologyModelImpl.Concurrent.class;
    }

    public static Class<?> getOWLOntologyManagerImplType() {
        return OWLManager.DEBUG_USE_OWL ?
                OWLManager.findClass("uk.ac.manchester.cs.owl.owlapi.OWLOntologyManagerImpl") :
                ru.avicomp.ontapi.OntologyManagerImpl.class;
    }

    @Test
    public void shouldShareReadWriteLock() throws Exception {
        // Nasty, but not sure of another way to do this without exposing it in
        // the interface
        Class<?> managerType = getOWLOntologyManagerImplType();
        Class<?> ontologyType = getConcurrentOWLOntologyImplType();
        Object managerReadLock = getReadLock(managerType, manager);
        Object managerWriteLock = getWriteLock(managerType, manager);
        Object ontologyReadLock = getReadLock(ontologyType, ontology);
        Object ontologyWriteLock = getWriteLock(ontologyType, ontology);
        Assert.assertThat(ontologyReadLock, CoreMatchers.is(managerReadLock));
        Assert.assertThat(ontologyWriteLock, CoreMatchers.is(managerWriteLock));
    }

    private static Object getReadLock(Class<?> type, Object instance) throws Exception {
        if (OWLManager.DEBUG_USE_OWL) {
            return getField("readLock", type, instance);
        }
        return ((ReadWriteLock) getField("lock", type, instance)).readLock();
    }

    private static Object getWriteLock(Class<?> type, Object instance) throws Exception {
        if (OWLManager.DEBUG_USE_OWL) {
            return getField("writeLock", type, instance);
        }
        return ((ReadWriteLock) getField("lock", type, instance)).writeLock();
    }

    private static Object getField(String field, Class<?> type, Object instance) throws IllegalAccessException, NoSuchFieldException {
        Field f;
        try {
            f = type.getDeclaredField(field);
        } catch (NoSuchFieldException e1) {
            try {
                f = instance.getClass().getSuperclass().getDeclaredField(field);
            } catch (NoSuchFieldException e2) {
                e1.addSuppressed(e2);
                throw e1;
            }
        }
        f.setAccessible(true);
        return f.get(instance);
    }

}
