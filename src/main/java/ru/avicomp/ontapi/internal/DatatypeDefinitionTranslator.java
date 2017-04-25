package ru.avicomp.ontapi.internal;

import java.util.stream.Stream;

import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLDataRange;
import org.semanticweb.owlapi.model.OWLDatatype;
import org.semanticweb.owlapi.model.OWLDatatypeDefinitionAxiom;

import ru.avicomp.ontapi.jena.model.OntDR;
import ru.avicomp.ontapi.jena.model.OntDT;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntStatement;
import ru.avicomp.ontapi.jena.vocabulary.OWL;

/**
 * example:
 * :data-type-3 rdf:type rdfs:Datatype ; owl:equivalentClass [ rdf:type rdfs:Datatype ; owl:unionOf ( :data-type-1  :data-type-2 ) ] .
 * <p>
 * Created by @szuev on 18.10.2016.
 */
public class DatatypeDefinitionTranslator extends AxiomTranslator<OWLDatatypeDefinitionAxiom> {
    @Override
    public void write(OWLDatatypeDefinitionAxiom axiom, OntGraphModel model) {
        WriteHelper.writeTriple(model, axiom.getDatatype(), OWL.equivalentClass, axiom.getDataRange(), axiom.annotations());
    }

    @Override
    public Stream<OntStatement> statements(OntGraphModel model) {
        return model.statements(null, OWL.equivalentClass, null)
                .filter(OntStatement::isLocal)
                .filter(s -> s.getSubject().canAs(OntDT.class))
                .filter(s -> s.getObject().canAs(OntDR.class));
    }

    @Override
    public boolean testStatement(OntStatement statement) {
        return statement.getPredicate().equals(OWL.equivalentClass)
                && statement.getSubject().canAs(OntDT.class)
                && statement.getObject().canAs(OntDR.class);
    }

    @Override
    public Wrap<OWLDatatypeDefinitionAxiom> asAxiom(OntStatement statement) {
        ConfigProvider.Config conf = getConfig(statement);
        Wrap<OWLDatatype> dt = ReadHelper.fetchDatatype(statement.getSubject().as(OntDT.class), conf.dataFactory());
        Wrap<? extends OWLDataRange> dr = ReadHelper.fetchDataRange(statement.getObject().as(OntDR.class), conf.dataFactory());
        Wrap.Collection<OWLAnnotation> annotations = ReadHelper.getStatementAnnotations(statement, conf.dataFactory(), conf.loaderConfig());
        OWLDatatypeDefinitionAxiom res = conf.dataFactory().getOWLDatatypeDefinitionAxiom(dt.getObject(), dr.getObject(), annotations.getObjects());
        return Wrap.create(res, statement).add(annotations.getTriples()).append(dt).append(dr);
    }
}
