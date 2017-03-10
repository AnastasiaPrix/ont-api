package ru.avicomp.ontapi.internal;

import org.semanticweb.owlapi.model.*;

import ru.avicomp.ontapi.jena.model.OntOPE;
import ru.avicomp.ontapi.jena.model.OntStatement;

/**
 * see {@link AbstractSubPropertyTranslator}
 * <p>
 * Created by @szuev on 29.09.2016.
 */
class SubObjectPropertyOfTranslator extends AbstractSubPropertyTranslator<OWLSubObjectPropertyOfAxiom, OntOPE> {
    @Override
    OWLPropertyExpression getSubProperty(OWLSubObjectPropertyOfAxiom axiom) {
        return axiom.getSubProperty();
    }

    @Override
    OWLPropertyExpression getSuperProperty(OWLSubObjectPropertyOfAxiom axiom) {
        return axiom.getSuperProperty();
    }

    @Override
    Class<OntOPE> getView() {
        return OntOPE.class;
    }

    @Override
    public Wrap<OWLSubObjectPropertyOfAxiom> asAxiom(OntStatement statement) {
        OWLDataFactory df = getDataFactory(statement.getModel());
        Wrap<? extends OWLObjectPropertyExpression> sub = ReadHelper.fetchObjectPropertyExpression(statement.getSubject().as(OntOPE.class), df);
        Wrap<? extends OWLObjectPropertyExpression> sup = ReadHelper.fetchObjectPropertyExpression(statement.getObject().as(OntOPE.class), df);
        Wrap.Collection<OWLAnnotation> annotations = ReadHelper.getStatementAnnotations(statement, df);
        OWLSubObjectPropertyOfAxiom res = df.getOWLSubObjectPropertyOfAxiom(sub.getObject(), sup.getObject(), annotations.getObjects());
        return Wrap.create(res, statement).add(annotations.getTriples()).append(sub).append(sup);
    }
}
