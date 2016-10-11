package ru.avicomp.ontapi.parsers;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;

/**
 * Examples:
 * pizza:JalapenoPepperTopping rdfs:subClassOf pizza:PepperTopping.
 * pizza:JalapenoPepperTopping rdfs:subClassOf [ a owl:Restriction; owl:onProperty pizza:hasSpiciness; owl:someValuesFrom pizza:Hot].
 * <p>
 * Created by @szuev on 28.09.2016.
 */
class SubClassOfParser extends AxiomParser<OWLSubClassOfAxiom> {
    @Override
    public void translate(Graph graph) {
        Model model = ModelFactory.createModelForGraph(graph);
        Resource subject = AxiomParseUtils.toResource(model, getAxiom().getSubClass());
        subject.inModel(model);
        model.add(subject, RDFS.subClassOf, AxiomParseUtils.toResource(model, getAxiom().getSuperClass()));
    }
}
