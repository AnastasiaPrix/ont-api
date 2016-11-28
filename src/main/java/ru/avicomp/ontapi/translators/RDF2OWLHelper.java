package ru.avicomp.ontapi.translators;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.out.NodeFmtLib;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.vocab.OWLFacet;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

import ru.avicomp.ontapi.OntApiException;
import ru.avicomp.ontapi.jena.JenaUtils;
import ru.avicomp.ontapi.jena.model.*;
import uk.ac.manchester.cs.owl.owlapi.*;

/**
 * Helper to translate rdf-graph to the axioms.
 * TODO:
 * <p>
 * Created by @szuev on 25.11.2016.
 */
public class RDF2OWLHelper {

    public static OWLEntity getEntity(OntEntity entity) {
        IRI iri = IRI.create(OntApiException.notNull(entity, "Null entity.").getURI());
        if (OntClass.class.isInstance(entity)) {
            return new OWLClassImpl(iri);
        } else if (OntDT.class.isInstance(entity)) {
            return new OWLDatatypeImpl(iri);
        } else if (OntIndividual.Named.class.isInstance(entity)) {
            return new OWLNamedIndividualImpl(iri);
        } else if (OntNAP.class.isInstance(entity)) {
            return new OWLAnnotationPropertyImpl(iri);
        } else if (OntNDP.class.isInstance(entity)) {
            return new OWLDataPropertyImpl(iri);
        } else if (OntNOP.class.isInstance(entity)) {
            return new OWLObjectPropertyImpl(iri);
        }
        throw new OntApiException("Unsupported " + entity);
    }

    public static OWLAnonymousIndividual getAnonymousIndividual(OntIndividual.Anonymous individual) {
        String label = NodeFmtLib.encodeBNodeLabel(OntApiException.notNull(individual, "Null individual.").asNode().getBlankNodeLabel());
        return new OWLAnonymousIndividualImpl(NodeID.getNodeID(label));
    }

    public static OWLIndividual getIndividual(OntIndividual individual) {
        if (OntApiException.notNull(individual, "Null individual").isURIResource()) {
            return new OWLNamedIndividualImpl(IRI.create(individual.getURI()));
        }
        return getAnonymousIndividual(individual.as(OntIndividual.Anonymous.class));
    }

    public static OWLLiteral getLiteral(Literal literal) {
        String txt = OntApiException.notNull(literal, "Null literal").getLexicalForm();
        String lang = literal.getLanguage();
        OWLDatatype dt = new OWLDatatypeImpl(IRI.create(literal.getDatatypeURI()));
        return new OWLLiteralImpl(txt, lang, dt);
    }

    public static OWLAnnotationSubject getAnnotationSubject(Resource resource) {
        if (resource.isURIResource()) {
            return IRI.create(resource.getURI());
        }
        if (resource.canAs(OntIndividual.Anonymous.class)) {
            return getAnonymousIndividual(resource.as(OntIndividual.Anonymous.class));
        }
        throw new OntApiException("Not an OWLAnnotationSubject " + resource);
    }

    public static OWLAnnotationValue getAnnotationValue(RDFNode node) {
        if (OntApiException.notNull(node, "Null node").isLiteral()) {
            return getLiteral(node.asLiteral());
        }
        if (node.isURIResource()) {
            return IRI.create(node.asResource().getURI());
        }
        if (node.canAs(OntIndividual.Anonymous.class)) {
            return getAnonymousIndividual(node.as(OntIndividual.Anonymous.class));
        }
        throw new OntApiException("Not an OWLAnnotationValue " + node);
    }

    public static OWLAnnotationProperty getAnnotationProperty(OntNAP nap) {
        IRI iri = IRI.create(OntApiException.notNull(nap, "Null annotation property.").getURI());
        return new OWLAnnotationPropertyImpl(iri);
    }

    public static OWLDataProperty getDataProperty(OntNDP nap) {
        IRI iri = IRI.create(OntApiException.notNull(nap, "Null data property.").getURI());
        return new OWLDataPropertyImpl(iri);
    }

    public static OWLObjectPropertyExpression getObjectProperty(OntOPE ope) {
        OntApiException.notNull(ope, "Null object property.");
        OWLObjectPropertyExpression res;
        if (ope.isAnon()) { //todo: handle inverse of inverseOf
            OWLObjectProperty op = new OWLObjectPropertyImpl(IRI.create(ope.as(OntOPE.Inverse.class).getDirect().getURI()));
            res = op.getInverseProperty();
        } else {
            res = new OWLObjectPropertyImpl(IRI.create(ope.getURI()));
        }
        return res;
    }

