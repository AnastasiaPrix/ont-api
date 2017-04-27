package ru.avicomp.ontapi.internal;

import java.util.Objects;
import java.util.stream.Stream;

import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLDeclarationAxiom;
import org.semanticweb.owlapi.model.OWLEntity;

import ru.avicomp.ontapi.jena.impl.Entities;
import ru.avicomp.ontapi.jena.model.OntEntity;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntObject;
import ru.avicomp.ontapi.jena.model.OntStatement;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

/**
 * Declaration of OWLEntity.
 * Simple triplet with rdf:type predicate.
 * <p>
 * Created by @szuev on 28.09.2016.
 */
public class DeclarationTranslator extends AxiomTranslator<OWLDeclarationAxiom> {
    @Override
    public void write(OWLDeclarationAxiom axiom, OntGraphModel model) {
        WriteHelper.writeDeclarationTriple(model, axiom.getEntity(), RDF.type, WriteHelper.getType(axiom.getEntity()), axiom.annotations());
    }

    @Override
    public Stream<OntStatement> statements(OntGraphModel model) {
        if (!getConfig(model).loaderConfig().isAllowReadDeclarations()) return Stream.empty();
        return model.ontEntities().map(OntObject::getRoot).filter(Objects::nonNull).filter(OntStatement::isLocal);
    }

    @Override
    public boolean testStatement(OntStatement statement) {
        return statement.getPredicate().equals(RDF.type)
                && statement.getSubject().isURIResource()
                && Stream.of(Entities.values()).map(Entities::type).anyMatch(t -> statement.getObject().equals(t));
    }

    @Override
    public InternalObject<OWLDeclarationAxiom> asAxiom(OntStatement statement) {
        ConfigProvider.Config conf = getConfig(statement);
        InternalObject<? extends OWLEntity> entity = ReadHelper.wrapEntity(statement.getSubject().as(OntEntity.class), conf.dataFactory());
        InternalObject.Collection<OWLAnnotation> annotations = ReadHelper.getStatementAnnotations(statement, conf.dataFactory(), conf.loaderConfig());
        OWLDeclarationAxiom res = conf.dataFactory().getOWLDeclarationAxiom(entity.getObject(), annotations.getObjects());
        return InternalObject.create(res, statement).add(annotations.getTriples());
    }
}
