package ru.avicomp.ontapi.jena.impl;

import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.jena.enhanced.EnhGraph;
import org.apache.jena.enhanced.EnhNode;
import org.apache.jena.graph.FrontsNode;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.impl.RDFListImpl;
import org.apache.jena.util.iterator.ExtendedIterator;

import ru.avicomp.ontapi.jena.OntJenaException;
import ru.avicomp.ontapi.jena.impl.configuration.*;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntIndividual;
import ru.avicomp.ontapi.jena.model.OntStatement;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

/**
 * both for anon and named individuals.
 * <p>
 * Created by szuev on 09.11.2016.
 */
public class OntIndividualImpl extends OntObjectImpl implements OntIndividual {

    public static OntObjectFactory anonymousIndividualFactory = new CommonOntObjectFactory(
            new OntMaker.Default(AnonymousImpl.class), new AnonymousImpl.Finder(), new AnonymousImpl.Filter());
    public static OntObjectFactory abstractIndividualFactory = new MultiOntObjectFactory(OntFinder.TYPED,
            OntEntityImpl.individualFactory, anonymousIndividualFactory);

    public OntIndividualImpl(Node n, EnhGraph m) {
        super(n, m);
    }

    @Override
    public OntStatement attachClass(OntCE clazz) {
        return addType(clazz);
    }

    @Override
    public void detachClass(OntCE clazz) {
        removeType(clazz);
    }

    public static class NamedImpl extends OntIndividualImpl implements OntIndividual.Named {
        public NamedImpl(Node n, EnhGraph m) {
            super(OntObjectImpl.checkNamed(n), m);
        }

        @Override
        public boolean isBuiltIn() {
            return false;
        }

        @Override
        public OntStatement getRoot() {
            return getRoot(RDF.type, OWL.NamedIndividual);
        }
    }

    /**
     * see description to the interface {@link OntIndividual.Anonymous}
     * It seems that checking for conditions 6, 7, 8, 9 could be displaced by checking that tested b-node is
     * a standalone object in a triple from annotation and object property assertion.
     * About this there are following reflections:
     * - in the well-formed ontology anonymous subject should be declared as individual (condition 1),
     *      otherwise it is just any other b-node (e.g. root for owl:Axiom)
     * - the bulk annotations consist of annotation assertions.
     */
    public static class AnonymousImpl extends OntIndividualImpl implements OntIndividual.Anonymous {
        public AnonymousImpl(Node n, EnhGraph m) {
            super(n, m);
        }

        @Override
        public void detachClass(OntCE clazz) {
            if (classes().filter(c -> !clazz.equals(c)).count() == 0) {
                // otherwise this the anonymous individual could be lost. use another way to last remove class-assertion.
                throw new OntJenaException("Can't detach class " + clazz + ": it is a single for individual " + this);
            }
            super.detachClass(clazz);
        }

        static class Finder implements OntFinder {
            @Override
            public Stream<Node> find(EnhGraph eg) {
                Stream<Node> declarations = Models.asStream(getDeclarations(Node.ANY, eg).mapWith(Triple::getSubject).filterKeep(Node::isBlank));
                Stream<Node> disjoint = disjointAnonIndividuals(eg);
                Stream<Node> oneOf = oneOfAnonIndividuals(eg);
                Stream<Node> assertions = positiveAssertionAnonIndividuals(eg);
                Stream<Node> negative = negativeAssertionAnonIndividuals(eg);
                Stream<Node> same = sameAnonIndividuals(eg);
                Stream<Node> different = differentAnonIndividuals(eg);
                return Stream.of(
                        declarations
                        , disjoint
                        , oneOf
                        , assertions
                        , negative
                        , same
                        , different
                ).flatMap(Function.identity()).distinct();
            }
        }

        static class Filter implements OntFilter {
            @Override
            public boolean test(Node node, EnhGraph graph) {
                return node.isBlank() &&
                        (!getDeclarations(node, graph).mapWith(Triple::getObject).toSet().isEmpty() ||
                                positiveAssertionAnonIndividuals(graph).anyMatch(node::equals) ||
                                negativeAssertionAnonIndividuals(graph).anyMatch(node::equals) ||
                                sameAnonIndividuals(graph).anyMatch(node::equals) ||
                                differentAnonIndividuals(graph).anyMatch(node::equals) ||
                                oneOfAnonIndividuals(graph).anyMatch(node::equals) ||
                                disjointAnonIndividuals(graph).anyMatch(node::equals));
            }
        }

