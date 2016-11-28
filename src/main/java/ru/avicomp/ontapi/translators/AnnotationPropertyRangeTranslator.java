package ru.avicomp.ontapi.translators;

import java.util.Set;
import java.util.stream.Stream;

import org.apache.jena.rdf.model.Resource;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationPropertyRangeAxiom;

import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntNAP;
import ru.avicomp.ontapi.jena.model.OntPE;
import ru.avicomp.ontapi.jena.model.OntStatement;
import uk.ac.manchester.cs.owl.owlapi.OWLAnnotationPropertyRangeAxiomImpl;

/**
 * base class {@link AbstractPropertyRangeTranslator}
 * Note: OWL Axiom Type is "AnnotationPropertyRangeOf", not "AnnotationPropertyRange"
 * <p>
 * Created by @szuev on 30.09.2016.
 */
class AnnotationPropertyRangeTranslator extends AbstractPropertyRangeTranslator<OWLAnnotationPropertyRangeAxiom> {

    @Override
    Stream<OntStatement> statements(OntGraphModel model) {
        return statements(model, OntNAP.class);
    }

    @Override
    OWLAnnotationPropertyRangeAxiom create(OntPE property, Resource range, Set<OWLAnnotation> annotations) {
        OWLAnnotationProperty p = RDF2OWLHelper.getAnnotationProperty(property.as(OntNAP.class));
        IRI r = IRI.create(range.getURI());
        return new OWLAnnotationPropertyRangeAxiomImpl(p, r, annotations);
    }
}
