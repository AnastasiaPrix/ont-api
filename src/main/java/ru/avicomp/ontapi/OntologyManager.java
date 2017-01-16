package ru.avicomp.ontapi;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.stream.Stream;

import org.apache.jena.graph.Graph;
import org.semanticweb.owlapi.model.*;

import ru.avicomp.ontapi.jena.model.OntGraphModel;

import static org.semanticweb.owlapi.util.OWLAPIPreconditions.emptyOptional;
import static org.semanticweb.owlapi.util.OWLAPIPreconditions.optional;

/**
 * Ontology manager.
 * Base class: {@link OWLOntologyManager}
 * It is the main point for creating, loading and accessing {@link OntologyModel}s models.
 *
 * Created by szuev on 24.10.2016.
 */
public interface OntologyManager extends OWLOntologyManager {

    void setGraphFactory(GraphFactory factory);

    GraphFactory getGraphFactory();

    OntologyModel getOntology(@Nullable IRI iri);

    OntologyModel getOntology(@Nonnull OWLOntologyID id);

    @Nullable
    OntologyModel getImportedOntology(@Nonnull OWLImportsDeclaration declaration);

    /**
     * Creates an ontology.
     * <p>
     * Note: this method doesn't throw a checked exception {@link OWLOntologyCreationException} like OWL-API.
     * Instead it there is an unchecked exception {@link OntApiException} which may wrap {@link OWLOntologyCreationException}.
     * OWL-API and ONT-API work in different ways.
     * So i see it is impossible to save the same places for exceptions.
     * For example in OWL-API you could expect some kind of exception during saving ontology,
     * but in ONT-API there would be another kind of exception during adding axiom.
     * I believe there is no reasons to save the same behaviour with exceptions everywhere.
     * The return type is also changed from {@link org.semanticweb.owlapi.model.OWLOntology} to our class {@link OntologyModel}.
     *
     * @param id {@link OWLOntologyID}
     * @return ontology {@link OntologyModel}
     * @throws OntApiException in case something wrong.
     */
    OntologyModel createOntology(@Nonnull OWLOntologyID id);

    OntologyModel loadOntology(@Nonnull IRI source) throws OWLOntologyCreationException;

    default OntologyModel createOntology() {
        return createOntology(new OWLOntologyID());
    }

    default OntologyModel createOntology(IRI iri) {
        return createOntology(new OWLOntologyID(optional(iri), emptyOptional(IRI.class)));
    }

    default OntGraphModel loadGraphModel(@Nonnull String source) throws OWLOntologyCreationException {
        return loadOntology(IRI.create(source)).asGraphModel();
    }

    default OntGraphModel getGraphModel(@Nullable String uri) {
        OntologyModel res = getOntology(uri == null ? null : IRI.create(uri));
        return res == null ? null : res.asGraphModel();
    }

    default OntGraphModel createGraphModel(@Nullable String uri) {
        return createOntology(uri == null ? null : IRI.create(uri)).asGraphModel();
    }

    default Stream<OntGraphModel> models() {
        return ontologies().map(OntologyModel.class::cast).map(OntologyModel::asGraphModel);
    }

    interface GraphFactory extends Serializable {
        Graph create();
    }
}
