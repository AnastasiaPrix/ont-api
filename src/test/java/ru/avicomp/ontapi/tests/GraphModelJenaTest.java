package ru.avicomp.ontapi.tests;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.jena.rdf.model.*;
import org.apache.jena.util.iterator.UniqueFilter;
import org.apache.jena.vocabulary.RDFS;
import org.apache.log4j.Logger;
import org.hamcrest.core.IsEqual;
import org.junit.Assert;
import org.junit.Test;

import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.impl.OntCEImpl;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;
import ru.avicomp.ontapi.jena.vocabulary.SWRL;
import ru.avicomp.ontapi.jena.vocabulary.XSD;
import ru.avicomp.ontapi.utils.ReadWriteUtils;
import ru.avicomp.ontapi.utils.TestUtils;

/**
 * to test {@link OntGraphModel}
 *
 * Created by szuev on 07.11.2016.
 */
public class GraphModelJenaTest {
    private static final Logger LOGGER = Logger.getLogger(GraphModelJenaTest.class);

    @Test
    public void testLoadCE() {
        LOGGER.info("load pizza");
        OntGraphModel m = OntModelFactory.createModel(ReadWriteUtils.loadResourceTTLFile("pizza.ttl").getGraph());
        LOGGER.info("Ontology: " + m.getID());

        List<OntClass> classes = m.ontObjects(OntClass.class).collect(Collectors.toList());
        int expectedClassesCount = m.listStatements(null, RDF.type, OWL.Class).mapWith(Statement::getSubject).filterKeep(RDFNode::isURIResource).toSet().size();
        int actualClassesCount = classes.size();
        LOGGER.info("Classes Count = " + actualClassesCount);
        Assert.assertEquals("Incorrect Classes count", expectedClassesCount, actualClassesCount);

        LOGGER.info("Class Expressions:");
        List<OntCE> ces = m.ontObjects(OntCE.class).collect(Collectors.toList());
        ces.forEach(LOGGER::debug);
        int expectedCEsCount = m.listStatements(null, RDF.type, OWL.Class).andThen(m.listStatements(null, RDF.type, OWL.Restriction)).toSet().size();
        int actualCEsCount = ces.size();
        LOGGER.info("Class Expressions Count = " + actualCEsCount);
        Assert.assertEquals("Incorrect CE's count", expectedCEsCount, actualCEsCount);

        List<OntCE.RestrictionCE> restrictionCEs = m.ontObjects(OntCE.RestrictionCE.class).collect(Collectors.toList());
        Assert.assertEquals("Incorrect count of restrictions ", m.listStatements(null, RDF.type, OWL.Restriction).toSet().size(), restrictionCEs.size());

        List<OntCE.ObjectSomeValuesFrom> objectSomeValuesFromCEs = m.ontObjects(OntCE.ObjectSomeValuesFrom.class).collect(Collectors.toList());
        List<OntCE.ObjectAllValuesFrom> objectAllValuesFromCEs = m.ontObjects(OntCE.ObjectAllValuesFrom.class).collect(Collectors.toList());
        List<OntCE.ObjectHasValue> objectHasValueCEs = m.ontObjects(OntCE.ObjectHasValue.class).collect(Collectors.toList());
        List<OntCE.UnionOf> unionOfCEs = m.ontObjects(OntCE.UnionOf.class).collect(Collectors.toList());
        List<OntCE.IntersectionOf> intersectionOfCEs = m.ontObjects(OntCE.IntersectionOf.class).collect(Collectors.toList());
        List<OntCE.ComplementOf> complementOfCEs = m.ontObjects(OntCE.ComplementOf.class).collect(Collectors.toList());
        List<OntCE.OneOf> oneOfCEs = m.ontObjects(OntCE.OneOf.class).collect(Collectors.toList());
        List<OntCE.ObjectMinCardinality> objectMinCardinalityCEs = m.ontObjects(OntCE.ObjectMinCardinality.class).collect(Collectors.toList());

        testPizzaCEs(m, OWL.someValuesFrom, objectSomeValuesFromCEs);
        testPizzaCEs(m, OWL.allValuesFrom, objectAllValuesFromCEs);
        testPizzaCEs(m, OWL.hasValue, objectHasValueCEs);
        testPizzaCEs(m, OWL.unionOf, unionOfCEs);
        testPizzaCEs(m, OWL.intersectionOf, intersectionOfCEs);
        testPizzaCEs(m, OWL.complementOf, complementOfCEs);
        testPizzaCEs(m, OWL.oneOf, oneOfCEs);
        testPizzaCEs(m, OWL.minCardinality, objectMinCardinalityCEs);
    }

