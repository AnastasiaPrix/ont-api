package ru.avicomp.ontapi.jena.impl.configuration;

import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.jena.enhanced.EnhGraph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

import ru.avicomp.ontapi.jena.OntJenaException;
import ru.avicomp.ontapi.jena.utils.Streams;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

/**
 * To perform the preliminary search resources in model,
 * then the result stream will be filtered by {@link OntFilter}
 * Used in the factory {@link CommonOntObjectFactory}.
 * <p>
 * Created by szuev on 07.11.2016.
 */
@FunctionalInterface
public interface OntFinder {
    OntFinder ANY_SUBJECT = eg -> Streams.asStream(eg.asGraph().find(Node.ANY, Node.ANY, Node.ANY).mapWith(Triple::getSubject));
    OntFinder ANY_SUBJECT_AND_OBJECT = eg -> Streams.asStream(eg.asGraph().find(Node.ANY, Node.ANY, Node.ANY))
            .map(t -> Stream.of(t.getSubject(), t.getObject()))
            .flatMap(Function.identity()).distinct();
    OntFinder ANYTHING = eg -> Streams.asStream(eg.asGraph().find(Node.ANY, Node.ANY, Node.ANY))
            .map(t -> Stream.of(t.getSubject(), t.getPredicate(), t.getObject()))
            .flatMap(Function.identity()).distinct();
    OntFinder TYPED = new ByPredicate(RDF.type);

    Stream<Node> find(EnhGraph eg);

    default OntFinder restrict(OntFilter filter) {
        OntJenaException.notNull(filter, "Null restriction filter.");
        return eg -> find(eg).filter(n -> filter.test(n, eg));
    }

    class ByType implements OntFinder {
        protected final Node type;

        public ByType(Resource type) {
            this.type = OntJenaException.notNull(type, "Null type.").asNode();
        }

        @Override
        public Stream<Node> find(EnhGraph eg) {
            return Streams.asStream(eg.asGraph().find(Node.ANY, RDF.type.asNode(), type).mapWith(Triple::getSubject));
        }
    }

    class ByPredicate implements OntFinder {
        protected final Node predicate;

        public ByPredicate(Property predicate) {
            this.predicate = OntJenaException.notNull(predicate, "Null predicate.").asNode();
        }

        @Override
        public Stream<Node> find(EnhGraph eg) {
            return Streams.asStream(eg.asGraph().find(Node.ANY, predicate, Node.ANY).mapWith(Triple::getSubject));
        }
    }
}
