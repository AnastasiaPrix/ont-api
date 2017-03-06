package ru.avicomp.ontapi.translators;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.jena.graph.FrontsTriple;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.out.NodeFmtLib;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.OWLAPIStreamUtils;
import org.semanticweb.owlapi.vocab.OWLFacet;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

import ru.avicomp.ontapi.OntApiException;
import ru.avicomp.ontapi.jena.impl.OntObjectImpl;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;
import uk.ac.manchester.cs.owl.owlapi.*;

/**
 * Helper to translate rdf-graph to the owl-objects form.
 * TODO: need to handle bad recursions (the simplest example: "_:b0 rdfs:subClassOf _:b0")
 * TODO: replace the return types of all methods from OWLObject to Wrap<OWLObject>
 * <p>
 * Created by @szuev on 25.11.2016.
 */
public class ReadHelper {
    public static final OWLDataFactory OWL_DATA_FACTORY = new OWLDataFactoryImpl();

    /**
     * todo: to internal use only, not ready
     *
     * @param object
     * @param df
     * @param <O>
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <O extends OWLObject> Wrap<O> mapObject(OntObject object, OWLDataFactory df) {
        Class<? extends OntObject> view = OntApiException.notNull((OntObjectImpl) object, "Null object view " + object).getActualClass();
        if (OntCE.class.isAssignableFrom(view)) {
            return (Wrap<O>) _getClassExpression((OntCE) object, df, new HashSet<>());
        }
        throw new OntApiException("Unsupported " + object);
    }

    /**
     * todo:
     *
     * @param entity {@link OntEntity}
     * @param df     {@link OWLDataFactory}
     * @return {@link Wrap}
     */
    public static Wrap<OWLEntity> _getEntity(OntEntity entity, OWLDataFactory df) {
        return Wrap.create(getEntity(entity, df), entity);
    }

    public static OWLEntity getEntity(OntEntity entity) {
        return getEntity(entity, OWL_DATA_FACTORY);
    }

    public static OWLEntity getEntity(OntEntity entity, OWLDataFactory df) {
        IRI iri = IRI.create(OntApiException.notNull(entity, "Null entity.").getURI());
        Class<? extends OntObject> view = OntApiException.notNull(((OntObjectImpl) entity).getActualClass(),
                "Can't determine view of entity " + entity);
        if (OntClass.class.equals(view)) {
            return df.getOWLClass(iri);
        } else if (OntDT.class.equals(view)) {
            return df.getOWLDatatype(iri);
        } else if (OntIndividual.Named.class.equals(view)) {
            return df.getOWLNamedIndividual(iri);
        } else if (OntNAP.class.equals(view)) {
            return df.getOWLAnnotationProperty(iri);
        } else if (OntNDP.class.equals(view)) {
            return df.getOWLDataProperty(iri);
        } else if (OntNOP.class.equals(view)) {
            return df.getOWLObjectProperty(iri);
        }
        throw new OntApiException("Unsupported " + entity);
    }

    public static OWLAnonymousIndividual getAnonymousIndividual(OntIndividual.Anonymous individual) {
        return getAnonymousIndividual((RDFNode) OntApiException.notNull(individual, "Null individual."));
    }

    /**
     * todo:
     *
     * @param anon {@link ru.avicomp.ontapi.jena.model.OntIndividual.Anonymous}
     * @param df   {@link OWLDataFactory}
     * @return {@link Wrap}
     */
    private static Wrap<OWLAnonymousIndividual> _getAnonymousIndividual(OntIndividual.Anonymous anon, OWLDataFactory df) {
        if (!anon.isAnon()) throw new OntApiException("Not anon " + anon);
        String label = NodeFmtLib.encodeBNodeLabel(anon.asNode().getBlankNodeLabel());
        return Wrap.create(df.getOWLAnonymousIndividual(label), anon);
    }

    private static OWLAnonymousIndividual getAnonymousIndividual(RDFNode anon) {
        if (!anon.isAnon()) throw new OntApiException("Not anon " + anon);
        String label = NodeFmtLib.encodeBNodeLabel(anon.asNode().getBlankNodeLabel());
        return new OWLAnonymousIndividualImpl(NodeID.getNodeID(label));
    }

    /**
     * todo:
     *
     * @param individual {@link OntIndividual}
     * @param df         {@link OWLDataFactory}
     * @return {@link Wrap}
     */
    public static Wrap<? extends OWLIndividual> _getIndividual(OntIndividual individual, OWLDataFactory df) {
        if (OntApiException.notNull(individual, "Null individual").isURIResource()) {
            return Wrap.create(df.getOWLNamedIndividual(IRI.create(individual.getURI())), individual);
        }
        return _getAnonymousIndividual(individual.as(OntIndividual.Anonymous.class), df);
    }

    public static OWLIndividual getIndividual(OntIndividual individual) {
        if (OntApiException.notNull(individual, "Null individual").isURIResource()) {
            return new OWLNamedIndividualImpl(IRI.create(individual.getURI()));
        }
        return getAnonymousIndividual(individual.as(OntIndividual.Anonymous.class));
    }

    /**
     * todo:
     * NOTE: different implementations of {@link OWLLiteral} have different mechanism to calculate hash.
     * For example {@link OWLLiteralImplInteger}.hashCode != {@link OWLLiteralImpl}.hashCode
     * So even if {@link OWLLiteral}s equal there is no guarantee that {@link Set}s of {@link OWLLiteral}s equal too.
     *
     * @param literal {@link Literal}
     * @param df      {@link OWLDataFactory}
     * @return {@link Wrap}
     */
    public static Wrap<OWLLiteral> _getLiteral(Literal literal, OWLDataFactory df) {
        String txt = OntApiException.notNull(literal, "Null literal").getLexicalForm();
        String lang = literal.getLanguage();
        OWLDatatype dt = df.getOWLDatatype(IRI.create(literal.getDatatypeURI()));
        if (lang != null && !lang.isEmpty()) {
            txt = txt + "@" + lang;
        }
        OWLLiteral res = df.getOWLLiteral(txt, dt);
        return new Wrap<>(res);
    }