    @Test
    public void testLoadProperties() {
        LOGGER.info("load pizza");
        OntGraphModel m = OntModelFactory.createModel(ReadWriteUtils.loadResourceTTLFile("pizza.ttl").getGraph());
        List<OntPE> actual = m.ontObjects(OntPE.class).collect(Collectors.toList());
        actual.forEach(LOGGER::debug);
        Set<Resource> expected = new HashSet<>();
        Stream.of(OWL.AnnotationProperty, OWL.DatatypeProperty, OWL.ObjectProperty)
                .forEach(r -> expected.addAll(m.listStatements(null, RDF.type, r).mapWith(Statement::getSubject).toSet()));
        Assert.assertEquals("Incorrect number of properties", expected.size(), actual.size());
    }

    @Test
    public void testLoadIndividuals() {
        LOGGER.info("load pizza");
        OntGraphModel m = OntModelFactory.createModel(ReadWriteUtils.loadResourceTTLFile("pizza.ttl").getGraph());
        List<OntIndividual> individuals = m.ontObjects(OntIndividual.class).collect(Collectors.toList());
        individuals.forEach(i -> LOGGER.debug(i + " classes: " + i.classes().collect(Collectors.toSet())));

        Set<Resource> namedIndividuals = m.listSubjectsWithProperty(RDF.type, OWL.NamedIndividual).toSet();
        Set<Resource> anonIndividuals = m.listStatements(null, RDF.type, (RDFNode) null)
                .filterKeep(s -> s.getSubject().isAnon())
                .filterKeep(s -> s.getObject().isResource() && m.contains(s.getObject().asResource(), RDF.type, OWL.Class))
                .mapWith(Statement::getSubject).toSet();
        Set<Resource> expected = new HashSet<>(namedIndividuals);
        expected.addAll(anonIndividuals);
        Assert.assertEquals("Incorrect number of individuals", expected.size(), individuals.size());
    }

    private static void testPizzaCEs(Model m, Property predicate, List<? extends OntCE> ces) {
        String type = ces.isEmpty() ? null : ((OntCEImpl) ces.get(0)).getActualClass().getSimpleName();
        Assert.assertEquals("Incorrect count of " + type, m.listSubjectsWithProperty(predicate).toSet().size(), ces.size());
    }

