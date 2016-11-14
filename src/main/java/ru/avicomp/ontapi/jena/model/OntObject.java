package ru.avicomp.ontapi.jena.model;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * TODO: add way to work with OntStatements.
 * Base Resource.
 * <p>
 * Created by szuev on 01.11.2016.
 */
public interface OntObject extends Resource {

    @Override
    GraphModel getModel();

    /**
     * returns root triplet statement,
     * usually it is declaration with predicate rdf:type
     * @return OntStatement
     */
    OntStatement getRoot();

    OntStatement addStatement(Property property, RDFNode value);

    OntStatement getStatement(Property property, OntObject object);

    Stream<OntStatement> statements(Property property);

    /**
     * returns all statements related to this object (i.e. with subject=this)
     *
     * @return Stream of statements.
     */
    Stream<OntStatement> statements();

    void remove(Property property, RDFNode object);

    /**
     * Returns the stream of all annotations attached to this object (not only to main-triple).
     * Each annotation could be plain (assertion) or bulk annotation (with/without sub-annotations).
     * Sub-annotations are not included to this stream.
     * <p>
     * According to OWL2-DL specification OntObject should be an uri-resource (i.e. not anonymous),
     * but we extend this behaviour for more generality.
     *
     * @return Stream of {@link OntStatement}s, each of them has as key {@link OntNAP} and as value any {@link RDFNode}.
     */
    default Stream<OntStatement> annotations() {
        return statements().map(OntStatement::annotations).flatMap(Function.identity());
    }

    /**
     * add annotation assertion.
     * it could be expanded to bulk form by adding sub-annotation
     *
     * @param property Named annotation property.
     * @param value    RDFNode (uri-resource, literal or anonymous individual)
     * @return OntStatement for newly added annotation.
     * @throws ru.avicomp.ontapi.OntException in case input is wrong.
     */
    default OntStatement addAnnotation(OntNAP property, RDFNode value) {
        return getRoot().addAnnotation(property, value);
    }

    default OntStatement addComment(String txt, String lang) {
        return addAnnotation(getModel().getRDFSComment(), ResourceFactory.createLangLiteral(txt, lang));
    }

    default OntStatement addLabel(String txt, String lang) {
        return addAnnotation(getModel().getRDFSLabel(), ResourceFactory.createLangLiteral(txt, lang));
    }

    default void clearAnnotations() {
        Set<OntStatement> annotated = statements().filter(OntStatement::hasAnnotations).collect(Collectors.toSet());
        annotated.forEach(OntStatement::clearAnnotations);
        annotations().forEach(a -> removeAll(a.getPredicate()));
    }
}