    /**
     * NOTE: different implementations of {@link OWLLiteral} have different mechanism to calculate hash.
     * For example {@link OWLLiteralImplInteger}.hashCode != {@link OWLLiteralImpl}.hashCode
     * So even if {@link OWLLiteral}s equal there is no guarantee that {@link Set}s of {@link OWLLiteral}s equal too.
     *
     * @param literal {@link Literal} - jena literal.
     * @return {@link OWLLiteralImpl} - OWL-API literal.
     */
    public static OWLLiteral getLiteral(Literal literal) {
        String txt = OntApiException.notNull(literal, "Null literal").getLexicalForm();
        String lang = literal.getLanguage();
        OWLDatatype dt = new OWLDatatypeImpl(IRI.create(literal.getDatatypeURI()));
        return new OWLLiteralImpl(txt, lang, dt);
    }

    public static Wrap<IRI> wrapIRI(OntObject object) {
        return Wrap.create(IRI.create(object.getURI()), object);
    }

    /**
     * todo:
     *
     * @param resource {@link OntObject}
     * @param df       {@link OWLDataFactory}
     * @return {@link Wrap}
     */
    public static Wrap<? extends OWLAnnotationSubject> _getAnnotationSubject(OntObject resource, OWLDataFactory df) {
        if (OntApiException.notNull(resource, "Null resource").isURIResource()) {
            return wrapIRI(resource);
        }
        if (resource.isAnon()) {
            return _getAnonymousIndividual(Models.asAnonymousIndividual(resource), df);
        }
        throw new OntApiException("Not an AnnotationSubject " + resource);
    }

    public static OWLAnnotationSubject getAnnotationSubject(Resource resource) {
        if (OntApiException.notNull(resource, "Null resource").isURIResource()) {
            return IRI.create(resource.getURI());
        }
        if (resource.isAnon()) {
            return getAnonymousIndividual(Models.asAnonymousIndividual(resource));
        }
        throw new OntApiException("Not an AnnotationSubject " + resource);
    }

    /**
     * todo:
     *
     * @param node {@link RDFNode}
     * @param df   {@link OWLDataFactory}
     * @return {@link Wrap}
     */
    public static Wrap<? extends OWLAnnotationValue> _getAnnotationValue(RDFNode node, OWLDataFactory df) {
        if (OntApiException.notNull(node, "Null node").isLiteral()) {
            return _getLiteral(node.asLiteral(), df);
        }
        if (node.isURIResource()) {
            OntObject r = node.as(OntObject.class);
            return Wrap.create(IRI.create(r.getURI()), r);
        }
        if (node.isAnon()) {
            return _getAnonymousIndividual(Models.asAnonymousIndividual(node), df);
        }
        throw new OntApiException("Not an AnnotationValue " + node);
    }

    public static OWLAnnotationValue getAnnotationValue(RDFNode node) {
        if (OntApiException.notNull(node, "Null node").isLiteral()) {
            return getLiteral(node.asLiteral());
        }
        if (node.isURIResource()) {
            return IRI.create(node.asResource().getURI());
        }
        if (node.isAnon()) {
            return getAnonymousIndividual(Models.asAnonymousIndividual(node));
        }
        throw new OntApiException("Not an AnnotationValue " + node);
    }

    /**
     * @param property {@link OntPE}
     * @param df       {@link OWLDataFactory}
     * @return {@link Wrap} around {@link OWLPropertyExpression}
     */
    public static Wrap<? extends OWLPropertyExpression> _getProperty(OntPE property, OWLDataFactory df) {
        if (OntApiException.notNull(property, "Null property.").canAs(OntNAP.class)) {
            return ReadHelper._getAnnotationProperty(property.as(OntNAP.class), df);
        }
        if (property.canAs(OntNDP.class)) {
            return ReadHelper._getDataProperty(property.as(OntNDP.class), df);
        }
        if (property.canAs(OntOPE.class)) {
            return ReadHelper._getObjectProperty(property.as(OntOPE.class), df);
        }
        throw new OntApiException("Unsupported property " + property);
    }

    public static OWLPropertyExpression getProperty(OntPE property) {
        if (OntApiException.notNull(property, "Null property.").canAs(OntNAP.class)) {
            return ReadHelper.getAnnotationProperty(property.as(OntNAP.class));
        }
        if (property.canAs(OntNDP.class)) {
            return ReadHelper.getDataProperty(property.as(OntNDP.class));
        }
        if (property.canAs(OntOPE.class)) {
            return ReadHelper.getObjectProperty(property.as(OntOPE.class));
        }
        throw new OntApiException("Unsupported property " + property);
    }

    /**
     * todo
     *
     * @param nap {@link OntNAP}
     * @param df  {@link OWLDataFactory}
     * @return {@link Wrap}
     */
    public static Wrap<OWLAnnotationProperty> _getAnnotationProperty(OntNAP nap, OWLDataFactory df) {
        IRI iri = IRI.create(OntApiException.notNull(nap, "Null annotation property.").getURI());
        return Wrap.create(df.getOWLAnnotationProperty(iri), nap);
    }

    public static OWLAnnotationProperty getAnnotationProperty(OntNAP nap) {
        IRI iri = IRI.create(OntApiException.notNull(nap, "Null annotation property.").getURI());
        return new OWLAnnotationPropertyImpl(iri);
    }

    /**
     * todo:
     *
     * @param nap {@link OntNDP}
     * @param df  {@link OWLDataFactory}
     * @return {@link Wrap}
     */
    public static Wrap<OWLDataProperty> _getDataProperty(OntNDP nap, OWLDataFactory df) {
        IRI iri = IRI.create(OntApiException.notNull(nap, "Null data property.").getURI());
        return Wrap.create(df.getOWLDataProperty(iri), nap);
    }

    public static OWLDataProperty getDataProperty(OntNDP nap) {
        IRI iri = IRI.create(OntApiException.notNull(nap, "Null data property.").getURI());
        return new OWLDataPropertyImpl(iri);
    }

