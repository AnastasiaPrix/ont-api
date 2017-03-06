package ru.avicomp.ontapi.translators;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.jena.graph.Triple;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;

import ru.avicomp.ontapi.OntApiException;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntStatement;
import uk.ac.manchester.cs.owl.owlapi.OWLDataFactoryImpl;

/**
 * The base class to perform Axiom Graph Translator (operator 'T'), both for reading and writing.
 * Specification: <a href='https://www.w3.org/TR/owl2-mapping-to-rdf/#Mapping_from_the_Structural_Specification_to_RDF_Graphs'>2.1 Translation of Axioms without Annotations</a>
 * <p>
 * Created by @szuev on 28.09.2016.
 */
public abstract class AxiomTranslator<Axiom extends OWLAxiom> {

    private AxiomParserProvider.Config config = AxiomParserProvider.DEFAULT_CONFIG;

    public static final OWLDataFactory OWL_DATA_FACTORY = new OWLDataFactoryImpl();

    /**
     * writes axiom to model.
     *
     * @param axiom {@link OWLAxiom}
     * @param model {@link OntGraphModel}
     */
    public abstract void write(Axiom axiom, OntGraphModel model);

    /**
     * reads axioms and triples from model.
     *
     * @param model {@link OntGraphModel}
     * @return Set of {@link Wrap} with {@link OWLAxiom} as key and Set of {@link Triple} as value
     */
    public Set<Wrap<Axiom>> read(OntGraphModel model) {
        try {
            Map<Axiom, Wrap<Axiom>> res = new HashMap<>();
            statements(model).map(ReadHelper.AxiomStatement::new).forEach(c -> {
                Axiom axiom = create(c.getStatement(), c.getAnnotations());
                Set<Triple> triples = c.getTriples();
                res.compute(axiom, (a, container) -> container == null ? new Wrap<>(a, triples) : container.add(triples));
            });
            return new HashSet<>(res.values());
        } catch (Exception e) {
            throw new OntApiException(String.format("Can't process reading. Translator <%s>.", getClass()), e);
        }
    }

    /**
     * returns the stream of statements defining the axiom.
     *
     * @param model {@link OntGraphModel} the model
     * @return Stream of {@link OntStatement}
     */
    abstract Stream<OntStatement> statements(OntGraphModel model);

    /**
     * todo: going to replace this method with {@link #asAxiom(OntStatement)}
     *
     * @param statement   {@link OntStatement}
     * @param annotations Set of {@link OWLAnnotation}
     * @return {@link OWLAxiom}
     */
    @Deprecated
    abstract Axiom create(OntStatement statement, Set<OWLAnnotation> annotations);

    /**
     *
     * @param statement {@link OntStatement} the statement which determines the axiom
     * @return {@link Wrap} around the {@link OWLAxiom}
     */
    abstract Wrap<Axiom> asAxiom(OntStatement statement);

    /**
     * returns the container with set of {@link OWLAnnotation} associated with the specified statement.
     *
     * @param statement {@link OntStatement}
     * @return {@link ru.avicomp.ontapi.translators.Wrap.Collection} of {@link OWLAnnotation}
     */
    Wrap.Collection<OWLAnnotation> annotations(OntStatement statement) {
        return new Wrap.Collection<>(ReadHelper.getAnnotations(statement));
    }

    /**
     * todo: should be passed from outside
     *
     * @return {@link OWLDataFactory}
     */
    public OWLDataFactory getDataFactory() {
        return OWL_DATA_FACTORY;
    }

    /**
     * todo: will be removed (settings will be passed from outside)
     * @return {@link ru.avicomp.ontapi.translators.AxiomParserProvider.Config} - it's a temporary solution.
     */
    public AxiomParserProvider.Config getConfig() {
        return config;
    }

    public void setConfig(AxiomParserProvider.Config config) {
        this.config = OntApiException.notNull(config, "Null config.");
    }
}