    public static OWLDatatype getDatatype(OntDT dt) {
        IRI iri = IRI.create(OntApiException.notNull(dt, "Null datatype.").getURI());
        return new OWLDatatypeImpl(iri);
    }

    public static Set<OWLTripleSet<OWLAnnotation>> getBulkAnnotations(OntObject object) {
        return getBulkAnnotations(OntApiException.notNull(object, "Null ont-object.").getRoot());
    }

    public static Set<OWLTripleSet<OWLAnnotation>> getBulkAnnotations(OntStatement statement) {
        return statement.annotations()
                .filter(OntStatement::hasAnnotations)
                .map(RDF2OWLHelper::getHierarchicalAnnotations).collect(Collectors.toSet());
    }

    private static OWLTripleSet<OWLAnnotation> getHierarchicalAnnotations(OntStatement a) {
        OntObject ann = a.getSubject().as(OntObject.class);
        Set<Triple> triples = new HashSet<>();
        Stream.of(RDF.type, OWL2.annotatedSource, OWL2.annotatedProperty, OWL2.annotatedTarget)
                .forEach(p -> triples.add(ann.getRequiredProperty(p).asTriple()));
        triples.add(a.asTriple());

        OWLAnnotationProperty p = getAnnotationProperty(a.getPredicate().as(OntNAP.class));
        OWLAnnotationValue v = getAnnotationValue(a.getObject());
        Set<OWLTripleSet<OWLAnnotation>> children = a.annotations().map(RDF2OWLHelper::getHierarchicalAnnotations).collect(Collectors.toSet());
        OWLAnnotation res = new OWLAnnotationImpl(p, v, children.stream().map(OWLTripleSet::getObject));
        triples.addAll(children.stream().map(OWLTripleSet::getTriples).map(Collection::stream).flatMap(Function.identity()).collect(Collectors.toSet()));
        return new OWLTripleSet<>(res, triples);
    }

    public static Set<Triple> getAssociatedTriples(RDFNode root) {
        return root.isAnon() ? JenaUtils.getAssociatedStatements(root.asResource()).stream().map(Statement::asTriple).collect(Collectors.toSet()) : Collections.emptySet();
    }

    public static OWLFacetRestriction getFacetRestriction(OntFR fr) {
        OWLLiteral literal = getLiteral(OntApiException.notNull(fr, "Null facet restriction.").getValue());
        if (OntFR.Length.class.isInstance(fr)) return new OWLFacetRestrictionImpl(OWLFacet.LENGTH, literal);
        if (OntFR.MinLength.class.isInstance(fr)) return new OWLFacetRestrictionImpl(OWLFacet.MIN_LENGTH, literal);
        if (OntFR.MaxLength.class.isInstance(fr)) return new OWLFacetRestrictionImpl(OWLFacet.MAX_LENGTH, literal);
        if (OntFR.MinInclusive.class.isInstance(fr))
            return new OWLFacetRestrictionImpl(OWLFacet.MIN_INCLUSIVE, literal);
        if (OntFR.MaxInclusive.class.isInstance(fr))
            return new OWLFacetRestrictionImpl(OWLFacet.MAX_INCLUSIVE, literal);
        if (OntFR.MinExclusive.class.isInstance(fr))
            return new OWLFacetRestrictionImpl(OWLFacet.MIN_EXCLUSIVE, literal);
        if (OntFR.MaxExclusive.class.isInstance(fr))
            return new OWLFacetRestrictionImpl(OWLFacet.MAX_EXCLUSIVE, literal);
        if (OntFR.Pattern.class.isInstance(fr)) return new OWLFacetRestrictionImpl(OWLFacet.PATTERN, literal);
        if (OntFR.FractionDigits.class.isInstance(fr))
            return new OWLFacetRestrictionImpl(OWLFacet.FRACTION_DIGITS, literal);
        if (OntFR.TotalDigits.class.isInstance(fr)) return new OWLFacetRestrictionImpl(OWLFacet.TOTAL_DIGITS, literal);
        if (OntFR.LangRange.class.isInstance(fr)) return new OWLFacetRestrictionImpl(OWLFacet.LANG_RANGE, literal);
        throw new OntApiException("Unsupported facet restriction " + fr);
    }