    /**
     * TODO:
     *
     * @param ope {@link OntOPE}
     * @param df  {@link OWLDataFactory}
     * @return {@link Wrap}
     */
    public static Wrap<OWLObjectPropertyExpression> _getObjectProperty(OntOPE ope, OWLDataFactory df) {
        OntApiException.notNull(ope, "Null object property.");
        OWLObjectPropertyExpression res;
        if (ope.isAnon()) { //todo: handle inverse of inverseOf
            OWLObjectProperty op = df.getOWLObjectProperty(IRI.create(ope.as(OntOPE.Inverse.class).getDirect().getURI()));
            res = op.getInverseProperty();
        } else {
            res = df.getOWLObjectProperty(IRI.create(ope.getURI()));
        }
        return Wrap.create(res, ope);
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

    /**
     * todo:
     *
     * @param dt {@link OntDT}
     * @param df {@link OWLDataFactory}
     * @return {@link Wrap}
     */
    public static Wrap<OWLDatatype> _getDatatype(OntDT dt, OWLDataFactory df) {
        IRI iri = IRI.create(OntApiException.notNull(dt, "Null datatype.").getURI());
        return Wrap.create(df.getOWLDatatype(iri), dt);
    }

    public static OWLDatatype getDatatype(OntDT dt) {
        IRI iri = IRI.create(OntApiException.notNull(dt, "Null datatype.").getURI());
        return new OWLDatatypeImpl(iri);
    }

    public static Stream<OWLAnnotation> annotations(OntObject object) {
        return annotations(OntApiException.notNull(object, "Null ont-object.").getRoot());
    }

    public static Stream<OWLAnnotation> annotations(OntStatement statement) {
        return statement.annotations()
                .map(a -> new OWLAnnotationImpl(
                        getAnnotationProperty(a.getPredicate().as(OntNAP.class)),
                        getAnnotationValue(a.getObject()),
                        annotations(a)));
    }

    public static Set<Wrap<OWLAnnotation>> getAnnotations(OntObject object) {
        return getBulkAnnotations(OntApiException.notNull(object, "Null ont-object.").getRoot());
    }

    private static boolean isEntityDeclaration(OntStatement statement) { // todo: what about anonymous individuals?
        return statement.isRoot() && statement.isDeclaration() && statement.getSubject().isURIResource();
    }

    public static Set<Wrap<OWLAnnotation>> getAnnotations(OntStatement statement) {
        if (isEntityDeclaration(statement) && statement.annotations().noneMatch(OntStatement::hasAnnotations)) {
            // for compatibility with OWL-API skip plain annotations attached to an entity:
            // they would go separately as annotation-assertions.
            return Collections.emptySet();
        }
        return getBulkAnnotations(statement);
    }

    private static Set<Wrap<OWLAnnotation>> getBulkAnnotations(OntStatement statement) {
        return statement.annotations().map(a -> a.hasAnnotations() ?
                getHierarchicalAnnotations(a) :
                getPlainAnnotation(a)).collect(Collectors.toSet());
    }

    private static Wrap<OWLAnnotation> getPlainAnnotation(OntStatement a) {
        OWLAnnotationProperty p = getAnnotationProperty(a.getPredicate().as(OntNAP.class));
        OWLAnnotationValue v = getAnnotationValue(a.getObject());
        OWLAnnotation res = new OWLAnnotationImpl(p, v, Stream.empty());
        return Wrap.create(res, a);
    }

    private static Wrap<OWLAnnotation> getHierarchicalAnnotations(OntStatement a) {
        OntObject ann = a.getSubject().as(OntObject.class);
        Set<Triple> triples = new HashSet<>();
        Stream.of(RDF.type, OWL.annotatedSource, OWL.annotatedProperty, OWL.annotatedTarget)
                .forEach(p -> triples.add(ann.getRequiredProperty(p).asTriple()));
        triples.add(a.asTriple());

        OWLAnnotationProperty p = getAnnotationProperty(a.getPredicate().as(OntNAP.class));
        OWLAnnotationValue v = getAnnotationValue(a.getObject());

        Set<Wrap<OWLAnnotation>> children = a.annotations().map(ReadHelper::getHierarchicalAnnotations).collect(Collectors.toSet());
        OWLAnnotation res = new OWLAnnotationImpl(p, v, children.stream().map(Wrap::getObject));
        children.stream().map(Wrap::getTriples).forEach(triples::addAll);
        return new Wrap<>(res, triples);
    }

    /**
     * todo:
     *
     * @param fr {@link OntFR}
     * @param df {@link OWLDataFactory}
     * @return {@link Wrap}
     */
    public static Wrap<OWLFacetRestriction> _getFacetRestriction(OntFR fr, OWLDataFactory df) {
        OWLFacetRestriction res = getFacetRestriction(fr, df);
        return Wrap.create(res, fr);
    }

    public static OWLFacetRestriction getFacetRestriction(OntFR fr) {
        return getFacetRestriction(fr, OWL_DATA_FACTORY);
    }

    public static OWLFacetRestriction getFacetRestriction(OntFR fr, OWLDataFactory df) {
        OWLLiteral literal = getLiteral(OntApiException.notNull(fr, "Null facet restriction.").getValue());
        Class<? extends OntObject> view = OntApiException.notNull(((OntObjectImpl) fr).getActualClass(),
                "Can't determine view of facet restriction " + fr);
        if (OntFR.Length.class.equals(view)) return df.getOWLFacetRestriction(OWLFacet.LENGTH, literal);
        if (OntFR.MinLength.class.equals(view)) return df.getOWLFacetRestriction(OWLFacet.MIN_LENGTH, literal);
        if (OntFR.MaxLength.class.equals(view)) return df.getOWLFacetRestriction(OWLFacet.MAX_LENGTH, literal);
        if (OntFR.MinInclusive.class.equals(view))
            return df.getOWLFacetRestriction(OWLFacet.MIN_INCLUSIVE, literal);
        if (OntFR.MaxInclusive.class.equals(view))
            return df.getOWLFacetRestriction(OWLFacet.MAX_INCLUSIVE, literal);
        if (OntFR.MinExclusive.class.equals(view))
            return df.getOWLFacetRestriction(OWLFacet.MIN_EXCLUSIVE, literal);
        if (OntFR.MaxExclusive.class.equals(view))
            return df.getOWLFacetRestriction(OWLFacet.MAX_EXCLUSIVE, literal);
        if (OntFR.Pattern.class.equals(view)) return df.getOWLFacetRestriction(OWLFacet.PATTERN, literal);
        if (OntFR.FractionDigits.class.equals(view))
            return df.getOWLFacetRestriction(OWLFacet.FRACTION_DIGITS, literal);
        if (OntFR.TotalDigits.class.equals(view)) return df.getOWLFacetRestriction(OWLFacet.TOTAL_DIGITS, literal);
        if (OntFR.LangRange.class.equals(view)) return df.getOWLFacetRestriction(OWLFacet.LANG_RANGE, literal);
        throw new OntApiException("Unsupported facet restriction " + fr);
    }

    private static OWLDataRange fetchDataRange(OntDR dr) {
        if (dr == null)
            return new OWLDatatypeImpl(OWLRDFVocabulary.RDFS_LITERAL.getIRI());
        return getDataRange(dr);
    }

    /**
     * todo:
     * @param dr {@link OntDR}
     * @param df {@link OWLDataFactory}
     * @return {@link Wrap} around {@link OWLDataRange}
     */
    public static Wrap<? extends OWLDataRange> _getDataRange(OntDR dr, OWLDataFactory df) {
        return _getDataRange(dr, df, new HashSet<>());
    }

    /**
     * todo:
     *
     * @param dr   {@link OntDR}
     * @param df   {@link OWLDataFactory}
     * @param seen Set of {@link Resource}
     * @return {@link Wrap}
     */
    @SuppressWarnings("unchecked")
    public static Wrap<? extends OWLDataRange> _getDataRange(OntDR dr, OWLDataFactory df, Set<Resource> seen) {
        if (OntApiException.notNull(dr, "Null data range.").isAnon() && seen.contains(dr)) {
            //todo:
            throw new OntApiException("Recursive loop on data range " + dr);
        }
        seen.add(dr);
        if (dr.isURIResource()) {
            return _getDatatype(dr.as(OntDT.class), df);
        }
        Class<? extends OntObject> view = OntApiException.notNull(((OntObjectImpl) dr).getActualClass(),
                "Can't determine view of data range " + dr);
        if (OntDR.Restriction.class.equals(view)) {
            OntDR.Restriction _dr = (OntDR.Restriction) dr;
            Wrap<OWLDatatype> d = _getDatatype(_dr.getDatatype(), df);
            List<Wrap<OWLFacetRestriction>> restrictions = _dr.facetRestrictions().map(f -> _getFacetRestriction(f, df)).collect(Collectors.toList());
            OWLDataRange res = df.getOWLDatatypeRestriction(d.getObject(), restrictions.stream().map(Wrap::getObject).collect(Collectors.toList()));
            Stream<Triple> triples = Stream.concat(_dr.content().map(FrontsTriple::asTriple),
                    restrictions.stream().map(Wrap::triples).flatMap(Function.identity()));
            return new Wrap<>(res, triples.collect(Collectors.toSet()));
        }
        if (OntDR.ComplementOf.class.equals(view)) {
            OntDR.ComplementOf _dr = (OntDR.ComplementOf) dr;
            Wrap<? extends OWLDataRange> d = _getDataRange(_dr.getDataRange(), df, seen);
            return Wrap.create(df.getOWLDataComplementOf(d.getObject()), _dr).append(d);
        }
        if (OntDR.UnionOf.class.equals(view) ||
                OntDR.IntersectionOf.class.equals(view)) {
            List<Wrap<? extends OWLDataRange>> dataRanges =
                    (OntDR.UnionOf.class.equals(view) ? ((OntDR.UnionOf) dr).dataRanges() : ((OntDR.IntersectionOf) dr).dataRanges())
                            .map(d -> _getDataRange(d, df, seen)).collect(Collectors.toList());
            OWLDataRange res = OntDR.UnionOf.class.equals(view) ?
                    df.getOWLDataUnionOf(dataRanges.stream().map(Wrap::getObject)) :
                    df.getOWLDataIntersectionOf(dataRanges.stream().map(Wrap::getObject));
            Stream<Triple> triples = Stream.concat(dr.content().map(FrontsTriple::asTriple),
                    dataRanges.stream().map(Wrap::triples).flatMap(Function.identity()));
            return new Wrap<>(res, triples.collect(Collectors.toSet()));
        }
        if (OntDR.OneOf.class.equals(view)) {
            OntDR.OneOf _dr = (OntDR.OneOf) dr;
            return Wrap.create(df.getOWLDataOneOf(_dr.values().map(ReadHelper::getLiteral)), _dr);
        }
        throw new OntApiException("Unsupported data range expression " + dr);
    }

    public static OWLDataRange getDataRange(OntDR dr) {
        if (OntApiException.notNull(dr, "Null data range.").isURIResource()) {
            return getDatatype(dr.as(OntDT.class));
        }
        if (OntDR.Restriction.class.isInstance(dr)) {
            OntDR.Restriction _dr = (OntDR.Restriction) dr;
            return new OWLDatatypeRestrictionImpl(getDatatype(_dr.getDatatype()), _dr.facetRestrictions().map(ReadHelper::getFacetRestriction).collect(Collectors.toSet()));
        }
        if (OntDR.ComplementOf.class.isInstance(dr)) {
            OntDR.ComplementOf _dr = (OntDR.ComplementOf) dr;
            return new OWLDataComplementOfImpl(getDataRange(_dr.getDataRange()));
        }
        if (OntDR.UnionOf.class.isInstance(dr)) {
            OntDR.UnionOf _dr = (OntDR.UnionOf) dr;
            return new OWLDataUnionOfImpl(_dr.dataRanges().map(ReadHelper::getDataRange));
        }
        if (OntDR.IntersectionOf.class.isInstance(dr)) {
            OntDR.IntersectionOf _dr = (OntDR.IntersectionOf) dr;
            return new OWLDataIntersectionOfImpl(_dr.dataRanges().map(ReadHelper::getDataRange));
        }
        if (OntDR.OneOf.class.isInstance(dr)) {
            OntDR.OneOf _dr = (OntDR.OneOf) dr;
            return new OWLDataOneOfImpl(_dr.values().map(ReadHelper::getLiteral));
        }
        throw new OntApiException("Unsupported data range expression " + dr);
    }

    private static OWLClassExpression fetchClassExpression(OntCE ce) {
        if (ce == null)
            return new OWLClassImpl(OWLRDFVocabulary.OWL_THING.getIRI());
        return getClassExpression(ce);
    }

    /**
     * todo:
     * @param ce {@link OntCE}
     * @param df {@link OWLDataFactory}
     * @return {@link Wrap} around {@link OWLClassExpression}
     */
    public static Wrap<? extends OWLClassExpression> _getClassExpression(OntCE ce, OWLDataFactory df) {
        return _getClassExpression(ce, df, new HashSet<>());
    }

    /**
     * todo:
     *
     * @param ce   {@link OntCE}
     * @param df   {@link OWLDataFactory}
     * @param seen Set of {@link Resource}
     * @return {@link Wrap}
     */
    @SuppressWarnings("unchecked")
    public static Wrap<? extends OWLClassExpression> _getClassExpression(OntCE ce, OWLDataFactory df, Set<Resource> seen) {
        if (OntApiException.notNull(ce, "Null class expression.").isAnon() && seen.contains(ce)) {
            //todo: should be configurable (throw or null)
            throw new OntApiException("Recursive loop on class expression " + ce);
        }
        seen.add(ce);
        if (ce.isURIResource()) {
            return Wrap.create(df.getOWLClass(IRI.create(ce.getURI())), ce);
        }
        Class<? extends OntObject> view = OntApiException.notNull(((OntObjectImpl) ce).getActualClass(),
                "Can't determine view of class expression " + ce);
        if (OntCE.ObjectSomeValuesFrom.class.equals(view) ||
                OntCE.ObjectAllValuesFrom.class.equals(view)) {
            OntCE.ComponentRestrictionCE<OntCE, OntOPE> _ce = (OntCE.ComponentRestrictionCE<OntCE, OntOPE>) ce;
            Wrap<OWLObjectPropertyExpression> p = _getObjectProperty(_ce.getOnProperty(), df);
            Wrap<? extends OWLClassExpression> c = _getClassExpression(_ce.getValue(), df, seen);
            OWLClassExpression res;
            if (OntCE.ObjectSomeValuesFrom.class.equals(view))
                res = df.getOWLObjectSomeValuesFrom(p.getObject(), c.getObject());
            else if (OntCE.ObjectAllValuesFrom.class.equals(view))
                res = df.getOWLObjectAllValuesFrom(p.getObject(), c.getObject());
            else
                throw new OntApiException("Should never happen");
            return Wrap.create(res, _ce).append(p).append(c);
        }
        if (OntCE.DataSomeValuesFrom.class.equals(view) ||
                OntCE.DataAllValuesFrom.class.equals(view)) {
            OntCE.ComponentRestrictionCE<OntDR, OntNDP> _ce = (OntCE.ComponentRestrictionCE<OntDR, OntNDP>) ce;
            Wrap<OWLDataProperty> p = _getDataProperty(_ce.getOnProperty(), df);
            Wrap<? extends OWLDataRange> d = _getDataRange(_ce.getValue(), df, new HashSet<>());
            OWLClassExpression res;
            if (OntCE.DataSomeValuesFrom.class.equals(view))
                res = df.getOWLDataSomeValuesFrom(p.getObject(), d.getObject());
            else if (OntCE.DataAllValuesFrom.class.equals(view))
                res = df.getOWLDataAllValuesFrom(p.getObject(), d.getObject());
            else
                throw new OntApiException("Should never happen");
            return Wrap.create(res, _ce).append(p).append(d);
        }
        if (OntCE.ObjectHasValue.class.equals(view)) {
            OntCE.ObjectHasValue _ce = (OntCE.ObjectHasValue) ce;
            Wrap<OWLObjectPropertyExpression> p = _getObjectProperty(_ce.getOnProperty(), df);
            Wrap<? extends OWLIndividual> i = _getIndividual(_ce.getValue(), df);
            return Wrap.create(df.getOWLObjectHasValue(p.getObject(), i.getObject()), _ce).append(p).append(i);
        }
        if (OntCE.DataHasValue.class.equals(view)) {
            OntCE.DataHasValue _ce = (OntCE.DataHasValue) ce;
            Wrap<OWLDataProperty> p = _getDataProperty(_ce.getOnProperty(), df);
            return Wrap.create(df.getOWLDataHasValue(p.getObject(), getLiteral(_ce.getValue())), _ce).append(p);
        }
        if (OntCE.ObjectMinCardinality.class.equals(view) ||
                OntCE.ObjectMaxCardinality.class.equals(view) ||
                OntCE.ObjectCardinality.class.equals(view)) {
            OntCE.CardinalityRestrictionCE<OntCE, OntOPE> _ce = (OntCE.CardinalityRestrictionCE<OntCE, OntOPE>) ce;
            Wrap<OWLObjectPropertyExpression> p = _getObjectProperty(_ce.getOnProperty(), df);
            Wrap<? extends OWLClassExpression> c = _getClassExpression(_ce.getValue() == null ? _ce.getModel().getOWLThing() : _ce.getValue(), df, seen);
            OWLObjectCardinalityRestriction res;
            if (OntCE.ObjectMinCardinality.class.equals(view))
                res = df.getOWLObjectMinCardinality(_ce.getCardinality(), p.getObject(), c.getObject());
            else if (OntCE.ObjectMaxCardinality.class.equals(view))
                res = df.getOWLObjectMaxCardinality(_ce.getCardinality(), p.getObject(), c.getObject());
            else if (OntCE.ObjectCardinality.class.equals(view))
                res = df.getOWLObjectExactCardinality(_ce.getCardinality(), p.getObject(), c.getObject());
            else
                throw new OntApiException("Should never happen");
            return Wrap.create(res, _ce).append(p).append(c);
        }
        if (OntCE.DataMinCardinality.class.equals(view) ||
                OntCE.DataMaxCardinality.class.equals(view) ||
                OntCE.DataCardinality.class.equals(view)) {
            OntCE.CardinalityRestrictionCE<OntDR, OntNDP> _ce = (OntCE.CardinalityRestrictionCE<OntDR, OntNDP>) ce;
            Wrap<OWLDataProperty> p = _getDataProperty(_ce.getOnProperty(), df);
            Wrap<? extends OWLDataRange> d = _getDataRange(_ce.getValue() == null ? _ce.getModel().getRDFSLiteral() : _ce.getValue(), df, new HashSet<>());
            OWLDataCardinalityRestriction res;
            if (OntCE.DataMinCardinality.class.equals(view))
                res = df.getOWLDataMinCardinality(_ce.getCardinality(), p.getObject(), d.getObject());
            else if (OntCE.DataMaxCardinality.class.equals(view))
                res = df.getOWLDataMaxCardinality(_ce.getCardinality(), p.getObject(), d.getObject());
            else if (OntCE.DataCardinality.class.equals(view))
                res = df.getOWLDataExactCardinality(_ce.getCardinality(), p.getObject(), d.getObject());
            else
                throw new OntApiException("Should never happen");
            return Wrap.create(res, _ce).append(p).append(d);
        }
        if (OntCE.HasSelf.class.equals(view)) {
            OntCE.HasSelf _ce = (OntCE.HasSelf) ce;
            Wrap<OWLObjectPropertyExpression> p = _getObjectProperty(_ce.getOnProperty(), df);
            return Wrap.create(df.getOWLObjectHasSelf(p.getObject()), _ce).append(p);
        }
        if (OntCE.UnionOf.class.equals(view) ||
                OntCE.IntersectionOf.class.equals(view)) {
            OntCE.ComponentsCE<OntCE> _ce = (OntCE.ComponentsCE<OntCE>) ce;
            List<Wrap<? extends OWLClassExpression>> components = _ce.components()
                    .map(c -> _getClassExpression(c, df, seen)).collect(Collectors.toList());
            OWLClassExpression res;
            if (OntCE.UnionOf.class.equals(view))
                res = df.getOWLObjectUnionOf(components.stream().map(Wrap::getObject));
            else if (OntCE.IntersectionOf.class.equals(view))
                res = df.getOWLObjectIntersectionOf(components.stream().map(Wrap::getObject));
            else
                throw new OntApiException("Should never happen");
            Stream<Triple> triples = Stream.concat(_ce.content().map(FrontsTriple::asTriple),
                    components.stream().map(Wrap::triples).flatMap(Function.identity()));
            return new Wrap<>(res, triples.collect(Collectors.toSet()));
        }
        if (OntCE.OneOf.class.equals(view)) {
            OntCE.OneOf _ce = (OntCE.OneOf) ce;
            List<Wrap<? extends OWLIndividual>> components = _ce.components()
                    .map(c -> _getIndividual(c, df)).collect(Collectors.toList());
            OWLClassExpression res = df.getOWLObjectOneOf(components.stream().map(Wrap::getObject));
            Stream<Triple> triples = Stream.concat(_ce.content().map(FrontsTriple::asTriple),
                    components.stream().map(Wrap::triples).flatMap(Function.identity()));
            return new Wrap<>(res, triples.collect(Collectors.toSet()));
        }
        if (OntCE.ComplementOf.class.isInstance(ce)) {
            OntCE.ComplementOf _ce = (OntCE.ComplementOf) ce;
            Wrap<? extends OWLClassExpression> c = _getClassExpression(_ce.getValue(), df, seen);
            return Wrap.create(df.getOWLObjectComplementOf(c.getObject()), _ce).append(c);
        }
        throw new OntApiException("Unsupported class expression " + ce);
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
            return new OWLObjectUnionOfImpl(_ce.components().map(ReadHelper::getClassExpression));
        }
        if (OntCE.IntersectionOf.class.isInstance(ce)) {
            OntCE.IntersectionOf _ce = (OntCE.IntersectionOf) ce;
            return new OWLObjectIntersectionOfImpl(_ce.components().map(ReadHelper::getClassExpression));
        }
        if (OntCE.OneOf.class.isInstance(ce)) {
            OntCE.OneOf _ce = (OntCE.OneOf) ce;
            return new OWLObjectOneOfImpl(_ce.components().map(ReadHelper::getIndividual));
        }
        if (OntCE.ComplementOf.class.isInstance(ce)) {
            OntCE.ComplementOf _ce = (OntCE.ComplementOf) ce;
            return new OWLObjectComplementOfImpl(getClassExpression(_ce.getValue()));
        }
        throw new OntApiException("Unsupported class expression " + ce);
    }

