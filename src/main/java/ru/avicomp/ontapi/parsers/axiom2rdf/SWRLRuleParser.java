package ru.avicomp.ontapi.parsers.axiom2rdf;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.semanticweb.owlapi.model.SWRLRule;

import ru.avicomp.ontapi.JenaUtils;
import ru.avicomp.ontapi.vocabulary.SWRL;

/**
 * for "Rule" Axiom {@link org.semanticweb.owlapi.model.AxiomType#SWRL_RULE}
 * Specification: <a href='https://www.w3.org/Submission/SWRL/'>SWRL: A Semantic Web Rule Language Combining OWL and RuleML</a>.
 * <p>
 * Created by szuev on 20.10.2016.
 */
class SWRLRuleParser extends AxiomParser<SWRLRule> {
    @Override
    public void process(Graph graph) {
        Model model = TranslationHelper.createModel(graph);
        Resource root = model.createResource();
        root.addProperty(RDF.type, SWRL.Imp);
        root.addProperty(SWRL.head, JenaUtils.createTypedList(model, SWRL.AtomList, getAxiom().head().map(a -> TranslationHelper.addRDFNode(model, a))));
        root.addProperty(SWRL.body, JenaUtils.createTypedList(model, SWRL.AtomList, getAxiom().body().map(a -> TranslationHelper.addRDFNode(model, a))));
        // annotation as for anonymous node.
        // WARNING: this way is correct, but OWL-API can't handle correctly complex annotations.
        // TODO: need to change OWL-loader
        TranslationHelper.addAnnotations(graph, root.asNode(), getAxiom());
    }
}
