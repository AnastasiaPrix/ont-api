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

package ru.avicomp.ontapi.tests.model;

import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import org.junit.Assert;
import org.junit.Test;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import ru.avicomp.ontapi.OntManagers;
import ru.avicomp.ontapi.OntologyManager;
import ru.avicomp.ontapi.OntologyModel;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntID;
import ru.avicomp.ontapi.jena.model.OntIndividual;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;
import ru.avicomp.ontapi.utils.OntIRI;
import ru.avicomp.ontapi.utils.ReadWriteUtils;
import ru.avicomp.ontapi.utils.TestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * to test behaviour with owl:imports
 * <p>
 * Created by @szuev on 08.10.2016.
 */
public class ImportsOntModelTest extends OntModelTestBase {

    @Test
    public void testAdd() {
        OntIRI iri = OntIRI.create("http://test.test/add-import/1");
        OntologyModel owl = TestUtils.createModel(iri);
        OntologyManager manager = owl.getOWLOntologyManager();
        OWLDataFactory factory = manager.getOWLDataFactory();
        OntGraphModel jena = owl.asGraphModel();
        int importsCount = 4;
        OntID jenaOnt = jena.setID(iri.getIRIString());
        Assert.assertNotNull(jenaOnt);
        LOGGER.info("Add imports.");
        OntIRI import1 = OntIRI.create("http://dummy-imports.com/first");
        OntIRI import2 = OntIRI.create("http://dummy-imports.com/second");
        OntIRI import3 = OntIRI.create(ReadWriteUtils.getResourceURI("foaf.rdf"));
        OntIRI import4 = OntIRI.create(ReadWriteUtils.getResourceURI("pizza.ttl"));
        manager.applyChange(new AddImport(owl, factory.getOWLImportsDeclaration(import1)));
        jena.getID().addImport(import2.getIRIString());
        manager.applyChange(new AddImport(owl, factory.getOWLImportsDeclaration(import3)));
        jena.getID().addImport(import4.getIRIString());

        debug(owl);

        Assert.assertEquals("OWL: incorrect imported ontology count.", 0, owl.imports().count());
        Assert.assertEquals("OWL: incorrect imports count.", importsCount, owl.importsDeclarations().count());
        Assert.assertEquals("Jena: incorrect imports count.", importsCount, jena.listStatements(iri.toResource(), OWL.imports, (RDFNode) null).toList().size());

        LOGGER.info("Remove imports.");
        jena.getID().removeImport(import4.getIRIString());
        manager.applyChange(new RemoveImport(owl, factory.getOWLImportsDeclaration(import1)));
        debug(owl);
        importsCount = 2;
        Assert.assertEquals("OWL: incorrect imports count after removing.", importsCount, owl.importsDeclarations().count());
        Assert.assertEquals("Jena: incorrect imports count after removing.", importsCount, jena.getID().imports().count());

        debug(owl);
    }

