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
 *
 */

package org.semanticweb.owlapi.api.test;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import ru.avicomp.owlapi.OWLManager;
import uk.ac.manchester.cs.owl.owlapi.OWLOntologyManagerImpl;
import uk.ac.manchester.cs.owl.owlapi.concurrent.ConcurrentOWLOntologyImpl;

import java.lang.reflect.Field;
import java.util.concurrent.locks.ReadWriteLock;


/**
 * Matthew Horridge Stanford Center for Biomedical Informatics Research 10/04/15
 */
@ru.avicomp.ontapi.utils.ModifiedForONTApi
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
        if (OWLManager.DEBUG_USE_OWL) {
            Assert.assertThat(ontology, CoreMatchers.is(CoreMatchers.instanceOf(ConcurrentOWLOntologyImpl.class)));
        } else {
            Assert.assertThat(ontology, CoreMatchers.is(CoreMatchers.instanceOf(ru.avicomp.ontapi.OntologyModelImpl.Concurrent.class)));
        }
    }

    @Test
    public void shouldShareReadWriteLock() throws Exception {
        // Nasty, but not sure of another way to do this without exposing it in
        // the interface
        Object managerReadLock, managerWriteLock;
        if (OWLManager.DEBUG_USE_OWL) {
            Field ontologyManagerField = OWLOntologyManagerImpl.class.getDeclaredField("readLock");
            ontologyManagerField.setAccessible(true);
            managerReadLock = ontologyManagerField.get(manager);
            ontologyManagerField = OWLOntologyManagerImpl.class.getDeclaredField("writeLock");
            ontologyManagerField.setAccessible(true);
            managerWriteLock = ontologyManagerField.get(manager);
        } else {
            Field ontologyManagerField = ru.avicomp.ontapi.OntologyManagerImpl.class.getDeclaredField("lock");
            ontologyManagerField.setAccessible(true);
            managerReadLock = ((ReadWriteLock) ontologyManagerField.get(manager)).readLock();
            managerWriteLock = ((ReadWriteLock) ontologyManagerField.get(manager)).writeLock();
        }

        Field ontologyLockField = ConcurrentOWLOntologyImpl.class.getDeclaredField("readLock");
        ontologyLockField.setAccessible(true);
        Object ontologyReadLock = ontologyLockField.get(ontology);
        ontologyLockField = ConcurrentOWLOntologyImpl.class.getDeclaredField("writeLock");
        ontologyLockField.setAccessible(true);
        Object ontologyWriteLock = ontologyLockField.get(ontology);

        Assert.assertThat(ontologyReadLock, CoreMatchers.is(managerReadLock));
        Assert.assertThat(ontologyWriteLock, CoreMatchers.is(managerWriteLock));
    }

}
