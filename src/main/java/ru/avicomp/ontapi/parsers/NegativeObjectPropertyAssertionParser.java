package ru.avicomp.ontapi.parsers;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.vocabulary.OWL2;
import org.semanticweb.owlapi.model.OWLNegativeObjectPropertyAssertionAxiom;

/**
 * see {@link AbstractNegativePropertyAssertionParser}
 * example:
 * [ a owl:NegativePropertyAssertion; owl:sourceIndividual :ind1; owl:assertionProperty :objProp; owl:targetIndividual :ind2 ] .
 * Created by szuev on 12.10.2016.
 */
class NegativeObjectPropertyAssertionParser extends AbstractNegativePropertyAssertionParser<OWLNegativeObjectPropertyAssertionAxiom> {
    @Override
    public Property getTargetPredicate() {
        return OWL2.targetIndividual;
    }
}
