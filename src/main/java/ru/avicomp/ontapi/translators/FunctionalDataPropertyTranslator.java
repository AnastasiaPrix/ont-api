package ru.avicomp.ontapi.translators;

import org.apache.jena.rdf.model.Resource;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLFunctionalDataPropertyAxiom;

import ru.avicomp.ontapi.jena.model.OntNDP;
import ru.avicomp.ontapi.jena.model.OntStatement;
import ru.avicomp.ontapi.jena.vocabulary.OWL;

/**
 * example:
 * foaf:gender rdf:type owl:DatatypeProperty , owl:FunctionalProperty ;
 * <p>
 * Created by @szuev on 30.09.2016.
 */
class FunctionalDataPropertyTranslator extends AbstractPropertyTypeTranslator<OWLFunctionalDataPropertyAxiom, OntNDP> {
    @Override
    Resource getType() {
        return OWL.FunctionalProperty;
    }

    @Override
    Class<OntNDP> getView() {
        return OntNDP.class;
    }

    @Override
    Wrap<OWLFunctionalDataPropertyAxiom> asAxiom(OntStatement statement) {
        Wrap<OWLDataProperty> p = ReadHelper.getDataProperty(getSubject(statement), getDataFactory());
        Wrap.Collection<OWLAnnotation> annotations = ReadHelper.getStatementAnnotations(statement, getDataFactory());
        OWLFunctionalDataPropertyAxiom res = getDataFactory().getOWLFunctionalDataPropertyAxiom(p.getObject(), annotations.getObjects());
        return Wrap.create(res, statement).add(annotations.getTriples()).append(p);
    }
}
