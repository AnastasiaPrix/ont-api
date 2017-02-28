package ru.avicomp.ontapi.jena.converters;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.jena.graph.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.avicomp.ontapi.jena.OntJenaException;

/**
 * Class to perform some transformation action on the specified graph.
 * Currently it is to convert the OWL1/RDFS ontological graph to the OWL2DL graph and to fix missed declarations.
 * Use it to fix "mistakes" in graph after loading from io-stream according OWL2 specification and before using common API.
 * <p>
 * Created by szuev on 28.10.2016.
 */
public abstract class GraphTransformConfig {
    protected static final Logger LOGGER = LoggerFactory.getLogger(GraphTransformConfig.class);
    private static Store converters = new Store()
            .add(RDFStoOWLFixer::new)
            .add(OWLtoOWL2DLFixer::new)
            .add(DeclarationFixer::new);

    public static Store setTransformers(Store store) {
        OntJenaException.notNull(store, "Null converter store specified.");
        Store res = converters;
        converters = store;
        return res;
    }

    public static Store getTransformers() {
        return converters;
    }

    /**
     * helper method to perform conversion one {@link Graph} to another.
     * Note: currently it returns the same graph, not a fixed copy.
     *
     * @param graph input graph
     * @return output graph
     */
    public static Graph convert(Graph graph) {
        getTransformers().actions(graph).forEach(TransformAction::process);
        return graph;
    }

    @FunctionalInterface
    public interface Maker<GC extends TransformAction> extends Serializable {
        GC create(Graph graph);
    }

    public static class Store implements Serializable {
        private Set<Maker> set = new LinkedHashSet<>();

        public Store add(Maker f) {
            set.add(f);
            return this;
        }

        public Store remove(Maker f) {
            set.remove(f);
            return this;
        }

        public Stream<TransformAction> actions(Graph graph) {
            return set.stream().map(factory -> factory.create(graph));
        }
    }

    public static <GC extends TransformAction> GC createTransformAction(Class<GC> impl, Graph graph) {
        try {
            return impl.getDeclaredConstructor(Graph.class).newInstance(graph);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new OntJenaException("Must have public constructor with " + Graph.class.getName() + " as parameter.", e);
        }
    }

}