    /**
     * todo:
     *
     * @param var {@link ru.avicomp.ontapi.jena.model.OntSWRL.Variable}
     * @param df  {@link OWLDataFactory}
     * @return {@link Wrap}
     */
    public static Wrap<SWRLVariable> _getSWRLVariable(OntSWRL.Variable var, OWLDataFactory df) {
        if (!OntApiException.notNull(var, "Null swrl var").isURIResource()) {
            throw new OntApiException("Anonymous swrl var " + var);
        }
        return Wrap.create(df.getSWRLVariable(IRI.create(var.getURI())), var);
    }

    public static SWRLVariable getSWRLVariable(OntSWRL.Variable var) {
        if (!OntApiException.notNull(var, "Null swrl var").isURIResource()) {
            throw new OntApiException("Anonymous swrl var " + var);
        }
        // not public access:
        return OWL_DATA_FACTORY.getSWRLVariable(IRI.create(var.getURI()));
    }

    /**
     * todo:
     *
     * @param arg {@link ru.avicomp.ontapi.jena.model.OntSWRL.DArg}
     * @param df  {@link OWLDataFactory}
     * @return {@link Wrap}
     */
    public static Wrap<? extends SWRLDArgument> _getSWRLLiteralArg(OntSWRL.DArg arg, OWLDataFactory df) {
        if (OntApiException.notNull(arg, "Null SWRL-D arg").isLiteral()) {
            return Wrap.create(df.getSWRLLiteralArgument(getLiteral(arg.asLiteral())), arg);
        }
        if (arg.canAs(OntSWRL.Variable.class)) {
            return _getSWRLVariable(arg.as(OntSWRL.Variable.class), df);
        }
        throw new OntApiException("Unsupported SWRL-D arg " + arg);
    }