    @Test
    public void testCreatePlainAnnotations() {
        String uri = "http://test.com/graph/1";
        String ns = uri + "#";

        OntGraphModel m = OntModelFactory.createModel();
        m.setNsPrefix("test", ns);
        TestUtils.setDefaultPrefixes(m);

        LOGGER.info("1) Assign version-iri and ontology comment.");
        m.setID(uri).setVersionIRI(ns + "1.0.1");
        m.getID().addComment("Some comment", "fr");
        m.getID().annotations().forEach(LOGGER::debug);
        Assert.assertEquals("Should be one header annotation", 1, m.getID().annotations().count());

        LOGGER.info("2) Create class with two labels.");
        OntClass cl = m.createOntEntity(OntClass.class, ns + "ClassN1");
        cl.addLabel("some label", null);
        OntStatement label2 = cl.addLabel("another label", "de");
        ReadWriteUtils.print(m);
        cl.annotations().forEach(LOGGER::debug);
        Assert.assertEquals("Incorrect count of labels.", 2, m.listObjectsOfProperty(cl, RDFS.label).toList().size());

        LOGGER.info("3) Annotate annotation " + label2);
        OntStatement seeAlsoForLabel2 = label2.addAnnotation(m.getAnnotationProperty(RDFS.seeAlso), ResourceFactory.createResource("http://see.also/1"));
        OntStatement labelForLabel2 = label2.addAnnotation(m.getRDFSLabel(), ResourceFactory.createPlainLiteral("label"));
        ReadWriteUtils.print(m);
        cl.annotations().forEach(LOGGER::debug);
        Assert.assertTrue("Can't find owl:Axiom section.", m.contains(null, RDF.type, OWL.Axiom));
        Assert.assertFalse("There is owl:Annotation section.", m.contains(null, RDF.type, OWL.Annotation));

        LOGGER.info("4) Create annotation property and annotate " + seeAlsoForLabel2 + " and " + labelForLabel2);
        OntNAP nap1 = m.createOntEntity(OntNAP.class, ns + "annotation-prop-1");
        seeAlsoForLabel2.addAnnotation(nap1, ResourceFactory.createPlainLiteral("comment to see also"));
        OntStatement annotationForLabelForLabel2 = labelForLabel2.addAnnotation(nap1, ResourceFactory.createPlainLiteral("comment to see label"));
        ReadWriteUtils.print(m);
        Assert.assertEquals("Expected two roots with owl:Annotation.", 2, m.listStatements(null, RDF.type, OWL.Annotation)
                .filterKeep(s -> !m.contains(null, null, s.getSubject())).filterKeep(new UniqueFilter<>()).toList().size());
        Assert.assertEquals("Expected two owl:Annotation.", 2, m.listStatements(null, RDF.type, OWL.Annotation).toList().size());
        Assert.assertEquals("Expected single owl:Axiom.", 1, m.listStatements(null, RDF.type, OWL.Axiom).toList().size());

        LOGGER.info("5) Delete annotations for " + labelForLabel2);
        labelForLabel2.deleteAnnotation(annotationForLabelForLabel2.getPredicate().as(OntNAP.class), annotationForLabelForLabel2.getObject());
        ReadWriteUtils.print(m);
        Assert.assertEquals("Expected one root with owl:Annotation.", 1, m.listStatements(null, RDF.type, OWL.Annotation)
                .filterKeep(s -> !m.contains(null, null, s.getSubject())).filterKeep(new UniqueFilter<>()).toList().size());
        Assert.assertEquals("Expected single owl:Annotation.", 1, m.listStatements(null, RDF.type, OWL.Annotation).toList().size());
        Assert.assertEquals("Expected single owl:Axiom.", 1, m.listStatements(null, RDF.type, OWL.Axiom).toList().size());


        LOGGER.info("6) Delete all annotations for " + label2);
        label2.clearAnnotations();
        ReadWriteUtils.print(m);
        Assert.assertEquals("Incorrect count of labels.", 2, m.listObjectsOfProperty(cl, RDFS.label).toList().size());
        Assert.assertFalse("There is owl:Axiom", m.contains(null, RDF.type, OWL.Axiom));
        Assert.assertFalse("There is owl:Annotation", m.contains(null, RDF.type, OWL.Annotation));

        LOGGER.info("7) Annotate sub-class-of");
        OntStatement subClassOf = cl.addSubClassOf(m.getOWLThing());
        OntStatement subClassOfAnnotation = subClassOf
                .addAnnotation(nap1, ResourceFactory.createPlainLiteral("test"));
        subClassOfAnnotation.addAnnotation(m.getRDFSLabel(), ResourceFactory.createPlainLiteral("test2"))
                .addAnnotation(m.getRDFSComment(), ResourceFactory.createPlainLiteral("test3"));

        ReadWriteUtils.print(m);
        cl.annotations().forEach(LOGGER::debug);
        Assert.assertEquals("Expected two owl:Annotation.", 2, m.listStatements(null, RDF.type, OWL.Annotation).toList().size());
        Assert.assertEquals("Expected single owl:Axiom.", 1, m.listStatements(null, RDF.type, OWL.Axiom).toList().size());
        Assert.assertEquals("Expected 3 root annotations for class " + cl, 3, cl.annotations().count());

        LOGGER.info("8) Deleter all annotations for class " + cl);
        cl.clearAnnotations();
        ReadWriteUtils.print(m);
        cl.annotations().forEach(LOGGER::debug);
        Assert.assertEquals("Found annotations for class " + cl, 0, cl.annotations().count());
        Assert.assertFalse("There is owl:Axiom", m.contains(null, RDF.type, OWL.Axiom));
        Assert.assertFalse("There is owl:Annotation", m.contains(null, RDF.type, OWL.Annotation));
    }