    @Test
    public void testGraph() {
        OntIRI baseIRI = OntIRI.create("http://test.test/add-import/base");
        OntologyManager manager = OntManagers.createConcurrentONT();
        OWLDataFactory factory = manager.getOWLDataFactory();

        OntologyModel base = manager.createOntology(baseIRI);

        OntIRI classIRI1 = baseIRI.addFragment("Class-1");
        OntIRI classIRI2 = baseIRI.addFragment("Class-2");
        OntIRI objPropIRI = baseIRI.addFragment("obj-prop-1");
        OntIRI dataPropIRI = baseIRI.addFragment("data-prop-1");
        OntIRI annPropIRI = baseIRI.addFragment("ann-prop-1");
        OntIRI dataTypeIRI = baseIRI.addFragment("data-type-1");

        OWLClass class1 = factory.getOWLClass(classIRI1);
        OWLClass class2 = factory.getOWLClass(classIRI2);
        OWLObjectProperty objProperty = factory.getOWLObjectProperty(objPropIRI);
        OWLDataProperty dataProperty = factory.getOWLDataProperty(dataPropIRI);
        OWLAnnotationProperty annProperty = factory.getOWLAnnotationProperty(annPropIRI);
        OWLDatatype dataType = factory.getOWLDatatype(dataTypeIRI);

        List<OWLAxiom> baseAxioms = new ArrayList<>();
        baseAxioms.add(factory.getOWLDeclarationAxiom(objProperty));
        baseAxioms.add(factory.getOWLDeclarationAxiom(dataProperty));
        baseAxioms.add(factory.getOWLDeclarationAxiom(annProperty));
        baseAxioms.add(factory.getOWLDeclarationAxiom(class1));
        baseAxioms.add(factory.getOWLDeclarationAxiom(class2));
        baseAxioms.add(factory.getOWLDeclarationAxiom(dataType));

        LOGGER.info("Apply axioms to the base ontology " + baseIRI);
        baseAxioms.forEach(axiom -> base.applyChanges(new AddAxiom(base, axiom)));

        debug(base);

        LOGGER.info("Add import " + baseIRI);
        OntIRI childIRI = OntIRI.create("http://test.test/add-import/child");
        OntologyModel child = manager.createOntology(childIRI);
        child.applyChanges(new AddImport(child, factory.getOWLImportsDeclaration(baseIRI)));

        Assert.assertEquals("Incorrect imports count", 1, child.imports().count());

        OWLDatatypeRestriction dataRange1 = factory.getOWLDatatypeMinMaxInclusiveRestriction(1, 2.3);

        OWLNamedIndividual individual1 = factory.getOWLNamedIndividual(childIRI.addFragment("Individual-1"));
        OWLNamedIndividual individual2 = factory.getOWLNamedIndividual(childIRI.addFragment("Individual-2"));
        OWLClassExpression ce1 = factory.getOWLObjectUnionOf(class1, class2);
        List<OWLAxiom> axioms = new ArrayList<>();
        axioms.add(factory.getOWLDeclarationAxiom(individual1));
        axioms.add(factory.getOWLDeclarationAxiom(individual2));
        axioms.add(factory.getOWLInverseFunctionalObjectPropertyAxiom(objProperty));
        axioms.add(factory.getOWLDataPropertyRangeAxiom(dataProperty, dataRange1));
        axioms.add(factory.getOWLClassAssertionAxiom(class1, individual1));
        axioms.add(factory.getOWLClassAssertionAxiom(ce1, individual2));
        axioms.add(factory.getOWLAnnotationPropertyDomainAxiom(annProperty, class1.getIRI()));

        LOGGER.info("Apply axioms to the subsidiary ontology " + child);
        axioms.forEach(axiom -> child.applyChanges(new AddAxiom(child, axiom)));

        debug(child);

        LOGGER.info("Check triplets presence.");
        checkTriple(base.asGraphModel(), child.asGraphModel(), classIRI1.toResource(), RDF.type, OWL.Class);
        checkTriple(base.asGraphModel(), child.asGraphModel(), classIRI2.toResource(), RDF.type, OWL.Class);
        checkTriple(base.asGraphModel(), child.asGraphModel(), objPropIRI.toResource(), RDF.type, OWL.ObjectProperty);
        checkTriple(base.asGraphModel(), child.asGraphModel(), dataPropIRI.toResource(), RDF.type, OWL.DatatypeProperty);
        checkTriple(base.asGraphModel(), child.asGraphModel(), annPropIRI.toResource(), RDF.type, OWL.AnnotationProperty);
        checkTriple(base.asGraphModel(), child.asGraphModel(), dataTypeIRI.toResource(), RDF.type, RDFS.Datatype);

        LOGGER.info("Reload models.");
        OntologyManager newManager = OntManagers.createONT();
        OntologyModel newBase = ReadWriteUtils.convertJenaToONT(newManager, base.asGraphModel());
        OntologyModel newChild = ReadWriteUtils.convertJenaToONT(newManager, child.asGraphModel());

        Assert.assertEquals("Incorrect imports count", 1, newChild.imports().count());
        Assert.assertEquals("Should be the same number of statements",
                child.asGraphModel().listStatements().toList().size(),
                newChild.asGraphModel().listStatements().toList().size());
        TestUtils.compareAxioms(base.axioms(), newBase.axioms());

        LOGGER.info("Check axioms after reload:");
        LOGGER.debug("Origin ont");
        child.axioms().map(String::valueOf).forEach(LOGGER::debug);
        LOGGER.debug("Reloaded ont");
        newChild.axioms().map(String::valueOf).forEach(LOGGER::debug);
        TestUtils.compareAxioms(child.axioms(), newChild.axioms());

        LOGGER.info("Remove import test");
        child.applyChanges(new RemoveImport(child, factory.getOWLImportsDeclaration(baseIRI)));
        debug(child);
        checkTriplePresence(child.asGraphModel(), classIRI1.toResource(), RDF.type, OWL.Class);
        checkTriplePresence(child.asGraphModel(), classIRI2.toResource(), RDF.type, OWL.Class);
        checkTriplePresence(child.asGraphModel(), objPropIRI.toResource(), RDF.type, OWL.ObjectProperty);
        checkTriplePresence(child.asGraphModel(), dataPropIRI.toResource(), RDF.type, OWL.DatatypeProperty);
        checkTriplePresence(child.asGraphModel(), annPropIRI.toResource(), RDF.type, OWL.AnnotationProperty);
        checkTripleAbsence(child.asGraphModel(), dataTypeIRI.toResource(), RDF.type, RDFS.Datatype);
    }

