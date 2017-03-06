package ru.avicomp.ontapi.translators;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.jena.rdf.model.Property;
import org.semanticweb.owlapi.model.*;

import ru.avicomp.ontapi.jena.impl.OntObjectImpl;
import ru.avicomp.ontapi.jena.model.OntOPE;
import ru.avicomp.ontapi.jena.model.OntStatement;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import uk.ac.manchester.cs.owl.owlapi.OWLSubPropertyChainAxiomImpl;

/**
 * base class : {@link AbstractSubChainedTranslator}
 * for SubPropertyChainOf axiom
 * example: owl:topObjectProperty owl:propertyChainAxiom ( :ob-prop-1 :ob-prop-2 ) .
 * <p>
 * Created by @szuev on 18.10.2016.
 */
class SubPropertyChainOfTranslator extends AbstractSubChainedTranslator<OWLSubPropertyChainOfAxiom, OntOPE> {
    @Override
    OWLObject getSubject(OWLSubPropertyChainOfAxiom axiom) {
        return axiom.getSuperProperty();
    }

    @Override
    Property getPredicate() {
        return OWL.propertyChainAxiom;
    }

    @Override
    Stream<? extends OWLObject> getObjects(OWLSubPropertyChainOfAxiom axiom) {
        return axiom.getPropertyChain().stream();
    }

    @Override
    Class<OntOPE> getView() {
        return OntOPE.class;
    }

    @Override
    OWLSubPropertyChainOfAxiom create(OntStatement statement, Set<OWLAnnotation> annotations) {
        OntOPE subject = statement.getSubject().as(OntOPE.class);
        List<OWLObjectPropertyExpression> children = subject.superPropertyOf().map(ReadHelper::getObjectProperty).collect(Collectors.toList());
        return new OWLSubPropertyChainAxiomImpl(children, ReadHelper.getObjectProperty(subject), annotations);
    }

    @Override
    Wrap<OWLSubPropertyChainOfAxiom> asAxiom(OntStatement statement) {
        OWLDataFactory df = getDataFactory();
        OntOPE ope = statement.getSubject().as(OntOPE.class);
        Wrap<OWLObjectPropertyExpression> subject = ReadHelper._getObjectProperty(ope, df);
        Wrap.Collection<OWLObjectPropertyExpression> members = Wrap.Collection.create(ope.superPropertyOf().map(s -> ReadHelper._getObjectProperty(s, df)));
        Wrap.Collection<OWLAnnotation> annotations = annotations(statement);
        // note: the input is a list. does it mean that the order is important?
        OWLSubPropertyChainOfAxiom res = df.getOWLSubPropertyChainOfAxiom(members.objects().collect(Collectors.toList()), subject.getObject(), annotations.getObjects());
        Stream<OntStatement> content = ((OntObjectImpl) ope).rdfListContent(getPredicate());
        return Wrap.create(res, content).add(annotations.getTriples()).add(members.getTriples());
    }
}
