package ru.avicomp.ontapi.translators;

import java.util.Set;

import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLObjectPropertyDomainAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;

import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntOPE;
import ru.avicomp.ontapi.jena.model.OntStatement;
import uk.ac.manchester.cs.owl.owlapi.OWLObjectPropertyDomainAxiomImpl;

/**
 * see {@link AbstractPropertyDomainTranslator}
 * <p>
 * Created by @szuev on 29.09.2016.
 */
class ObjectPropertyDomainTranslator extends AbstractPropertyDomainTranslator<OWLObjectPropertyDomainAxiom, OntOPE> {
    @Override
    Class<OntOPE> getView() {
        return OntOPE.class;
    }

    @Override
    OWLObjectPropertyDomainAxiom create(OntStatement statement, Set<OWLAnnotation> annotations) {
        OWLObjectPropertyExpression p = ReadHelper.getObjectProperty(statement.getSubject().as(OntOPE.class));
        OWLClassExpression ce = ReadHelper.getClassExpression(statement.getObject().as(OntCE.class));
        return new OWLObjectPropertyDomainAxiomImpl(p, ce, annotations);
    }

    @Override
    Wrap<OWLObjectPropertyDomainAxiom> asAxiom(OntStatement statement) {
        Wrap<OWLObjectPropertyExpression> p = ReadHelper._getObjectProperty(statement.getSubject().as(getView()), getDataFactory());
        Wrap<? extends OWLClassExpression> ce = ReadHelper._getClassExpression(statement.getObject().as(OntCE.class), getDataFactory());
        Wrap.Collection<OWLAnnotation> annotations = annotations(statement);
        OWLObjectPropertyDomainAxiom res = getDataFactory().getOWLObjectPropertyDomainAxiom(p.getObject(), ce.getObject(), annotations.getObjects());
        return Wrap.create(res, statement).add(annotations.getTriples()).append(p).append(ce);
    }
}