    @Test
    public void testCreateAnonAnnotations() {
        String uri = "http://test.com/graph/2";
        String ns = uri + "#";

        OntGraphModel m = OntModelFactory.createModel();
        m.setNsPrefix("test", ns);
        TestUtils.setDefaultPrefixes(m);
        m.setID(uri);

        OntClass cl1 = m.createOntEntity(OntClass.class, ns + "Class1");
        OntClass cl2 = m.createOntEntity(OntClass.class, ns + "Class2");
        OntClass cl3 = m.createOntEntity(OntClass.class, ns + "Class3");
        OntNAP nap1 = m.createOntEntity(OntNAP.class, ns + "AnnotationProperty1");

        OntDisjoint.Classes disjointClasses = m.createDisjointClasses(Arrays.asList(cl1, cl2, cl3));
        Assert.assertEquals("Incorrect owl:AllDisjointClasses number", 1, m.ontObjects(OntDisjoint.Classes.class).count());

        disjointClasses.addLabel("label1", "en");
        disjointClasses.addLabel("comment", "kjpopo").addAnnotation(nap1, ResourceFactory.createTypedLiteral("some txt"));
        ReadWriteUtils.print(m);
        Assert.assertEquals("Expected two assertions", 2, disjointClasses.as(OntAnnotation.class).assertions().count());
        Assert.assertEquals("Expected three annotations", 3, disjointClasses.as(OntAnnotation.class).annotations().count());

        Assert.assertFalse("There is owl:Axiom", m.contains(null, RDF.type, OWL.Axiom));
        Assert.assertEquals("Should be single owl:Annotation", 1, m.listStatements(null, RDF.type, OWL.Annotation).toList().size());

        OntNOP nop1 = m.createOntEntity(OntNOP.class, ns + "ObjectProperty1");
        OntIndividual.Named ind1 = cl1.createIndividual(ns + "Individual1");
        OntIndividual.Anonymous ind2 = cl2.createIndividual();
        ind2.addComment("anonymous individual", "ru");
        OntNPA.ObjectAssertion nopa = nop1.addNegativeAssertion(ind1, ind2);
        Assert.assertEquals("Incorrect owl:NegativePropertyAssertion number", 1, nop1.negativeAssertions().count());
        nopa.addLabel("label1", null)
                .addAnnotation(m.getRDFSLabel(), ResourceFactory.createTypedLiteral("label2"))
                .addAnnotation(m.getRDFSLabel(), ResourceFactory.createPlainLiteral("label3"));
        Assert.assertEquals("Should be 3 owl:Annotation", 3, m.listStatements(null, RDF.type, OWL.Annotation).toList().size());

        ReadWriteUtils.print(m);

        Assert.assertEquals("Should only be two roots", 2, m.ontObjects(OntAnnotation.class).count());
        OntStatement disjointWith = cl1.addDisjointWith(cl3);
        Assert.assertFalse("No annotation resource is expected.", disjointWith.asAnnotationResource().isPresent());
        disjointWith.addAnnotation(m.getAnnotationProperty(OWL.deprecated), "disjoint with comment N1", null)
                .addAnnotation(m.getAnnotationProperty(OWL.incompatibleWith), "disjoint with comment N2", "rur");
        ReadWriteUtils.print(m);
        Assert.assertTrue("Should be annotation resource", disjointWith.asAnnotationResource().isPresent());
        Assert.assertEquals("Should only be three roots", 3, m.ontObjects(OntAnnotation.class).count());
    }

