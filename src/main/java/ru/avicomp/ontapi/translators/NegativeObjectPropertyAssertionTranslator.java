package ru.avicomp.ontapi.translators;

import java.util.Set;

import org.semanticweb.owlapi.model.*;

import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntNPA;
import ru.avicomp.ontapi.jena.model.OntStatement;
import uk.ac.manchester.cs.owl.owlapi.OWLNegativeObjectPropertyAssertionAxiomImpl;

/**
 * example:
 * [ a owl:NegativePropertyAssertion; owl:sourceIndividual :ind1; owl:assertionProperty :objProp; owl:targetIndividual :ind2 ] .
 * Created by szuev on 12.10.2016.
 */
class NegativeObjectPropertyAssertionTranslator extends AbstractNegativePropertyAssertionTranslator<OWLNegativeObjectPropertyAssertionAxiom, OntNPA.ObjectAssertion> {
    @Override
    OntNPA.ObjectAssertion createNPA(OWLNegativeObjectPropertyAssertionAxiom axiom, OntGraphModel model) {
        return WriteHelper.addObjectProperty(model, axiom.getProperty())
                .addNegativeAssertion(WriteHelper.addIndividual(model, axiom.getSubject()), WriteHelper.addIndividual(model, axiom.getObject()));
    }

    @Override
    Class<OntNPA.ObjectAssertion> getView() {
        return OntNPA.ObjectAssertion.class;
    }

    @Override
    OWLNegativeObjectPropertyAssertionAxiom create(OntStatement statement, Set<OWLAnnotation> annotations) {
        OntNPA.ObjectAssertion npa = statement.getSubject().as(OntNPA.ObjectAssertion.class);
        OWLIndividual subject = ReadHelper.getIndividual(npa.getSource());
        OWLObjectPropertyExpression property = ReadHelper.getObjectProperty(npa.getProperty());
        OWLIndividual object = ReadHelper.getIndividual(npa.getTarget());
        return new OWLNegativeObjectPropertyAssertionAxiomImpl(subject, property, object, annotations);
    }

    @Override
    Wrap<OWLNegativeObjectPropertyAssertionAxiom> asAxiom(OntStatement statement) {
        OWLDataFactory df = getDataFactory();
        OntNPA.ObjectAssertion npa = statement.getSubject().as(getView());
        Wrap<? extends OWLIndividual> s = ReadHelper._getIndividual(npa.getSource(), df);
        Wrap<OWLObjectPropertyExpression> p = ReadHelper._getObjectProperty(npa.getProperty(), df);
        Wrap<? extends OWLIndividual> o = ReadHelper._getIndividual(npa.getTarget(), df);
        Wrap.Collection<OWLAnnotation> annotations = annotations(statement);
        OWLNegativeObjectPropertyAssertionAxiom res = df.getOWLNegativeObjectPropertyAssertionAxiom(p.getObject(),
                s.getObject(), o.getObject(), annotations.getObjects());
        return Wrap.create(res, npa.content()).add(annotations.getTriples()).append(s).append(p).append(o);
    }
}