        private static ExtendedIterator<Triple> getDeclarations(Node node, EnhGraph eg) {
            return eg.asGraph().find(node, RDF_TYPE, Node.ANY).
                    filterKeep(t -> OntCEImpl.abstractCEFactory.canWrap(t.getObject(), eg));
        }

        private static Stream<Node> negativeAssertionAnonIndividuals(EnhGraph eg) {
            return Models.asStream(eg.asGraph().find(Node.ANY, RDF.type.asNode(), OWL.NegativePropertyAssertion.asNode()))
                    .map(Triple::getSubject).map(subject ->
                            Stream.of(OWL.sourceIndividual, OWL.targetIndividual)
                                    .map(FrontsNode::asNode)
                                    .map(predicate -> Models.asStream(eg.asGraph().find(subject, predicate, Node.ANY)).map(Triple::getObject))
                                    .flatMap(Function.identity()))
                    .flatMap(Function.identity())
                    .filter(Node::isBlank);
        }

        private static Stream<Node> sameAnonIndividuals(EnhGraph eg) {
            return anonsForPredicate(eg.asGraph(), OWL.sameAs.asNode());
        }

        private static Stream<Node> differentAnonIndividuals(EnhGraph eg) {
            return anonsForPredicate(eg.asGraph(), OWL.differentFrom.asNode());
        }

        private static Stream<Node> anonsForPredicate(Graph graph, Node predicate) {
            return Models.asStream(graph.find(Node.ANY, predicate, Node.ANY))
                    .map(triple -> Stream.of(triple.getSubject(), triple.getObject()))
                    .flatMap(Function.identity()).filter(Node::isBlank);
        }

        /**
         * returns stream of blank nodes ("_:a"), where blank node is an standalone object in a triple
         * which corresponds object property assertion "_:a1 PN _:a2" or annotation property assertion "s A t"
         *
         * @param eg {@link OntGraphModelImpl}
         * @return Stream of {@link Node}
         */
        private static Stream<Node> positiveAssertionAnonIndividuals(EnhGraph eg) {
            return Stream.of(OntEntityImpl.annotationPropertyFactory.find(eg), OntEntityImpl.objectPropertyFactory.find(eg))
                    .flatMap(Function.identity())
                    .map(EnhNode::asNode)
                    .map(node -> standaloneAnonAssertionObjects(eg.asGraph(), node))
                    .flatMap(Function.identity());
        }

        private static Stream<Node> standaloneAnonAssertionObjects(Graph graph, Node predicate) {
            return Models.asStream(graph.find(Node.ANY, predicate, Node.ANY))
                    .map(Triple::getObject)
                    .filter(Node::isBlank)
                    .filter(node -> !graph.contains(node, Node.ANY, Node.ANY));
        }

        private static Stream<Node> disjointAnonIndividuals(EnhGraph eg) {
            return blankNodesFromList(eg, OWL.AllDifferent.asNode(), OWL.distinctMembers.asNode(), OWL.members.asNode());
        }

        private static Stream<Node> oneOfAnonIndividuals(EnhGraph eg) {
            return blankNodesFromList(eg, OWL.Class.asNode(), OWL.oneOf.asNode());
        }

        private static Stream<Node> blankNodesFromList(EnhGraph eg, Node type, Node... predicates) {
            Stream<Node> roots = Models.asStream(eg.asGraph().find(Node.ANY, RDF.type.asNode(), type))
                    .map(Triple::getSubject)
                    .filter(Node::isBlank);
            return objects(eg.asGraph(), roots, predicates)
                    .filter(node -> RDFListImpl.factory.canWrap(node, eg))
                    .map(node -> RDFListImpl.factory.wrap(node, eg))
                    .map(enhNode -> enhNode.as(RDFList.class))
                    .map(RDFList::asJavaList)
                    .map(Collection::stream)
                    .flatMap(Function.identity())
                    .map(FrontsNode::asNode)
                    .filter(Node::isBlank);

        }

        private static Stream<Node> objects(Graph graph, Node subject, Node predicate) {
            return Models.asStream(graph.find(subject, predicate, Node.ANY).mapWith(Triple::getObject));
        }

        private static Stream<Node> objects(Graph graph, Node subject, Node... predicates) {
            return Stream.of(predicates).map(p -> objects(graph, subject, p)).flatMap(Function.identity());
        }

        private static Stream<Node> objects(Graph graph, Stream<Node> subjects, Node... predicates) {
            return subjects.map(r -> objects(graph, r, predicates)).flatMap(Function.identity());
        }
    }
}
