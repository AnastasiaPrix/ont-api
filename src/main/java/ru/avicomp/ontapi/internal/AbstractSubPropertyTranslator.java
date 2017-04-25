package ru.avicomp.ontapi.internal;

import java.util.stream.Stream;

import org.apache.jena.vocabulary.RDFS;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLPropertyExpression;

import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntPE;
import ru.avicomp.ontapi.jena.model.OntStatement;

/**
 * base class for {@link SubObjectPropertyOfTranslator}, {@link SubDataPropertyOfTranslator} and {@link SubAnnotationPropertyOfTranslator}
 * Example:
 * foaf:msnChatID  rdfs:subPropertyOf foaf:nick .
 * <p>
 * Created by @szuev on 30.09.2016.
 */
public abstract class AbstractSubPropertyTranslator<Axiom extends OWLAxiom, P extends OntPE> extends AxiomTranslator<Axiom> {

    abstract OWLPropertyExpression getSubProperty(Axiom axiom);

    abstract OWLPropertyExpression getSuperProperty(Axiom axiom);

    abstract Class<P> getView();

    public Stream<OntStatement> statements(OntGraphModel model) {
        return model.statements(null, RDFS.subPropertyOf, null)
                .filter(OntStatement::isLocal)
                .filter(s -> s.getSubject().canAs(getView()))
                .filter(s -> s.getObject().canAs(getView()));
    }

    @Override
    public boolean testStatement(OntStatement statement) {
        return statement.getPredicate().equals(RDFS.subPropertyOf)
                && statement.getSubject().canAs(getView())
                && statement.getObject().canAs(getView());
    }

    @Override
    public void write(Axiom axiom, OntGraphModel model) {
        WriteHelper.writeTriple(model, getSubProperty(axiom), RDFS.subPropertyOf, getSuperProperty(axiom), axiom.annotations());
    }
}
