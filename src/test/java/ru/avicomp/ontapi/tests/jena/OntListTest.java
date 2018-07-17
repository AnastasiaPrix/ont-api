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

package ru.avicomp.ontapi.tests.jena;

import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.ontapi.jena.OntJenaException;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.RDF;
import ru.avicomp.ontapi.utils.ReadWriteUtils;

import java.util.Arrays;
import java.util.Collections;

/**
 * Created by @szuev on 10.07.2018.
 */
public class OntListTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(OntListTest.class);

    @Test
    public void testCommonFunctionality1() {
        OntGraphModel m = OntModelFactory.createModel();
        m.setNsPrefixes(OntModelFactory.STANDARD);
        OntNOP p1 = m.createOntEntity(OntNOP.class, "p1");
        OntNOP p2 = m.createOntEntity(OntNOP.class, "p2");
        OntNOP p3 = m.createOntEntity(OntNOP.class, "p3");
        p1.addSuperPropertyOf(Collections.emptySet());
        check(m, 1, OntNOP.class);

        OntList<OntOPE> list = p2.createPropertyChain();
        Assert.assertTrue(list.canAs(RDFList.class));
        RDFList r_list = list.as(RDFList.class);
        System.out.println(r_list.isEmpty() + " " + r_list.isValid());
        Assert.assertEquals(3, list.add(p3).add(p3).add(p1).members().count());
        Assert.assertEquals(3, list.members().count());
        Assert.assertEquals(3, list.as(RDFList.class).size());
        Assert.assertEquals(1, p2.listPropertyChains().count());
        Assert.assertEquals(0, p3.listPropertyChains().count());
        Assert.assertEquals(2, m.listObjectProperties().flatMap(OntOPE::listPropertyChains).count());
        check(m, 2, OntNOP.class);

        list.remove();
        Assert.assertEquals(2, list.members().count());
        Assert.assertEquals(2, list.as(RDFList.class).size());
        Assert.assertFalse(list.isEmpty());
        Assert.assertFalse(list.members().anyMatch(p -> p.equals(p1)));
        Assert.assertEquals(p3, list.last().orElseThrow(AssertionError::new));
        Assert.assertEquals(p3, list.first().orElseThrow(AssertionError::new));
        check(m, 2, OntNOP.class);

        Assert.assertEquals(1, (list = list.remove()).members().count());
        Assert.assertEquals(1, list.as(RDFList.class).size());
        Assert.assertFalse(list.isEmpty());
        check(m, 2, OntOPE.class);

        list = list.remove();
        Assert.assertEquals(0, list.members().count());
        Assert.assertEquals(0, list.as(RDFList.class).size());
        Assert.assertTrue(list.isEmpty());
        check(m, 2, OntOPE.class);

    }

    @Test
    public void testCommonFunctionality2() {
        OntGraphModel m = OntModelFactory.createModel();
        m.setNsPrefixes(OntModelFactory.STANDARD);
        OntNOP p1 = m.createOntEntity(OntNOP.class, "p1");
        OntNOP p2 = m.createOntEntity(OntNOP.class, "p2");
        OntNOP p3 = m.createOntEntity(OntNOP.class, "p3");
        OntNOP p4 = m.createOntEntity(OntNOP.class, "p4");
        p1.createPropertyChain().add(p2).add(p3);
        check(m, 1, OntOPE.class);

        Assert.assertEquals(1, p1.listPropertyChains().count());
        OntList<OntOPE> list = p1.listPropertyChains().findFirst().orElseThrow(AssertionError::new);
        Assert.assertEquals(3, list.addFirst(p4).members().count());
        Assert.assertTrue(list.first().filter(p4::equals).isPresent());
        Assert.assertTrue(list.last().filter(p3::equals).isPresent());
        check(m, 1, OntOPE.class);

        Assert.assertEquals(1, p1.listPropertyChains().count());
        Assert.assertEquals(2, list.removeFirst().members().count());
        Assert.assertTrue(list.first().filter(p2::equals).isPresent());
        Assert.assertTrue(list.last().filter(p3::equals).isPresent());
        check(m, 1, OntNOP.class);

        Assert.assertTrue(list.removeFirst().removeFirst().isEmpty());
        check(m, 1, OntPE.class);
        Assert.assertEquals(1, list.addFirst(p4).members().count());
        Assert.assertTrue(list.first().filter(p4::equals).isPresent());
        Assert.assertEquals(1, p1.listPropertyChains().count());
        list = p1.listPropertyChains().findFirst().orElseThrow(AssertionError::new);
        Assert.assertEquals(1, list.members().count());
        Assert.assertTrue(list.last().filter(p4::equals).isPresent());
        check(m, 1, OntOPE.class);

        Assert.assertEquals(3, p1.listPropertyChains().findFirst().orElseThrow(AssertionError::new).addLast(p3).addFirst(p2).size());
        check(m, 1, OntNOP.class);
        list = p1.listPropertyChains().findFirst().orElseThrow(AssertionError::new);
        Assert.assertEquals(3, list.size());
        list.removeLast().removeLast();
        Assert.assertEquals(1, p1.listPropertyChains().findFirst().orElseThrow(AssertionError::new).size());
        Assert.assertEquals(1, list.members().count());

        list.clear();
        Assert.assertEquals(0, list.members().count());
        Assert.assertTrue(p1.listPropertyChains().findFirst().orElseThrow(AssertionError::new).isEmpty());
        Assert.assertEquals(0, list.members().count());
        Assert.assertEquals(3, list.addLast(p2).addFirst(p4).addFirst(p3).size());
        Assert.assertEquals(Arrays.asList(p3, p4, p2), list.as(RDFList.class).asJavaList());
    }

    @Test
    public void testGetAndClear1() {
        OntGraphModel m = OntModelFactory.createModel();
        m.setNsPrefixes(OntModelFactory.STANDARD);
        OntNOP p1 = m.createOntEntity(OntNOP.class, "p1");
        OntNOP p2 = m.createOntEntity(OntNOP.class, "p2");
        OntNOP p3 = m.createOntEntity(OntNOP.class, "p3");
        OntNOP p4 = m.createOntEntity(OntNOP.class, "p4");

        OntList<OntOPE> list = p1.createPropertyChain().add(p2).add(p3).add(p4);
        check(m, 1, OntOPE.class);

        Assert.assertEquals(3, list.get(0).size());
        Assert.assertEquals(2, list.get(1).size());
        Assert.assertEquals(1, list.get(2).size());
        Assert.assertEquals(0, list.get(3).size());
        try {
            OntList<OntOPE> n = list.get(4);
            Assert.fail("Found out of bound list: " + n);
        } catch (OntJenaException.IllegalArgument j) {
            LOGGER.debug("Expected: {}", j.getMessage());
        }

        Assert.assertTrue(list.get(2).clear().isEmpty());
        check(m, 1, OntOPE.class);
        Assert.assertEquals(2, list.size());
    }

    @Test
    public void testGetAndClear2() {
        OntGraphModel m = OntModelFactory.createModel();
        m.setNsPrefixes(OntModelFactory.STANDARD);
        OntNOP p1 = m.createOntEntity(OntNOP.class, "p1");
        OntNOP p2 = m.createOntEntity(OntNOP.class, "p2");
        OntNOP p3 = m.createOntEntity(OntNOP.class, "p3");
        OntNOP p4 = m.createOntEntity(OntNOP.class, "p4");

        OntList<OntOPE> list = p1.createPropertyChain().add(p2).add(p3).add(p4);
        check(m, 1, OntOPE.class);
        Assert.assertEquals(2, list.get(2).addFirst(p2).get(1).addLast(p2).size());
        check(m, 1, OntOPE.class);
        // p2, p3, p2, p4, p2
        Assert.assertEquals(Arrays.asList(p2, p3, p2, p4, p2), list.as(RDFList.class).asJavaList());
        // link expired:
        p1.listPropertyChains().findFirst().orElseThrow(AssertionError::new).clear();
        try {
            list.size();
            Assert.fail("Possible to work with expired ont-list instance");
        } catch (OntJenaException.IllegalState j) {
            LOGGER.debug("Expected: {}", j.getMessage());
        }
    }

    @Test
    public void testMixedList() {
        OntGraphModel m = OntModelFactory.createModel();
        m.setNsPrefixes(OntModelFactory.STANDARD);
        OntNOP p1 = m.createOntEntity(OntNOP.class, "p1");
        OntNOP p2 = m.createOntEntity(OntNOP.class, "p2");
        OntNOP p3 = m.createOntEntity(OntNOP.class, "p3");
        OntNOP p4 = m.createOntEntity(OntNOP.class, "p4");
        OntList<OntOPE> list = p1.createPropertyChain(Arrays.asList(p4, p3, p2));
        list.get(1).as(RDFList.class).replace(0, m.createTypedLiteral("Not a property"));
        check(m, 1, RDFNode.class);
        Assert.assertEquals(3, list.size());
        try {
            long c = list.members().count();
            Assert.fail("Possible to get members count for expired ont-list: " + c);
        } catch (OntJenaException.IllegalState j) {
            LOGGER.debug("Expected: {}", j.getMessage());
        }
        list = p1.listPropertyChains().findFirst().orElseThrow(AssertionError::new);
        Assert.assertEquals(3, list.size());
        Assert.assertEquals(2, list.members().count());
        Assert.assertEquals(3, list.addFirst(p3).members().count());
        Assert.assertEquals(4, list.size());
        Assert.assertEquals(2, list.get(1).members().count());
        Assert.assertEquals(p3, list.first().orElseThrow(AssertionError::new));
        Assert.assertEquals(p2, list.last().orElseThrow(AssertionError::new));
    }

    private static void check(OntGraphModel m, int numLists, Class<? extends RDFNode> type) {
        debug(m);
        Assert.assertFalse(m.contains(null, RDF.type, RDF.List));
        Assert.assertEquals(numLists, m.statements(null, null, RDF.nil).count());
        m.statements(null, RDF.first, null).map(Statement::getObject).forEach(n -> Assert.assertTrue(n.canAs(type)));
        m.statements(null, RDF.rest, null)
                .map(Statement::getObject)
                .forEach(n -> Assert.assertTrue(RDF.nil.equals(n) ||
                        (n.isAnon() && m.statements().map(OntStatement::getSubject).anyMatch(n::equals))));
    }

    private static void debug(OntGraphModel m) {
        ReadWriteUtils.print(m);
        LOGGER.debug("====");
        m.statements().map(Models::toString).forEach(LOGGER::debug);
    }
}
