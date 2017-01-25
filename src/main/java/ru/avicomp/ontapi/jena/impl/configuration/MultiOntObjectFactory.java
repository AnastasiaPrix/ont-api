package ru.avicomp.ontapi.jena.impl.configuration;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.jena.enhanced.EnhGraph;
import org.apache.jena.enhanced.EnhNode;
import org.apache.jena.graph.Node;
import org.apache.jena.ontology.ConversionException;

/**
 * Factory to combine several factories.
 * <p>
 * Created by szuev on 07.11.2016.
 */
public class MultiOntObjectFactory extends OntObjectFactory {
    private final OntObjectFactory[] factories;
    private OntFinder finder;

    public MultiOntObjectFactory(OntObjectFactory... factories) {
        this(null, factories);
    }

    public MultiOntObjectFactory(OntFinder finder, OntObjectFactory... factories) {
        this.finder = finder;
        this.factories = factories;
    }

    private MultiOntObjectFactory(OntFinder finder, Stream<OntObjectFactory> factories) {
        this(finder, factories.toArray(OntObjectFactory[]::new));
    }

    @Override
    public EnhNode wrap(Node node, EnhGraph eg) {
        EnhNode res = doWrap(node, eg);
        if (res != null) return res;
        throw new ConversionException("Can't wrap node " + node + ". Use direct factory.");
    }

    @Override
    public boolean canWrap(Node node, EnhGraph eg) {
        for (OntObjectFactory f : factories) {
            if (f.canWrap(node, eg)) return true;
        }
        return false;
    }

    private EnhNode doWrap(Node node, EnhGraph eg) {
        for (OntObjectFactory f : factories) {
            if (f.canWrap(node, eg)) return f.wrap(node, eg);
        }
        return null;
    }

    @Override
    public Stream<EnhNode> find(EnhGraph eg) {
        if (finder != null) {
            return finder.find(eg).map(n -> doWrap(n, eg)).filter(Objects::nonNull);
        }
        return Arrays.stream(factories).map(f -> f.find(eg)).flatMap(Function.identity()).distinct();
    }

    public OntFinder getFinder() {
        return finder;
    }

    public Stream<? extends OntObjectFactory> factories() {
        return Arrays.stream(factories);
    }

    public MultiOntObjectFactory concat(OntObjectFactory... factories) {
        Stream<OntObjectFactory> toAdd = Stream.of(factories).map(f ->
                f instanceof MultiOntObjectFactory ?
                        ((MultiOntObjectFactory) f).factories() :
                        Stream.of(f)).flatMap(Function.identity());
        return new MultiOntObjectFactory(getFinder(), Stream.concat(toAdd, factories()));
    }
}
