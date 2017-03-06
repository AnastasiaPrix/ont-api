package ru.avicomp.ontapi.translators;

import java.util.Set;

import org.semanticweb.owlapi.model.*;

import ru.avicomp.ontapi.jena.model.OntOPE;
import ru.avicomp.ontapi.jena.model.OntStatement;
import uk.ac.manchester.cs.owl.owlapi.OWLSubObjectPropertyOfAxiomImpl;

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
    OWLSubObjectPropertyOfAxiom create(OntStatement statement, Set<OWLAnnotation> annotations) {
        OWLObjectPropertyExpression sub = ReadHelper.getObjectProperty(statement.getSubject().as(OntOPE.class));
        OWLObjectPropertyExpression sup = ReadHelper.getObjectProperty(statement.getObject().as(OntOPE.class));
        return new OWLSubObjectPropertyOfAxiomImpl(sub, sup, annotations);
    }

    @Override
    Wrap<OWLSubObjectPropertyOfAxiom> asAxiom(OntStatement statement) {
        OWLDataFactory df = getDataFactory();
        Wrap<OWLObjectPropertyExpression> sub = ReadHelper._getObjectProperty(statement.getSubject().as(OntOPE.class), df);
        Wrap<OWLObjectPropertyExpression> sup = ReadHelper._getObjectProperty(statement.getObject().as(OntOPE.class), df);
        Wrap.Collection<OWLAnnotation> annotations = annotations(statement);
        OWLSubObjectPropertyOfAxiom res = df.getOWLSubObjectPropertyOfAxiom(sub.getObject(), sup.getObject(), annotations.getObjects());
        return Wrap.create(res, statement).add(annotations.getTriples()).append(sub).append(sup);
    }
}
