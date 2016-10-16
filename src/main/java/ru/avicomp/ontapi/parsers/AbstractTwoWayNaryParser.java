package ru.avicomp.ontapi.parsers;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.semanticweb.owlapi.model.IsAnonymous;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLNaryAxiom;
import org.semanticweb.owlapi.model.OWLObject;

import ru.avicomp.ontapi.OntException;

/**
 * This is for following axioms with two or more than two entities:
 * DisjointClasses ({@link DisjointClassesParser}),
 * DisjointObjectProperties ({@link DisjointObjectPropertiesParser}),
 * DisjointDataProperties ({@link DisjointDataPropertiesParser}),
 * DifferentIndividuals ({@link DifferentIndividualsParser}).
 * Each of these axioms could be written in two ways: as single triple (or sequence of single triples) or as special anonymous node with rdf:List inside.
 * <p>
 * Created by szuev on 12.10.2016.
 */
abstract class AbstractTwoWayNaryParser<Axiom extends OWLAxiom & OWLNaryAxiom<? extends IsAnonymous>> extends AxiomParser<Axiom> {
    @Override
    public void process(Graph graph) {
        OWLNaryAxiom<? extends IsAnonymous> axiom = getAxiom();
        long count = axiom.operands().count();
        if (count < 2) throw new OntException("Should be at least two entities " + axiom);
        if (count == 2) { // single triple classic way
            OWLObject entity = axiom.operands().filter(e -> !e.isAnonymous()).findFirst().orElse(null);
            if (entity == null)
                throw new OntException("Can't find a single non-anonymous expression inside " + axiom);
            OWLObject rest = axiom.operands().filter((obj) -> !entity.equals(obj)).findFirst().orElse(null);
            AxiomParseUtils.processAnnotatedTriple(graph, entity, getPredicate(), rest, axiom, true);
        } else { // OWL2 anonymous node
            Model model = AxiomParseUtils.createModel(graph);
            Resource root = model.createResource();
            model.add(root, RDF.type, getMembersType());
            model.add(root, getMembersPredicate(), AxiomParseUtils.addRDFList(model, axiom.operands()));
            AnnotationsParseUtils.addAnnotations(graph, root.asNode(), axiom);
        }
    }

    public abstract Property getPredicate();

    public abstract Resource getMembersType();

    public abstract Property getMembersPredicate();
}