    @Test
    public void testCreateExpressions() {
        String uri = "http://test.com/graph/3";
        String ns = uri + "#";

        OntGraphModel m = OntModelFactory.createModel();
        m.setNsPrefix("test", ns);
        TestUtils.setDefaultPrefixes(m);
        m.setID(uri);

        OntNDP ndp1 = m.createOntEntity(OntNDP.class, ns + "dataProperty1");
        OntDT dt1 = m.createOntEntity(OntDT.class, ns + "dataType1");
        dt1.addEquivalentClass(m.getOntEntity(OntDT.class, XSD.dateTime));

        OntDT dt2 = m.createOntEntity(OntDT.class, ns + "dataType2");

        OntFR fr1 = m.createFacetRestriction(OntFR.MaxExclusive.class, ResourceFactory.createTypedLiteral(12));
        OntFR fr2 = m.createFacetRestriction(OntFR.LangRange.class, ResourceFactory.createTypedLiteral("\\d+"));

        OntDR dr1 = m.createRestrictionDataRange(dt1, Arrays.asList(fr1, fr2));

        OntCE ce1 = m.createDataSomeValuesFrom(ndp1, dr1);

        OntDR dr2 = m.createIntersectionOfDataRange(Arrays.asList(dt1, dt2));
        OntIndividual i1 = ce1.createIndividual(ns + "individual1");
        OntCE ce2 = m.createDataMaxCardinality(ndp1, 343434, dr2);
        i1.attachClass(ce2);
        i1.attachClass(m.createOntEntity(OntClass.class, ns + "Class1"));

        OntIndividual i2 = ce2.createIndividual();
        i2.addStatement(ndp1, ResourceFactory.createPlainLiteral("individual value"));

        ReadWriteUtils.print(m);
        Assert.assertEquals("Incorrect count of individuals", 2, m.ontObjects(OntIndividual.class).count());
        Assert.assertEquals("Incorrect count of class expressions", 3, m.ontObjects(OntCE.class).count());
        Assert.assertEquals("Incorrect count of restrictions", 2, m.ontObjects(OntCE.RestrictionCE.class).count());
        Assert.assertEquals("Incorrect count of cardinality restrictions", 1, m.ontObjects(OntCE.CardinalityRestrictionCE.class).count());
        Assert.assertEquals("Incorrect count of datatype entities", 2, m.ontObjects(OntDT.class).count());
        Assert.assertEquals("Incorrect count of data properties", 1, m.ontObjects(OntNDP.class).count());
        Assert.assertEquals("Incorrect count of facet restrictions", 2, m.ontObjects(OntFR.class).count());
        Assert.assertEquals("Incorrect count of data ranges", 4, m.ontObjects(OntDR.class).count());
        Assert.assertEquals("Incorrect count of entities", 5, m.ontObjects(OntEntity.class).count());
    }

