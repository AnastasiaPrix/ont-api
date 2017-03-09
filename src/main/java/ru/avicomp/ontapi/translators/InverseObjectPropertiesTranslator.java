package ru.avicomp.ontapi.translators;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLInverseObjectPropertiesAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;

import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntOPE;
import ru.avicomp.ontapi.jena.model.OntStatement;
import ru.avicomp.ontapi.jena.vocabulary.OWL;

/**
 * example:
 * pizza:hasBase owl:inverseOf pizza:isBaseOf ;
 * <p>
 * Created by @szuev on 30.09.2016.
 */
class InverseObjectPropertiesTranslator extends AxiomTranslator<OWLInverseObjectPropertiesAxiom> {
    @Override
    public void write(OWLInverseObjectPropertiesAxiom axiom, OntGraphModel model) {
        WriteHelper.writeTriple(model, axiom.getFirstProperty(), OWL.inverseOf, axiom.getSecondProperty(), axiom.annotations());
    }

    @Override
    Stream<OntStatement> statements(OntGraphModel model) {
        // NOTE as a precaution: the first (commented) way is not correct
        // since it includes anonymous object property expressions (based on owl:inverseOf),
        // which could be treat as separated axioms, but OWL-API doesn't think so.
        /*return model.statements(null, OWL.inverseOf, null)
                .filter(OntStatement::isLocal)
                .filter(s -> s.getSubject().canAs(OntOPE.class))
                .filter(s -> s.getObject().canAs(OntOPE.class));*/
        return model.ontObjects(OntOPE.class)
                .map(subj -> subj.inverseOf().map(obj -> subj.statement(OWL.inverseOf, obj)))
                .flatMap(Function.identity())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(OntStatement::isLocal);
    }

    @Override
    Wrap<OWLInverseObjectPropertiesAxiom> asAxiom(OntStatement statement) {
        OWLDataFactory df = getDataFactory(statement.getModel());
        Wrap<? extends OWLObjectPropertyExpression> f = ReadHelper.getObjectProperty(statement.getSubject().as(OntOPE.class), df);
        Wrap<? extends OWLObjectPropertyExpression> s = ReadHelper.getObjectProperty(statement.getObject().as(OntOPE.class), df);
        Wrap.Collection<OWLAnnotation> annotations = ReadHelper.getStatementAnnotations(statement, df);
        OWLInverseObjectPropertiesAxiom res = df.getOWLInverseObjectPropertiesAxiom(f.getObject(), s.getObject(), annotations.getObjects());
        return Wrap.create(res, statement).add(annotations.getTriples()).append(f).append(s);
    }
}