    public static SWRLDArgument getSWRLiteralDArg(OntSWRL.DArg arg) {
        if (OntApiException.notNull(arg, "Null SWRL-D arg").isLiteral()) {
            return new SWRLLiteralArgumentImpl(getLiteral(arg.asLiteral()));
        }
        if (arg.canAs(OntSWRL.Variable.class)) {
            return getSWRLVariable(arg.as(OntSWRL.Variable.class));
        }
        throw new OntApiException("Unsupported SWRL-D arg " + arg);
    }

    /**
     * todo:
     *
     * @param arg {@link ru.avicomp.ontapi.jena.model.OntSWRL.IArg}
     * @param df  {@link OWLDataFactory}
     * @return {@link Wrap}
     */
    public static Wrap<? extends SWRLIArgument> _getSWRLIndividualArg(OntSWRL.IArg arg, OWLDataFactory df) {
        if (OntApiException.notNull(arg, "Null SWRL-I arg").canAs(OntIndividual.class)) {
            return Wrap.create(df.getSWRLIndividualArgument(getIndividual(arg.as(OntIndividual.class))), arg);
        }
        if (arg.canAs(OntSWRL.Variable.class)) {
            return _getSWRLVariable(arg.as(OntSWRL.Variable.class), df);
        }
        throw new OntApiException("Unsupported SWRL-I arg " + arg);
    }