    @Test
    public void testCreateSWRL() {
        String uri = "http://test.com/graph/4";
        String ns = uri + "#";

        OntGraphModel m = OntModelFactory.createModel();
        m.setID(uri);
        m.setNsPrefix("test", ns);
        m.setNsPrefix("SWRL", SWRL.NS);
        TestUtils.setDefaultPrefixes(m);

        OntClass cl1 = m.createOntEntity(OntClass.class, ns + "Class1");
        OntClass cl2 = m.createOntEntity(OntClass.class, ns + "Class2");
        OntIndividual i1 = cl1.createIndividual(ns + "Individual1");

        OntCE.UnionOf cl3 = m.createUnionOf(Arrays.asList(cl1, cl2));
        OntIndividual i2 = cl3.createIndividual();

        OntSWRL.Variable var1 = m.createSWRLVariable(ns + "Variable1");
        OntSWRL.DArg dArg1 = ResourceFactory.createTypedLiteral(12).inModel(m).as(OntSWRL.DArg.class);
        OntSWRL.DArg dArg2 = var1.as(OntSWRL.DArg.class);

        OntSWRL.Atom.BuiltIn atom1 = m.createBuiltInSWRLAtom(ResourceFactory.createResource(ns + "AtomPredicate1"), Arrays.asList(dArg1, dArg2));
        OntSWRL.Atom.OntClass atom2 = m.createClassSWRLAtom(cl2, i2.as(OntSWRL.IArg.class));
        OntSWRL.Atom.SameIndividuals atom3 = m.createSameIndividualsSWRLAtom(i1.as(OntSWRL.IArg.class), var1.as(OntSWRL.IArg.class));
        OntSWRL.Imp imp = m.createSWRLImp(Collections.singletonList(atom1), Arrays.asList(atom2, atom3));
        imp.addComment("This is SWRL Imp", null).addAnnotation(m.getRDFSLabel(), cl1.createIndividual());

        ReadWriteUtils.print(m);
        LOGGER.debug("All D-Args");
        m.ontObjects(OntSWRL.DArg.class).forEach(LOGGER::debug);
        LOGGER.debug("All I-Args");
        m.ontObjects(OntSWRL.IArg.class).forEach(LOGGER::debug);
        Assert.assertEquals("Incorrect count of atoms", 3, m.ontObjects(OntSWRL.Atom.class).count());
        Assert.assertEquals("Incorrect count of variables", 1, m.ontObjects(OntSWRL.Variable.class).count());
        Assert.assertEquals("Incorrect count of SWRL:Imp", 1, m.ontObjects(OntSWRL.Imp.class).count());
        Assert.assertEquals("Incorrect count of SWRL Objects", 5, m.ontObjects(OntSWRL.class).count());
        // literals(2) and variables(1):
        Assert.assertEquals("Incorrect count of SWRL D-Arg", 3, m.ontObjects(OntSWRL.DArg.class).count());
        // individuals(2 anonymous, 1 named) and variables(1):
        Assert.assertEquals("Incorrect count of SWRL I-Arg", 4, m.ontObjects(OntSWRL.IArg.class).count());
    }

    @Test
    public void testCreateImports() {
        String baseURI = "http://test.com/graph/5";
        String baseNS = baseURI + "#";
        OntGraphModel base = OntModelFactory.createModel();
        TestUtils.setDefaultPrefixes(base);
        base.setID(baseURI);
        OntClass cl1 = base.createOntEntity(OntClass.class, baseNS + "Class1");
        OntClass cl2 = base.createOntEntity(OntClass.class, baseNS + "Class2");

        String childURI = "http://test.com/graph/6";
        String childNS = childURI + "#";
        OntGraphModel child = OntModelFactory.createModel();
        TestUtils.setDefaultPrefixes(child);
        child.setID(childURI);
        child.addImport(base);
        OntClass cl3 = child.createOntEntity(OntClass.class, childNS + "Class3");
        cl3.addSubClassOf(child.createIntersectionOf(Arrays.asList(cl1, cl2)));
        cl3.createIndividual(childNS + "Individual1");

        LOGGER.info("Base:");
        base = child.imports().findFirst().orElse(null);
        Assert.assertNotNull("Null base", base);
        ReadWriteUtils.print(base);
        LOGGER.info("Child:");
        ReadWriteUtils.print(child);
        Set<String> imports = child.getID().imports().collect(Collectors.toSet());
        Assert.assertThat("Incorrect imports", imports, IsEqual.equalTo(Stream.of(baseURI).collect(Collectors.toSet())));
        Assert.assertEquals("Incorrect count of entities", 4, child.ontEntities().count());
        Assert.assertEquals("Incorrect count of local entities", 2, child.ontEntities().filter(OntEntity::isLocal).count());
    }

}

