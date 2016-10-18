package ru.avicomp.ontapi.parsers;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.semanticweb.owlapi.model.OWLDeclarationAxiom;

/**
 * simple triplet with rdf:type predicate.
 * <p>
 * Created by @szuev on 28.09.2016.
 */
class DeclarationParser extends AbstractSingleTripleParser<OWLDeclarationAxiom> {
    @Override
    public Resource getSubject() {
        return TranslationHelper.toResource(getAxiom().getEntity());
    }

    @Override
    public Property getPredicate() {
        return RDF.type;
    }

    @Override
    public RDFNode getObject() {
        return TranslationHelper.getType(getAxiom().getEntity());
    }
}