    public static SWRLIArgument getSWRLIndividualArg(OntSWRL.IArg arg) {
        if (OntApiException.notNull(arg, "Null SWRL-I arg").canAs(OntIndividual.class)) {
            return new SWRLIndividualArgumentImpl(getIndividual(arg.as(OntIndividual.class)));
        }
        if (arg.canAs(OntSWRL.Variable.class)) {
            return getSWRLVariable(arg.as(OntSWRL.Variable.class));
        }
        throw new OntApiException("Unsupported SWRL-I arg " + arg);
    }

    /**
     * todo:
     *
     * @param atom {@link ru.avicomp.ontapi.jena.model.OntSWRL.Atom}
     * @param df   {@link OWLDataFactory}
     * @return {@link Wrap}
     */
    @SuppressWarnings("unchecked")
    public static Wrap<? extends SWRLAtom> _getSWRLAtom(OntSWRL.Atom atom, OWLDataFactory df) {
        Class<? extends OntObject> view = OntApiException.notNull(((OntObjectImpl) OntApiException.notNull(atom, "Null SWRL atom.")).getActualClass(),
                "Can't determine view of SWRL atom " + atom);
        if (OntSWRL.Atom.BuiltIn.class.equals(view)) {
            OntSWRL.Atom.BuiltIn _atom = (OntSWRL.Atom.BuiltIn) atom;
            IRI iri = IRI.create(_atom.getPredicate().getURI());
            List<Wrap<? extends SWRLDArgument>> arguments = _atom.arguments().map(a -> _getSWRLLiteralArg(a, df)).collect(Collectors.toList());
            SWRLAtom res = df.getSWRLBuiltInAtom(iri, arguments.stream().map(Wrap::getObject).collect(Collectors.toList()));
            Stream<Triple> triples = Stream.concat(_atom.content().map(FrontsTriple::asTriple),
                    arguments.stream().map(Wrap::triples).flatMap(Function.identity()));
            return new Wrap<>(res, triples.collect(Collectors.toSet()));
        }
        if (OntSWRL.Atom.OntClass.class.equals(view)) {
            OntSWRL.Atom.OntClass _atom = (OntSWRL.Atom.OntClass) atom;
            Wrap<? extends OWLClassExpression> c = _getClassExpression(_atom.getPredicate(), df, new HashSet<>());
            Wrap<? extends SWRLIArgument> a = _getSWRLIndividualArg(_atom.getArg(), df);
            return Wrap.create(df.getSWRLClassAtom(c.getObject(), a.getObject()), _atom).append(c).append(a);
        }
        if (OntSWRL.Atom.DataProperty.class.equals(view)) {
            OntSWRL.Atom.DataProperty _atom = (OntSWRL.Atom.DataProperty) atom;
            Wrap<OWLDataProperty> p = _getDataProperty(_atom.getPredicate(), df);
            Wrap<? extends SWRLIArgument> f = _getSWRLIndividualArg(_atom.getFirstArg(), df);
            Wrap<? extends SWRLDArgument> s = _getSWRLLiteralArg(_atom.getSecondArg(), df);
            return Wrap.create(df.getSWRLDataPropertyAtom(p.getObject(), f.getObject(), s.getObject()), _atom).append(p).append(f).append(s);
        }
        if (OntSWRL.Atom.ObjectProperty.class.equals(view)) {
            OntSWRL.Atom.ObjectProperty _atom = (OntSWRL.Atom.ObjectProperty) atom;
            Wrap<OWLObjectPropertyExpression> p = _getObjectProperty(_atom.getPredicate(), df);
            Wrap<? extends SWRLIArgument> f = _getSWRLIndividualArg(_atom.getFirstArg(), df);
            Wrap<? extends SWRLIArgument> s = _getSWRLIndividualArg(_atom.getSecondArg(), df);
            return Wrap.create(df.getSWRLObjectPropertyAtom(p.getObject(), f.getObject(), s.getObject()), _atom).append(p).append(f).append(s);
        }
        if (OntSWRL.Atom.DataRange.class.equals(view)) {
            OntSWRL.Atom.DataRange _atom = (OntSWRL.Atom.DataRange) atom;
            Wrap<? extends OWLDataRange> d = _getDataRange(_atom.getPredicate(), df, new HashSet<>());
            Wrap<? extends SWRLDArgument> a = _getSWRLLiteralArg(_atom.getArg(), df);
            return Wrap.create(df.getSWRLDataRangeAtom(d.getObject(), a.getObject()), _atom).append(d).append(a);
        }
        if (OntSWRL.Atom.DifferentIndividuals.class.equals(view)) {
            OntSWRL.Atom.DifferentIndividuals _atom = (OntSWRL.Atom.DifferentIndividuals) atom;
            Wrap<? extends SWRLIArgument> f = _getSWRLIndividualArg(_atom.getFirstArg(), df);
            Wrap<? extends SWRLIArgument> s = _getSWRLIndividualArg(_atom.getSecondArg(), df);
            return Wrap.create(df.getSWRLDifferentIndividualsAtom(f.getObject(), s.getObject()), _atom).append(f).append(s);
        }
        if (OntSWRL.Atom.SameIndividuals.class.equals(view)) {
            OntSWRL.Atom.SameIndividuals _atom = (OntSWRL.Atom.SameIndividuals) atom;
            Wrap<? extends SWRLIArgument> f = _getSWRLIndividualArg(_atom.getFirstArg(), df);
            Wrap<? extends SWRLIArgument> s = _getSWRLIndividualArg(_atom.getSecondArg(), df);
            return Wrap.create(df.getSWRLSameIndividualAtom(f.getObject(), s.getObject()), _atom).append(f).append(s);
        }
        throw new OntApiException("Unsupported SWRL atom " + atom);
    }

