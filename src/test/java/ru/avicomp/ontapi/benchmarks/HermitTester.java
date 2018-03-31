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

package ru.avicomp.ontapi.benchmarks;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.ontapi.OntManagers;
import ru.avicomp.ontapi.OntologyModel;
import ru.avicomp.ontapi.tests.HermitReasonerTest;

import java.util.function.Supplier;

/**
 * Created by @szuev on 30.03.2018.
 */
@Ignore
public class HermitTester {
    private static final Logger LOGGER = LoggerFactory.getLogger(HermitTester.class);

    @BeforeClass
    public static void before() throws Exception {
        processTest(1, OntManagers::createONT);
        processTest(1, OntManagers::createOWL);
        LOGGER.info("==============");
    }

    @Test
    public void test01OWL() throws Exception {
        processTest(100, OntManagers::createOWL);
    }

    @Test
    public void test02ONT() throws Exception {
        processTest(100, OntManagers::createONT);
    }

    private static void processTest(int num, Supplier<OWLOntologyManager> manager) throws Exception {
        IRI file = IRI.create(HermitTester.class.getResource("/pizza.ttl"));
        LOGGER.info("Ontology file {}", file);
        OWLOntologyManager m = manager.get();
        OWLOntology o = m.loadOntologyFromOntologyDocument(file);
        for (int i = 0; i < num; i++) {
            LOGGER.info("Test #{}, {}", i, o instanceof OntologyModel ? "ONT" : "OWL");
            HermitReasonerTest.performHermitTest(o);
        }
    }

}