    @Test
    public void testSimpleImportsBehaviour() {
        OntologyManager m = OntManagers.createONT();
        String a_uri = "A";
        String b_uri = "B";
        OntologyModel a_owl = m.createOntology(IRI.create(a_uri));
        OntologyModel b_owl = m.createOntology(IRI.create(b_uri));

        OntGraphModel a = m.getGraphModel(a_uri);
        Assert.assertNotNull(a);
        OntGraphModel b = m.getGraphModel(b_uri);
        Assert.assertNotNull(b);
        a.addImport(b);

        Assert.assertTrue(a_owl.imports().anyMatch(o -> Objects.equals(o, b_owl)));

        LOGGER.info("Add class and associated individual");
        OntIndividual i = b.createOntEntity(OntClass.class, "class").createIndividual("individual");
        b_owl.axioms().forEach(x -> LOGGER.debug("{}", x));

        Set<OWLAxiom> b_axioms_1 = b_owl.axioms().collect(Collectors.toSet());
        Set<OWLAxiom> a_axioms_1 = a_owl.axioms(Imports.INCLUDED).collect(Collectors.toSet());
        Assert.assertEquals(3, b_axioms_1.size());
        Assert.assertEquals(0, a_owl.getAxiomCount());
        Assert.assertEquals(b_axioms_1, a_axioms_1);

        LOGGER.info("Remove individual (class assertion + declaration)");
        b.removeOntObject(i);
        b_owl.axioms().forEach(x -> LOGGER.debug("{}", x));

        Set<OWLAxiom> b_axioms_2 = b_owl.axioms().collect(Collectors.toSet());
        Set<OWLAxiom> a_axioms_2 = a_owl.axioms(Imports.INCLUDED).collect(Collectors.toSet());
        Assert.assertEquals(1, b_axioms_2.size());
        Assert.assertEquals(0, a_owl.getAxiomCount());
        Assert.assertEquals(b_axioms_2, a_axioms_2);
    }

    private static void checkTriple(OntGraphModel base, OntGraphModel child, Resource subject, Property predicate, RDFNode object) {
        checkTriplePresence(base, subject, predicate, object);
        checkTripleAbsence(child, subject, predicate, object);
    }

    private static void checkTriplePresence(OntGraphModel model, Resource subject, Property predicate, RDFNode object) {
        Triple t = createTriple(subject, predicate, object);
        Assert.assertTrue("Can't find the triple " + t, model.getBaseGraph().contains(t));
    }

    private static void checkTripleAbsence(OntGraphModel model, Resource subject, Property predicate, RDFNode object) {
        Triple t = createTriple(subject, predicate, object);
        Assert.assertFalse("There is the triple " + t, model.getBaseGraph().contains(t));
    }

    private static Triple createTriple(Resource r, Property p, RDFNode o) {
        return Triple.create(r.asNode(), p.asNode(), o.asNode());
    }
}