    public static SWRLAtom getSWRLAtom(OntSWRL.Atom atom) {
        OntApiException.notNull(atom, "Null SWRL atom.");
        if (OntSWRL.Atom.BuiltIn.class.isInstance(atom)) {
            OntSWRL.Atom.BuiltIn a = (OntSWRL.Atom.BuiltIn) atom;
            IRI i = IRI.create(a.getPredicate().getURI());
            return new SWRLBuiltInAtomImpl(i, a.arguments().map(ReadHelper::getSWRLiteralDArg).collect(Collectors.toList()));
        }
        if (OntSWRL.Atom.OntClass.class.isInstance(atom)) {
            OntSWRL.Atom.OntClass a = (OntSWRL.Atom.OntClass) atom;
            return new SWRLClassAtomImpl(getClassExpression(a.getPredicate()), getSWRLIndividualArg(a.getArg()));
        }
        if (OntSWRL.Atom.DataProperty.class.isInstance(atom)) {
            OntSWRL.Atom.DataProperty a = (OntSWRL.Atom.DataProperty) atom;
            return new SWRLDataPropertyAtomImpl(getDataProperty(a.getPredicate()), getSWRLIndividualArg(a.getFirstArg()), getSWRLiteralDArg(a.getSecondArg()));
        }
        if (OntSWRL.Atom.ObjectProperty.class.isInstance(atom)) {
            OntSWRL.Atom.ObjectProperty a = (OntSWRL.Atom.ObjectProperty) atom;
            return new SWRLObjectPropertyAtomImpl(getObjectProperty(a.getPredicate()), getSWRLIndividualArg(a.getFirstArg()), getSWRLIndividualArg(a.getSecondArg()));
        }
        if (OntSWRL.Atom.DataRange.class.isInstance(atom)) {
            OntSWRL.Atom.DataRange a = (OntSWRL.Atom.DataRange) atom;
            return new SWRLDataRangeAtomImpl(getDataRange(a.getPredicate()), getSWRLiteralDArg(a.getArg()));
        }
        if (OntSWRL.Atom.DifferentIndividuals.class.isInstance(atom)) {
            OntSWRL.Atom.DifferentIndividuals a = (OntSWRL.Atom.DifferentIndividuals) atom;
            OWLObjectProperty property = new OWLObjectPropertyImpl(IRI.create(OWL.differentFrom.getURI())); // it is not true object property.
            return new SWRLDifferentIndividualsAtomImpl(property, getSWRLIndividualArg(a.getFirstArg()), getSWRLIndividualArg(a.getSecondArg()));
        }
        if (OntSWRL.Atom.SameIndividuals.class.isInstance(atom)) {
            OntSWRL.Atom.SameIndividuals a = (OntSWRL.Atom.SameIndividuals) atom;
            OWLObjectProperty property = new OWLObjectPropertyImpl(IRI.create(OWL.sameAs.getURI())); // it is not true object property.
            return new SWRLSameIndividualAtomImpl(property, getSWRLIndividualArg(a.getFirstArg()), getSWRLIndividualArg(a.getSecondArg()));
        }
        throw new OntApiException("Unsupported SWRL atom " + atom);
    }

