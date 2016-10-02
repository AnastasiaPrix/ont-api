package ru.avicomp.ontapi.parsers;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.semanticweb.owlapi.model.HasProperty;
import org.semanticweb.owlapi.model.OWLAxiom;

/**
 * base class for {@link FunctionalObjectPropertyParser} and {@link FunctionalDataPropertyParser}
 * <p>
 * Created by @szuev on 30.09.2016.
 */
abstract class AbstractFunctionalPropertyParser<Axiom extends OWLAxiom & HasProperty> extends SingleTripletParser<Axiom> {
    @Override
    public Resource getSubject() {
        return ParseUtils.toResource(getAxiom().getProperty());
    }

    @Override
    public Property getPredicate() {
        return RDF.type;
    }

    @Override
    public RDFNode getObject() {
        return OWL.FunctionalProperty;
    }
}
