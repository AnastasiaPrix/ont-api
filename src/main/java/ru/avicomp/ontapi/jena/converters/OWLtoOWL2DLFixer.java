package ru.avicomp.ontapi.jena.converters;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.*;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDFS;

import ru.avicomp.ontapi.jena.utils.Streams;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

/**
 * To convert OWL 1 DL => OWL 2 DL
 *
 * See <a href='https://www.w3.org/TR/owl2-mapping-to-rdf/#Mapping_from_RDF_Graphs_to_the_Structural_Specification'>Chapter 3</a>
 * alse <a href='https://www.w3.org/TR/owl2-quick-reference/'>4.2 Additional Vocabulary in OWL 2 RDF Syntax</a>
 *
 */
public class OWLtoOWL2DLFixer extends TransformAction {
    public OWLtoOWL2DLFixer(Graph graph) {
        super(graph);
    }

    @Override
    public void perform() {
        // table 5:
        Stream.of(OWL.DataRange, RDFS.Datatype, OWL.Restriction, OWL.Class)
                .forEach(type -> findByType(type)
                        .forEachRemaining(node -> deleteType(node, RDFS.Class)));

        // table 5:
        Stream.of(OWL.ObjectProperty, OWL.FunctionalProperty, OWL.InverseFunctionalProperty,
                OWL.TransitiveProperty, OWL.DatatypeProperty, OWL.AnnotationProperty, OWL.OntologyProperty)
                .forEach(type -> findByType(type)
                        .forEachRemaining(node -> deleteType(node, RDF.Property)));

        // definitely ObjectProperty (table 6, supplemented by owl:AsymmetricProperty, owl:ReflexiveProperty, owl:IrreflexiveProperty):
        Stream.of(OWL.InverseFunctionalProperty, OWL.TransitiveProperty, OWL.SymmetricProperty
                , OWL.AsymmetricProperty, OWL.ReflexiveProperty, OWL.IrreflexiveProperty
        ).forEach(type -> findByType(type)
                .forEachRemaining(node -> addType(node, OWL.ObjectProperty)));

        // replace owl:OntologyProperty with owl:AnnotationProperty (table 6)
        replaceType(OWL.OntologyProperty, OWL.AnnotationProperty);
        // replace owl:DataRange(as deprecated) with rdfs:Datatype (see quick-reference guide)
        replaceType(OWL.DataRange, RDFS.Datatype);

        fixOntology();
        fixAxioms();
    }

    /**
     * merge several owl:Ontology to single one.
     * as primary choose the one that has the largest number of triplets.
     * if there is no any owl:Ontology -> add new anonymous owl:Ontology
     */
    private void fixOntology() {
        Model m = getBaseModel();
        // choose or create the new one:
        Resource ontology = Streams.asStream(m.listStatements(null, RDF.type, OWL.Ontology))
                .map(Statement::getSubject)
                .sorted(Comparator.comparing(this::statementsCount).reversed())
                .findFirst()
                .orElse(m.createResource().addProperty(RDF.type, OWL.Ontology));
        // move all content from other ontologies to the selected one
        Stream<Resource> other = Streams.asStream(m.listStatements(null, RDF.type, OWL.Ontology)
                .mapWith(Statement::getSubject)
                .filterDrop(ontology::equals));
        List<Statement> rest = other
                .map(o -> Streams.asStream(m.listStatements(o, null, (RDFNode) null)))
                .flatMap(Function.identity()).collect(Collectors.toList());
        rest.forEach(s -> ontology.addProperty(s.getPredicate(), s.getObject()));
        // remove all other ontologies
        m.remove(rest);
    }

    /**
     * see <a href='https://www.w3.org/TR/owl2-mapping-to-rdf/#Parsing_of_Axioms'>Chapter 3.2.5, Table 18</a>
     * Warning: there is also an interpretation of empty rdf:List as owl:Nothing or owl:Thing in table.
     * But I don't see that OWL-API works such way (it treats rdf:nil as just an empty collection).
     * So we skip also fixing for this case.
     * Plus - it works currently only for named owl:Class resources.
     */
    private void fixAxioms() {
        Stream.of(OWL.complementOf, OWL.unionOf, OWL.intersectionOf, OWL.oneOf)
                .forEach(property -> {
                    Set<Resource> classes = listStatements(null, property, null)
                            .map(Statement::getSubject)
                            .filter(RDFNode::isURIResource)
                            .collect(Collectors.toSet());
                    classes.forEach(c -> moveToEquivalentClass(c, property));
                });
    }

    private void moveToEquivalentClass(Resource subject, Property predicate) {
        List<Statement> statements = listStatements(subject, predicate, null).collect(Collectors.toList());
        Model m = getBaseModel();
        Resource newRoot = m.createResource();
        newRoot.addProperty(RDF.type, OWL.Class);
        m.add(subject, OWL.equivalentClass, newRoot);
        statements.forEach(s -> newRoot.addProperty(s.getPredicate(), s.getObject()));
        m.remove(statements);
    }

    private Integer statementsCount(Resource subject) {
        return (int) Streams.asStream(subject.listProperties()).count();
    }

/*    @Override
    public boolean test() {
        return !containsType(OWL.Ontology) || containsType(RDFS.Class) || containsType(RDF.Property) || containsType(OWL.OntologyProperty);
    }*/

    private ExtendedIterator<Node> findByType(Resource type) {
        return getBaseGraph().find(Node.ANY, RDF_TYPE, type.asNode()).mapWith(Triple::getSubject);
    }
}
