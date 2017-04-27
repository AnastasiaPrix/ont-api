package ru.avicomp.ontapi.internal;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDisjointClassesAxiom;

import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntDisjoint;
import ru.avicomp.ontapi.jena.model.OntStatement;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import uk.ac.manchester.cs.owl.owlapi.OWLDisjointClassesAxiomImpl;

/**
 * see {@link AbstractTwoWayNaryTranslator}
 * example:
 * :Complex2 owl:disjointWith  :Simple2 , :Simple1 .
 * OWL2 alternative way:
 * [ a owl:AllDisjointClasses ; owl:members ( :Complex2 :Simple1 :Simple2 ) ] .
 * <p>
 * Created by @szuev on 28.09.2016.
 */
public class DisjointClassesTranslator extends AbstractTwoWayNaryTranslator<OWLDisjointClassesAxiom, OWLClassExpression, OntCE> {
    @Override
    Property getPredicate() {
        return OWL.disjointWith;
    }

    @Override
    Class<OntCE> getView() {
        return OntCE.class;
    }

    @Override
    OWLDisjointClassesAxiom create(Stream<OWLClassExpression> components, Set<OWLAnnotation> annotations) {
        return new OWLDisjointClassesAxiomImpl(components.collect(Collectors.toSet()), annotations);
    }

    @Override
    Resource getMembersType() {
        return OWL.AllDisjointClasses;
    }

    @Override
    Property getMembersPredicate() {
        return OWL.members;
    }

    @Override
    Class<OntDisjoint.Classes> getDisjointView() {
        return OntDisjoint.Classes.class;
    }

    @Override
    public InternalObject<OWLDisjointClassesAxiom> asAxiom(OntStatement statement) {
        ConfigProvider.Config conf = getConfig(statement);
        InternalObject.Collection<? extends OWLClassExpression> members;
        Stream<OntStatement> content;
        if (statement.getSubject().canAs(getDisjointView())) {
            OntDisjoint.Classes disjoint = statement.getSubject().as(getDisjointView());
            content = disjoint.content();
            members = InternalObject.Collection.create(disjoint.members().map(m -> ReadHelper.fetchClassExpression(m, conf.dataFactory())));
        } else {
            content = Stream.of(statement);
            members = InternalObject.Collection.create(Stream.of(statement.getSubject(), statement.getObject())
                    .map(r -> r.as(getView())).map(m -> ReadHelper.fetchClassExpression(m, conf.dataFactory())));
        }
        InternalObject.Collection<OWLAnnotation> annotations = ReadHelper.getStatementAnnotations(statement, conf.dataFactory(), conf.loaderConfig());
        OWLDisjointClassesAxiom res = conf.dataFactory().getOWLDisjointClassesAxiom(members.getObjects(), annotations.getObjects());
        return InternalObject.create(res, content).add(annotations.getTriples()).add(members.getTriples());
    }
}
