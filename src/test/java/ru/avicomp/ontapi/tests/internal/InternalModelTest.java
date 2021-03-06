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

package ru.avicomp.ontapi.tests.internal;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDFS;
import org.hamcrest.core.IsEqual;
import org.junit.Assert;
import org.junit.Test;
import org.semanticweb.owlapi.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.ontapi.OntFormat;
import ru.avicomp.ontapi.OntManagers;
import ru.avicomp.ontapi.internal.AxiomParserProvider;
import ru.avicomp.ontapi.internal.InternalModel;
import ru.avicomp.ontapi.internal.InternalModelHolder;
import ru.avicomp.ontapi.internal.ONTObject;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.impl.conf.OntModelConfig;
import ru.avicomp.ontapi.jena.impl.conf.OntPersonality;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;
import ru.avicomp.ontapi.transforms.GraphTransformers;
import ru.avicomp.ontapi.utils.ReadWriteUtils;
import ru.avicomp.ontapi.utils.TestUtils;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * to test RDF->Axiom parsing
 * <p>
 * Created by @szuev on 27.11.2016.
 */
public class InternalModelTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(InternalModelTest.class);

    @Test
    public void testAxiomRead() {
        Model m = ReadWriteUtils.loadResourceTTLFile("ontapi/pizza.ttl");
        OntGraphModel model = OntModelFactory.createModel(m.getGraph());
        // 39 axiom types:
        Set<Class<? extends OWLAxiom>> types = AxiomType.AXIOM_TYPES.stream().map(AxiomType::getActualClass).collect(Collectors.toSet());

        types.forEach(view -> check(model, view));

        Map<OWLAxiom, Set<Triple>> axioms = types.stream()
                .flatMap(view -> AxiomParserProvider.get(view).axioms(model))
                .collect(Collectors.toMap(ONTObject::getObject, i -> i.triples().collect(Collectors.toSet())));

        LOGGER.debug("Recreate model");
        Model m2 = ModelFactory.createDefaultModel();
        model.getID().statements().forEach(m2::add);
        axioms.forEach((axiom, triples) -> triples.forEach(triple -> m2.getGraph().add(triple)));
        m2.setNsPrefixes(m.getNsPrefixMap());

        ReadWriteUtils.print(m2);
        Set<Statement> actual = m2.listStatements().toSet();
        Set<Statement> expected = m.listStatements().toSet();
        Assert.assertThat("Incorrect statements (actual=" + actual.size() + ", expected=" + expected.size() + ")", actual, IsEqual.equalTo(expected));
    }

    @Test
    public void testOntologyAnnotations() {
        OWLDataFactory factory = OntManagers.getDataFactory();

        InternalModel model = InternalModelHolder.createInternalModel(ReadWriteUtils.loadResourceTTLFile("ontapi/pizza.ttl").getGraph());

        Set<OWLAnnotation> annotations = model.listOWLAnnotations().collect(Collectors.toSet());
        annotations.forEach(x -> LOGGER.debug("{}", x));
        Assert.assertEquals("Incorrect annotations count", 4, annotations.size());

        LOGGER.debug("Create bulk annotation.");
        OWLAnnotation bulk = factory.getOWLAnnotation(factory.getRDFSLabel(), factory.getOWLLiteral("the label"),
                Stream.of(factory.getRDFSComment("just comment to ontology annotation")));
        model.add(bulk);
        annotations = model.listOWLAnnotations().collect(Collectors.toSet());
        annotations.forEach(x -> LOGGER.debug("{}", x));
        Assert.assertEquals("Incorrect annotations count", 5, annotations.size());

        LOGGER.debug("Create plain(assertion) annotation.");
        OWLAnnotation plain = factory.getOWLAnnotation(factory.getRDFSSeeAlso(), IRI.create("http://please.click.me/"));
        model.add(plain);
        annotations = model.listOWLAnnotations().collect(Collectors.toSet());
        annotations.forEach(x -> LOGGER.debug("{}", x));
        Assert.assertEquals("Incorrect annotations count", 6, annotations.size());

        LOGGER.debug("Remove annotations.");
        OWLAnnotation comment = annotations.stream().filter(a -> a.getProperty().getIRI().toString().equals(RDFS.comment.getURI())).findFirst().orElse(null);
        LOGGER.debug("Delete {}", bulk);
        model.remove(bulk);
        LOGGER.debug("Delete {}", comment);
        model.remove(comment);

        annotations = model.listOWLAnnotations().collect(Collectors.toSet());
        annotations.forEach(x -> LOGGER.debug("{}", x));
        Assert.assertEquals("Incorrect annotations count", 4, annotations.size());
    }

    @Test
    public void testPizzaEntities() {
        testEntities("ontapi/pizza.ttl", OntFormat.TURTLE);
    }

    @Test
    public void testFoafEntities() {
        String file = "ontapi/foaf.rdf";
        OntFormat format = OntFormat.RDF_XML;

        OntPersonality profile = OntModelConfig.getPersonality();
        OWLDataFactory factory = OntManagers.getDataFactory();

        OWLOntology owl = loadOWLOntology(file);
        InternalModel jena = loadInternalModel(file, format);
        debugPrint(jena, owl);

        test(OWLClass.class, jena.listOWLClasses(), owl.classesInSignature());
        test(OWLDatatype.class, jena.listOWLDatatypes(), owl.datatypesInSignature());
        test(OWLNamedIndividual.class, jena.listOWLNamedIndividuals(), owl.individualsInSignature());
        test(OWLAnonymousIndividual.class, jena.listOWLAnonymousIndividuals(), owl.anonymousIndividuals());
        Set<OWLAnnotationProperty> expectedAnnotationProperties = owl.annotationPropertiesInSignature().collect(Collectors.toSet());
        Set<OWLDataProperty> expectedDataProperties = owl.dataPropertiesInSignature().collect(Collectors.toSet());
        Set<OWLObjectProperty> expectedObjectProperties = owl.objectPropertiesInSignature().collect(Collectors.toSet());

        // <http://purl.org/dc/terms/creator> is owl:ObjectProperty since it is equivalent to <http://xmlns.com/foaf/0.1/maker>
        // see file <owl:equivalentProperty rdf:resource="http://purl.org/dc/terms/creator"/>
        // but OWL-API doesn't see it in entities list.
        OWLObjectProperty creator = factory.getOWLObjectProperty(IRI.create("http://purl.org/dc/terms/creator"));
        expectedObjectProperties.add(creator);

        OntModelConfig.StdMode mode = TestUtils.getMode(profile);
        // remove all illegal punnings from OWL-API output:
        Set<Resource> illegalPunnings = TestUtils.getIllegalPunnings(jena, mode);
        LOGGER.debug("Illegal punnings inside graph: {}", illegalPunnings);
        Set<OWLAnnotationProperty> illegalAnnotationProperties = illegalPunnings.stream().map(r -> r.inModel(jena))
                .filter(r -> r.hasProperty(RDF.type, OWL.AnnotationProperty))
                .map(Resource::getURI).map(IRI::create).map(factory::getOWLAnnotationProperty).collect(Collectors.toSet());
        Set<OWLDataProperty> illegalDataProperties = illegalPunnings.stream().map(r -> r.inModel(jena))
                .filter(r -> r.hasProperty(RDF.type, OWL.DatatypeProperty))
                .map(Resource::getURI).map(IRI::create).map(factory::getOWLDataProperty).collect(Collectors.toSet());
        Set<OWLObjectProperty> illegalObjectProperties = illegalPunnings.stream().map(r -> r.inModel(jena))
                .filter(r -> r.hasProperty(RDF.type, OWL.ObjectProperty))
                .map(Resource::getURI).map(IRI::create).map(factory::getOWLObjectProperty).collect(Collectors.toSet());
        expectedAnnotationProperties.removeAll(illegalAnnotationProperties);
        expectedDataProperties.removeAll(illegalDataProperties);
        expectedObjectProperties.removeAll(illegalObjectProperties);

        test(OWLDataProperty.class, jena.listOWLDataProperties(), expectedDataProperties.stream());
        test(OWLAnnotationProperty.class, jena.listOWLAnnotationProperties(), expectedAnnotationProperties.stream());
        test(OWLObjectProperty.class, jena.listOWLObjectProperties(), expectedObjectProperties.stream());
    }

    @Test
    public void testGoodrelationsEntities() {
        testEntities("ontapi/goodrelations.rdf", OntFormat.RDF_XML);
    }

    private static <Axiom extends OWLAxiom> void check(OntGraphModel model, Class<Axiom> view) {
        LOGGER.debug("=========================");
        LOGGER.debug("{}:", view.getSimpleName());
        AxiomParserProvider.get(view).axioms(model).forEach(e -> {
            Axiom axiom = e.getObject();
            Set<Triple> triples = e.triples().collect(Collectors.toSet());
            Assert.assertNotNull("Null axiom", axiom);
            Assert.assertTrue("No associated triples", !triples.isEmpty());
            LOGGER.debug("{} {}", axiom, triples);
        });
    }

    private void testEntities(String file, OntFormat format) {
        OWLOntology owl = loadOWLOntology(file);
        InternalModel jena = loadInternalModel(file, format);
        debugPrint(jena, owl);
        test(OWLClass.class, jena.listOWLClasses(), owl.classesInSignature());
        test(OWLDatatype.class, jena.listOWLDatatypes(), owl.datatypesInSignature());
        test(OWLNamedIndividual.class, jena.listOWLNamedIndividuals(), owl.individualsInSignature());
        test(OWLAnonymousIndividual.class, jena.listOWLAnonymousIndividuals(), owl.anonymousIndividuals());
        test(OWLAnnotationProperty.class, jena.listOWLAnnotationProperties(), owl.annotationPropertiesInSignature());
        test(OWLObjectProperty.class, jena.listOWLObjectProperties(), owl.objectPropertiesInSignature());
        test(OWLDataProperty.class, jena.listOWLDataProperties(), owl.dataPropertiesInSignature());
    }

    private void debugPrint(InternalModel jena, OWLOntology owl) {
        ReadWriteUtils.print(owl);
        LOGGER.debug("==============================");
        ReadWriteUtils.print(jena);
        LOGGER.debug("==============================");
    }

    private <T extends OWLObject> void test(Class<T> view, Stream<T> ont, Stream<T> owl) {
        LOGGER.debug("Test <{}>:", view.getSimpleName());
        List<T> actual = ont.sorted().collect(Collectors.toList());
        List<T> expected = owl.sorted().collect(Collectors.toList());
        LOGGER.debug("{} (owl, expected) ::: {} (ont, actual)", expected.size(), actual.size());
        if (OWLAnonymousIndividual.class.equals(view)) {
            Assert.assertEquals("Incorrect anonymous individuals count ", actual.size(), expected.size());
        } else {
            Assert.assertThat("Incorrect " + view.getSimpleName(), actual, IsEqual.equalTo(expected));
        }
    }

    private OWLOntology loadOWLOntology(String file) {
        URI fileURI = ReadWriteUtils.getResourceURI(file);
        OWLOntologyManager manager = OntManagers.createOWL();
        LOGGER.debug("Load pure owl from {}", fileURI);
        return ReadWriteUtils.loadOWLOntology(manager, IRI.create(fileURI));
    }

    private InternalModel loadInternalModel(String file, OntFormat format) {
        URI fileURI = ReadWriteUtils.getResourceURI(file);
        LOGGER.debug("Load jena model from {}", fileURI);
        Model init = ReadWriteUtils.load(fileURI, format);
        Graph graph = GraphTransformers.convert(init.getGraph());
        return InternalModelHolder.createInternalModel(graph);
    }

}
