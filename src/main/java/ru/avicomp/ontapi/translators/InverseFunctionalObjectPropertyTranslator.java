package ru.avicomp.ontapi.translators;

import java.util.Set;

import org.apache.jena.rdf.model.Resource;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLInverseFunctionalObjectPropertyAxiom;

import ru.avicomp.ontapi.jena.model.OntOPE;
import ru.avicomp.ontapi.jena.model.OntStatement;
import ru.avicomp.ontapi.jena.vocabulary.OWL2;
import uk.ac.manchester.cs.owl.owlapi.OWLInverseFunctionalObjectPropertyAxiomImpl;

/**
 * example:
 * pizza:hasBase rdf:type owl:FunctionalProperty
 * <p>
 * Created by @szuev on 28.09.2016.
 */
class InverseFunctionalObjectPropertyTranslator extends AbstractPropertyTypeTranslator<OWLInverseFunctionalObjectPropertyAxiom, OntOPE> {
    @Override
    OWLInverseFunctionalObjectPropertyAxiom create(OntStatement statement, Set<OWLAnnotation> annotations) {
        return new OWLInverseFunctionalObjectPropertyAxiomImpl(RDF2OWLHelper.getObjectProperty(getSubject(statement)), annotations);
    }

    @Override
    Resource getType() {
        return OWL2.InverseFunctionalProperty;
    }

    @Override
    Class<OntOPE> getView() {
        return OntOPE.class;
    }
}
