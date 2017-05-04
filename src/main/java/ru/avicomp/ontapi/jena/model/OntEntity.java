package ru.avicomp.ontapi.jena.model;

/**
 * OWL-Entity here.
 * It is always uri-resource
 * <p>
 * Created by szuev on 01.11.2016.
 * @see OntClass
 * @see OntDT
 * @see OntIndividual.Named
 * @see OntNAP
 * @see OntNOP
 * @see OntNDP
 */
public interface OntEntity extends OntObject {

    /**
     * Determines if this entity is a builtin entity. The entity is a builtin if it is:
     * - a class and the IRI is either owl:Thing or owl:Nothing
     * - an object property and the IRI corresponds to owl:topObjectProperty or owl:bottomObjectProperty
     * - a data property and the IRI corresponds to owl:topDataProperty or owl:bottomDataProperty
     * - a datatype and the IRI is rdfs:Literal or is in the OWL 2 datatype map or is rdf:PlainLiteral
     * - an annotation property and the IRI is one of the following:
     * rdfs:label, rdfs:comment, rdfs:seeAlso, rdfs:isDefinedBy, owl:deprecated, owl:versionInfo, owl:priorVersion, owl:backwardCompatibleWith, owl:incompatibleWith
     *
     * @return true if it is a built-in entity
     */
    boolean isBuiltIn();

}
