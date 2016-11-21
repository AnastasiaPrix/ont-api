package ru.avicomp.ontapi.translators;

import org.apache.jena.graph.Graph;
import org.apache.jena.vocabulary.OWL;
import org.semanticweb.owlapi.model.OWLDatatypeDefinitionAxiom;

/**
 * example:
 * :data-type-3 rdf:type rdfs:Datatype ; owl:equivalentClass [ rdf:type rdfs:Datatype ; owl:unionOf ( :data-type-1  :data-type-2 ) ] .
 * <p>
 * Created by @szuev on 18.10.2016.
 */
class DatatypeDefinitionTranslator extends AxiomTranslator<OWLDatatypeDefinitionAxiom> {
    @Override
    public void write(OWLDatatypeDefinitionAxiom axiom, Graph graph) {
        TranslationHelper.processAnnotatedTriple(graph, axiom.getDatatype(), OWL.equivalentClass, axiom.getDataRange(), axiom, true);
    }
}