    /**
     * answers true if two nary axioms intersect, i.e. they have the same annotations and some components are included in both axioms.
     *
     * @param left  OWLNaryAxiom left axiom
     * @param right OWLNaryAxiom right axiom
     * @return true if axioms intersect.
     */
    public static boolean isIntersect(OWLNaryAxiom left, OWLNaryAxiom right) {
        if (!OWLAPIStreamUtils.equalStreams(left.annotations(), right.annotations())) return false;
        Set set1 = ((Stream<?>) left.operands()).collect(Collectors.toSet());
        Set set2 = ((Stream<?>) right.operands()).collect(Collectors.toSet());
        return !Collections.disjoint(set1, set2);
    }

    /**
     * A helper object, which helps
     * to find all (owl-)annotations and triples related to the specified statement.
     * todo: it seems it is wrong - the set should contain main triple, declaration triple and annotations triples.
     */
    @Deprecated
    public static class AxiomStatement {
        private final OntStatement statement;
        private final Set<Triple> triples;
        private final Set<OWLAnnotation> annotations;

        public AxiomStatement(OntStatement main) {
            this.statement = OntApiException.notNull(main, "Null statement.");
            this.triples = new HashSet<>();
            this.annotations = new HashSet<>();
            triples.add(main.asTriple());
            Resource subject = main.getSubject();
            RDFNode object = main.getObject();
            Stream<Statement> associated;

            if (subject.isAnon()
                    // todo: seems this place degrades the performance. need to change whole mechanism (separate to each translator)
                    && !subject.canAs(OntIndividual.Anonymous.class)) { // for anonymous axioms (e.g. disjoint all)
                associated = Models.getAssociatedStatements(subject).stream();
            } else if (object.isAnon()) { // e.g. anon class expression in statement subClassOf
                associated = Models.getAssociatedStatements(object.asResource()).stream();
            } else {
                associated = Stream.empty();
            }
            associated.map(Statement::asTriple).forEach(triples::add);
            ReadHelper.getAnnotations(main).forEach(a -> {
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
