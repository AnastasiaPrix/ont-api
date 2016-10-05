package ru.avicomp.ontapi.parsers;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.semanticweb.owlapi.model.OWLAxiom;

/**
 * Class for parse axiom which is related to single triplet.
 * <p>
 * Created by @szuev on 28.09.2016.
 */
abstract class SingleTripletParser<Axiom extends OWLAxiom> extends AxiomParser<Axiom> {

    public abstract Resource getSubject();

    public abstract Property getPredicate();

    public abstract RDFNode getObject();

    @Override
    public void process(Graph graph) {
            graph.add(Triple.create(getSubject().asNode(), getPredicate().asNode(), getObject().asNode()));
    }

    @Override
    public void reverse(Graph graph) {
        graph.remove(getSubject().asNode(), getPredicate().asNode(), getObject().asNode());
    }
}
