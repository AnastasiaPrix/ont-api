package ru.avicomp.ontapi.tests;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.log4j.Logger;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import ru.avicomp.ontapi.OntFormat;
import ru.avicomp.ontapi.OntManagerFactory;
import ru.avicomp.ontapi.OntologyModel;
import ru.avicomp.ontapi.utils.ReadWriteUtils;
import ru.avicomp.ontapi.utils.TestUtils;

/**
 * base and utility class for graph-tests
 * <p>
 * Created by @szuev on 02.10.2016.
 */
public abstract class GraphTestBase {
    static final Logger LOGGER = Logger.getLogger(GraphTestBase.class);

    public static void debug(OWLOntology ontology) {
        LOGGER.info("DEBUG:");
        ReadWriteUtils.print(ontology, OntFormat.TURTLE);
        LOGGER.debug("Axioms:");
        ontology.axioms().forEach(LOGGER::debug);
    }

    Stream<OWLAxiom> filterAxioms(OWLOntology ontology, AxiomType... excluded) {
        if (excluded.length == 0) return ontology.axioms();
        List<AxiomType> types = Stream.of(excluded).collect(Collectors.toList());
        return ontology.axioms().filter(axiom -> !types.contains(axiom.getAxiomType()));
    }

    void checkAxioms(OntologyModel original, AxiomType... excluded) {
        LOGGER.info("Load ontology to another manager from jena graph.");
        OWLOntologyManager manager = OntManagerFactory.createOWLManager();
        OWLOntology result = ReadWriteUtils.convertJenaToOWL(manager, original.asGraphModel());
        LOGGER.info("All (actual) axioms from reloaded ontology:");
        result.axioms().forEach(LOGGER::info);
        Map<AxiomType, List<OWLAxiom>> expected = TestUtils.toMap(filterAxioms(original, excluded));
        Map<AxiomType, List<OWLAxiom>> actual = TestUtils.toMap(filterAxioms(result, excluded));
        TestUtils.compareAxioms(expected, actual);
    }
}
