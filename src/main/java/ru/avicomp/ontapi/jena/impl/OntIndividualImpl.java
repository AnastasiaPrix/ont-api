package ru.avicomp.ontapi.jena.impl;

import java.util.Set;
import java.util.stream.Stream;

import org.apache.jena.enhanced.EnhGraph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;

import ru.avicomp.ontapi.OntApiException;
import ru.avicomp.ontapi.jena.JenaUtils;
import ru.avicomp.ontapi.jena.impl.configuration.*;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntIndividual;
import ru.avicomp.ontapi.jena.model.OntStatement;

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
        public boolean isLocal() {
            return getModel().isInBaseModel(this, RDF.type, OWL2.NamedIndividual);
        }

        @Override
        public boolean isBuiltIn() {
            return false;
        }
    }

    public static class AnonymousImpl extends OntIndividualImpl implements OntIndividual.Anonymous {
        public AnonymousImpl(Node n, EnhGraph m) {
            super(n, m);
        }

        @Override
        public void detachClass(OntCE clazz) {
            if (classes().filter(c -> !clazz.equals(c)).count() == 0) {
                // otherwise this would no longer be an individual
                throw new OntApiException("Can't detach last class " + clazz);
            }
            super.detachClass(clazz);
        }

        static class Finder implements OntFinder {
            @Override
            public Stream<Node> find(EnhGraph eg) {
                return JenaUtils.asStream(eg.asGraph().find(Node.ANY, RDF_TYPE, Node.ANY).
                        filterKeep(t -> t.getSubject().isBlank()).
                        filterKeep(t -> isOntClass(t.getObject(), eg)).mapWith(Triple::getSubject));
            }
        }

        static class Filter implements OntFilter {
            @Override
            public boolean test(Node node, EnhGraph graph) {
                if (!node.isBlank()) return false;
                Set<Node> nodes = graph.asGraph().find(node, RDF_TYPE, Node.ANY).mapWith(Triple::getObject).filterKeep(n -> isOntClass(n, graph)).toSet();
                return !nodes.isEmpty();
            }
        }

        private static boolean isOntClass(Node node, EnhGraph eg) {
            return OntCEImpl.abstractCEFactory.canWrap(node, eg);
        }
    }
}
