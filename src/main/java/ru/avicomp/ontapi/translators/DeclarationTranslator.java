package ru.avicomp.ontapi.translators;

import java.util.Set;
import java.util.stream.Stream;

import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLDeclarationAxiom;
import org.semanticweb.owlapi.model.OWLEntity;

import ru.avicomp.ontapi.jena.model.OntEntity;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntObject;
import ru.avicomp.ontapi.jena.model.OntStatement;
import ru.avicomp.ontapi.jena.vocabulary.RDF;
import uk.ac.manchester.cs.owl.owlapi.OWLDeclarationAxiomImpl;

/**
 * Declaration of OWLEntity.
 * Simple triplet with rdf:type predicate.
 * <p>
 * Created by @szuev on 28.09.2016.
 */
class DeclarationTranslator extends AxiomTranslator<OWLDeclarationAxiom> {
    @Override
    public void write(OWLDeclarationAxiom axiom, OntGraphModel model) {
        WriteHelper.writeDeclarationTriple(model, axiom.getEntity(), RDF.type, WriteHelper.getType(axiom.getEntity()), axiom.annotations());
    }

    @Override
    Stream<OntStatement> statements(OntGraphModel model) {
        return model.ontEntities().filter(OntObject::isLocal).map(OntObject::getRoot);
    }

    @Override
    OWLDeclarationAxiom create(OntStatement statement, Set<OWLAnnotation> annotations) {
        OWLEntity entity = ReadHelper.getEntity(statement.getSubject().as(OntEntity.class));
        return new OWLDeclarationAxiomImpl(entity, annotations);
    }

    @Override
    Wrap<OWLDeclarationAxiom> asAxiom(OntStatement statement) {
        Wrap<OWLEntity> entity = ReadHelper._getEntity(statement.getSubject().as(OntEntity.class), getDataFactory());
        Wrap.Collection<OWLAnnotation> annotations = annotations(statement);
        OWLDeclarationAxiom res = getDataFactory().getOWLDeclarationAxiom(entity.getObject(), annotations.getObjects());
        return Wrap.create(res, statement).add(annotations.getTriples());
    }
}