    private static OWLDataRange fetchDataRange(OntDR dr) {
        if (dr == null)
            return new OWLDatatypeImpl(OWLRDFVocabulary.RDFS_LITERAL.getIRI());
        return getDataRange(dr);
    }

    public static OWLDataRange getDataRange(OntDR dr) {
        if (OntApiException.notNull(dr, "Null data range.").isURIResource()) {
            return getDatatype(dr.as(OntDT.class));
        }
        if (OntDR.Restriction.class.isInstance(dr)) {
            OntDR.Restriction _dr = (OntDR.Restriction) dr;
            return new OWLDatatypeRestrictionImpl(getDatatype(_dr.getDatatype()), _dr.facetRestrictions().map(RDF2OWLHelper::getFacetRestriction).collect(Collectors.toSet()));
        }
        if (OntDR.ComplementOf.class.isInstance(dr)) {
            OntDR.ComplementOf _dr = (OntDR.ComplementOf) dr;
            return new OWLDataComplementOfImpl(getDataRange(_dr.getDataRange()));
        }
        if (OntDR.UnionOf.class.isInstance(dr)) {
            OntDR.UnionOf _dr = (OntDR.UnionOf) dr;
            return new OWLDataUnionOfImpl(_dr.dataRanges().map(RDF2OWLHelper::getDataRange));
        }
        if (OntDR.IntersectionOf.class.isInstance(dr)) {
            OntDR.IntersectionOf _dr = (OntDR.IntersectionOf) dr;
            return new OWLDataIntersectionOfImpl(_dr.dataRanges().map(RDF2OWLHelper::getDataRange));
        }
        if (OntDR.OneOf.class.isInstance(dr)) {
            OntDR.OneOf _dr = (OntDR.OneOf) dr;
            return new OWLDataOneOfImpl(_dr.values().map(RDF2OWLHelper::getLiteral));
        }
        throw new OntApiException("Unsupported data range expression " + dr);
    }

    private static OWLClassExpression fetchClassExpression(OntCE ce) {
        if (ce == null)
            return new OWLClassImpl(OWLRDFVocabulary.OWL_THING.getIRI());
        return getClassExpression(ce);
    }

