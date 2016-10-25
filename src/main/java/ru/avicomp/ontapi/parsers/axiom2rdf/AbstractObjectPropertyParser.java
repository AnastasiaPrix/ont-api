package ru.avicomp.ontapi.parsers.axiom2rdf;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.vocabulary.RDF;
import org.semanticweb.owlapi.model.HasProperty;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;

/**
 * base class for following parsers:
 * {@link ReflexiveObjectPropertyParser},
 * {@link IrreflexiveObjectPropertyParser},
 * {@link AsymmetricObjectPropertyParser},
 * {@link SymmetricObjectPropertyParser},
 * {@link TransitiveObjectPropertyParser},
 * {@link InverseFunctionalObjectPropertyParser},
 * Created by @szuev on 18.10.2016.
 */
abstract class AbstractObjectPropertyParser<Axiom extends OWLAxiom & HasProperty<? extends OWLObjectPropertyExpression>> extends AbstractSingleTripleParser<Axiom> {
    @Override
    public OWLObjectPropertyExpression getSubject() {
        return getAxiom().getProperty();
    }

    @Override
    public Property getPredicate() {
        return RDF.type;
    }
}