    public static OWLClassExpression getClassExpression(OntCE ce) {
        if (OntApiException.notNull(ce, "Null class expression.").isURIResource()) {
            return new OWLClassImpl(IRI.create(ce.getURI()));
        }
        if (OntCE.ObjectSomeValuesFrom.class.isInstance(ce)) {
            OntCE.ObjectSomeValuesFrom _ce = (OntCE.ObjectSomeValuesFrom) ce;
            return new OWLObjectSomeValuesFromImpl(getObjectProperty(_ce.getOnProperty()), getClassExpression(_ce.getValue()));
        }
        if (OntCE.DataSomeValuesFrom.class.isInstance(ce)) {
            OntCE.DataSomeValuesFrom _ce = (OntCE.DataSomeValuesFrom) ce;
            return new OWLDataSomeValuesFromImpl(getDataProperty(_ce.getOnProperty()), getDataRange(_ce.getValue()));
        }
        if (OntCE.ObjectAllValuesFrom.class.isInstance(ce)) {
            OntCE.ObjectAllValuesFrom _ce = (OntCE.ObjectAllValuesFrom) ce;
            return new OWLObjectAllValuesFromImpl(getObjectProperty(_ce.getOnProperty()), getClassExpression(_ce.getValue()));
        }
        if (OntCE.DataAllValuesFrom.class.isInstance(ce)) {
            OntCE.DataAllValuesFrom _ce = (OntCE.DataAllValuesFrom) ce;
            return new OWLDataAllValuesFromImpl(getDataProperty(_ce.getOnProperty()), getDataRange(_ce.getValue()));
        }
        if (OntCE.ObjectHasValue.class.isInstance(ce)) {
            OntCE.ObjectHasValue _ce = (OntCE.ObjectHasValue) ce;
            return new OWLObjectHasValueImpl(getObjectProperty(_ce.getOnProperty()), getIndividual(_ce.getValue()));
        }
        if (OntCE.DataHasValue.class.isInstance(ce)) {
            OntCE.DataHasValue _ce = (OntCE.DataHasValue) ce;
            return new OWLDataHasValueImpl(getDataProperty(_ce.getOnProperty()), getLiteral(_ce.getValue()));
        }
        if (OntCE.ObjectMinCardinality.class.isInstance(ce)) {
            OntCE.ObjectMinCardinality _ce = (OntCE.ObjectMinCardinality) ce;
            return new OWLObjectMinCardinalityImpl(getObjectProperty(_ce.getOnProperty()), _ce.getCardinality(), fetchClassExpression(_ce.getValue()));
        }
        if (OntCE.DataMinCardinality.class.isInstance(ce)) {
            OntCE.DataMinCardinality _ce = (OntCE.DataMinCardinality) ce;
            return new OWLDataMinCardinalityImpl(getDataProperty(_ce.getOnProperty()), _ce.getCardinality(), fetchDataRange(_ce.getValue()));
        }
        if (OntCE.ObjectMaxCardinality.class.isInstance(ce)) {
            OntCE.ObjectMaxCardinality _ce = (OntCE.ObjectMaxCardinality) ce;
            return new OWLObjectMaxCardinalityImpl(getObjectProperty(_ce.getOnProperty()), _ce.getCardinality(), fetchClassExpression(_ce.getValue()));
        }
        if (OntCE.DataMaxCardinality.class.isInstance(ce)) {
            OntCE.DataMaxCardinality _ce = (OntCE.DataMaxCardinality) ce;
            return new OWLDataMaxCardinalityImpl(getDataProperty(_ce.getOnProperty()), _ce.getCardinality(), fetchDataRange(_ce.getValue()));
        }
        if (OntCE.ObjectCardinality.class.isInstance(ce)) {
            OntCE.ObjectCardinality _ce = (OntCE.ObjectCardinality) ce;
            return new OWLObjectExactCardinalityImpl(getObjectProperty(_ce.getOnProperty()), _ce.getCardinality(), fetchClassExpression(_ce.getValue()));
        }
        if (OntCE.DataCardinality.class.isInstance(ce)) {
            OntCE.DataCardinality _ce = (OntCE.DataCardinality) ce;
            return new OWLDataExactCardinalityImpl(getDataProperty(_ce.getOnProperty()), _ce.getCardinality(), fetchDataRange(_ce.getValue()));
        }
        if (OntCE.HasSelf.class.isInstance(ce)) {
            OntCE.HasSelf _ce = (OntCE.HasSelf) ce;
            return new OWLObjectHasSelfImpl(getObjectProperty(_ce.getOnProperty()));
        }
        if (OntCE.UnionOf.class.isInstance(ce)) {
            OntCE.UnionOf _ce = (OntCE.UnionOf) ce;
            return new OWLObjectUnionOfImpl(_ce.components().map(RDF2OWLHelper::getClassExpression));
        }
        if (OntCE.IntersectionOf.class.isInstance(ce)) {
            OntCE.IntersectionOf _ce = (OntCE.IntersectionOf) ce;
            return new OWLObjectIntersectionOfImpl(_ce.components().map(RDF2OWLHelper::getClassExpression));
        }
        if (OntCE.OneOf.class.isInstance(ce)) {
            OntCE.OneOf _ce = (OntCE.OneOf) ce;
            return new OWLObjectOneOfImpl(_ce.components().map(RDF2OWLHelper::getIndividual));
        }
        if (OntCE.ComplementOf.class.isInstance(ce)) {
            OntCE.ComplementOf _ce = (OntCE.ComplementOf) ce;
            return new OWLObjectComplementOfImpl(getClassExpression(_ce.getValue()));
        }
        throw new OntApiException("Unsupported class expression " + ce);
    }

    public static class StatementContent {
        private final OntStatement statement;
        private final Set<Triple> triples;
        private final Set<OWLAnnotation> annotations;

        public StatementContent(OntStatement statement) {
            this.statement = OntApiException.notNull(statement, "Null statement.");
            this.triples = new HashSet<>();
            this.annotations = new HashSet<>();
            triples.add(statement.asTriple());
            triples.addAll(getAssociatedTriples(statement.getObject()));
            getBulkAnnotations(statement).forEach(a -> {
                triples.addAll(a.getTriples());
                annotations.add(a.getObject());
            });
        }

        public OntStatement getStatement() {
            return statement;
        }

        public Set<Triple> getTriples() {
            return triples;
        }

        public Set<OWLAnnotation> getAnnotations() {
            return annotations;
        }
    }
}
