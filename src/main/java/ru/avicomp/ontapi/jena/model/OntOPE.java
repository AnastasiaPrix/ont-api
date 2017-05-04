package ru.avicomp.ontapi.jena.model;

import java.util.Collection;
import java.util.stream.Stream;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;

import ru.avicomp.ontapi.jena.vocabulary.OWL;

/**
 * Object Property Expression (i.e. for iri-object property entity and for inverseOf anonymous property expression)
 * <p>
 * Created by @szuev on 08.11.2016.
 */
public interface OntOPE extends OntPE {

    /**
     * Adds negative property assertion object.
     *
     * @param source {@link OntIndividual}
     * @param target {@link OntIndividual}
     * @return {@link OntNPA.ObjectAssertion}
     * @see OntNDP#addNegativeAssertion(OntIndividual, Literal)
     */
    OntNPA.ObjectAssertion addNegativeAssertion(OntIndividual source, OntIndividual target);

    /**
     * Returns all members the right part of statement 'P owl:propertyChainAxiom (P1 ... Pn)'
     * Note(1): in the result there could be repetitions.
     * Example: SubObjectPropertyOf( ObjectPropertyChain( :hasParent :hasParent ) :hasGrandparent )
     * Note(2): there could be several chains, it gets first,
     * there is also {@link #propertyChains()} method for such cases.
     *
     * @return Stream of {@link OntOPE}s.
     */
    Stream<OntOPE> superPropertyOf();

    /**
     * Returns all sub-property-of chains.
     *
     * @return Stream of {@link RDFList}s
     */
    Stream<RDFList> propertyChains();

    /**
     * Adds new sub-property-of chain.
     *
     * @param chain Collection of {@link OntOPE}s
     * @return the {@link OntStatement} ('_:this owl:propertyChainAxiom ( ... )')
     */
    OntStatement addSuperPropertyOf(Collection<OntOPE> chain);

    /**
     * Removes all statements with predicate owl:propertyChainAxiom ('_:this owl:propertyChainAxiom ( ... )')
     */
    void removeSuperPropertyOf();

    /**
     * Anonymous triple "_:x owl:inverseOf PN" which is also object property expression,
     */
    interface Inverse extends OntOPE {
        OntOPE getDirect();
    }

    /**
     * Gets the object property from the right part of statement "_:x owl:inverseOf PN" or "P1 owl:inverseOf P2".
     *
     * @return {@link OntOPE} or null.
     */
    OntOPE getInverseOf();

    /**
     * Returns all associated negative object property assertions
     *
     * @return Stream of {@link OntNPA.ObjectAssertion}s.
     * @see OntNDP#negativeAssertions()
     */
    default Stream<OntNPA.ObjectAssertion> negativeAssertions() {
        return getModel().ontObjects(OntNPA.ObjectAssertion.class).filter(a -> OntOPE.this.equals(a.getProperty()));
    }

    /**
     * Returns all associated negative object property assertions for the specified source individual.
     *
     * @param source {@link OntIndividual}
     * @return Stream of {@link OntNPA.ObjectAssertion}s.
     * @see OntNDP#negativeAssertions(OntIndividual)
     */

    default Stream<OntNPA.ObjectAssertion> negativeAssertions(OntIndividual source) {
        return negativeAssertions()
                .filter(a -> a.getSource().equals(source));
    }

    /**
     * Returns all domain class expressions (statement "P rdfs:domain C").
     *
     * @return Stream of {@link OntCE}s.
     */
    @Override
    default Stream<OntCE> domain() {
        return objects(RDFS.domain, OntCE.class);
    }

    /**
     * Adds domain statement
     *
     * @param domain {@link OntCE}
     * @return {@link OntStatement}
     */
    default OntStatement addDomain(OntCE domain) {
        return addStatement(RDFS.domain, domain);
    }

    /**
     * Returns all ranges (statement pattern: "P rdfs:range C")
     *
     * @return Stream of {@link OntCE}s
     */
    @Override
    default Stream<OntCE> range() {
        return objects(RDFS.range, OntCE.class);
    }

    /**
     * Adds range statement
     *
     * @param range {@link OntCE}
     * @return {@link OntStatement}
     */
    default OntStatement addRange(OntCE range) {
        return addStatement(RDFS.range, range);
    }

    /**
     * Returns all super properties, the pattern is "P1 rdfs:subPropertyOf P2"
     *
     * @return Stream of {@link OntOPE}s
     * @see #addSubPropertyOf(OntOPE)
     * @see OntPE#removeSubPropertyOf(Resource)
     */
    @Override
    default Stream<OntOPE> subPropertyOf() {
        return objects(RDFS.subPropertyOf, OntOPE.class);
    }

    /**
     * Adds super property
     *
     * @param superProperty {@link OntOPE}
     * @return {@link OntStatement}
     */
    default OntStatement addSubPropertyOf(OntOPE superProperty) {
        return addStatement(RDFS.subPropertyOf, superProperty);
    }

    /**
     * Returns disjoint properties (statement: "P1 owl:propertyDisjointWith P2").
     *
     * @return Stream of {@link OntOPE}s
     * @see OntNDP#disjointWith()
     * @see OntDisjoint.ObjectProperties
     */
    default Stream<OntOPE> disjointWith() {
        return objects(OWL.propertyDisjointWith, OntOPE.class);
    }

    /**
     * Adds disjoint object property.
     *
     * @param other {@link OntOPE}
     * @return {@link OntStatement}
     * @see OntNDP#addDisjointWith(OntNDP)
     * @see OntDisjoint.ObjectProperties
     */
    default OntStatement addDisjointWith(OntOPE other) {
        return addStatement(OWL.propertyDisjointWith, other);
    }

    /**
     * Clears all "P1 owl:propertyDisjointWith P2" statements for the specified object property.
     *
     * @param other {@link OntOPE}
     * @see OntNDP#removeDisjointWith(OntNDP)
     * @see OntDisjoint.ObjectProperties
     */
    default void removeDisjointWith(OntOPE other) {
        remove(OWL.propertyDisjointWith, other);
    }

    /**
     * Returns all equivalent object properties ("Pi owl:equivalentProperty Pj")
     *
     * @return Stream of {@link OntOPE}s.
     * @see OntNDP#equivalentProperty()
     */
    default Stream<OntOPE> equivalentProperty() {
        return objects(OWL.equivalentProperty, OntOPE.class);
    }

    /**
     * Adds new owl:equivalentProperty statement for this property.
     *
     * @param other {@link OntOPE}
     * @return {@link OntStatement}
     * @see OntNDP#addEquivalentProperty(OntNDP)
     */
    default OntStatement addEquivalentProperty(OntOPE other) {
        return addStatement(OWL.equivalentProperty, other);
    }

    /**
     * Removes all equivalent-property statements for the specified object property.
     *
     * @param other {@link OntOPE}
     * @see OntNDP#removeEquivalentProperty(OntNDP)
     */
    default void removeEquivalentProperty(OntOPE other) {
        remove(OWL.equivalentProperty, other);
    }

    /**
     * Returns all object properties from the right part of statement "P1 owl:inverseOf P2"
     *
     * @return Stream of {@link OntOPE}s.
     */
    default Stream<OntOPE> inverseOf() {
        return objects(OWL.inverseOf, OntOPE.class);
    }

    /**
     * Adds new inverse-of statement.
     *
     * @param other {@link OntOPE}
     * @return {@link OntStatement}
     */
    default OntStatement addInverseOf(OntOPE other) {
        return addStatement(OWL.inverseOf, other);
    }

    /**
     * Removes all statements with predicate owl:inverseOf and this property as subject.
     *
     * @param other {@link OntOPE}
     */
    default void removeInverseOf(OntOPE other) {
        remove(OWL.inverseOf, other);
    }

    /**
     * To add or remove "P rdf:type owl:ReflexiveProperty" statement.
     *
     * @param reflexive true if should be reflexive
     */
    void setReflexive(boolean reflexive);

    /**
     * To add or remove "P rdf:type owl:IrreflexiveProperty" statement.
     *
     * @param irreflexive true if should be irreflexive
     */
    void setIrreflexive(boolean irreflexive);

    /**
     * To add or remove "P rdf:type owl:SymmetricProperty" statement.
     *
     * @param symmetric true if should be symmetric
     */
    void setSymmetric(boolean symmetric);

    /**
     * To add or remove "P rdf:type owl:AsymmetricProperty" statement.
     *
     * @param asymmetric true if should be asymmetric
     */
    void setAsymmetric(boolean asymmetric);

    /**
     * To add or remove "P rdf:type owl:TransitiveProperty" statement.
     *
     * @param transitive true if should be transitive
     */
    void setTransitive(boolean transitive);

    /**
     * To add or remove "P rdf:type owl:FunctionalProperty" statement.
     *
     * @param functional true if should be functional
     * @see OntNDP#setFunctional(boolean)
     */
    void setFunctional(boolean functional);

    /**
     * To add or remove "P rdf:type owl:InverseFunctionalProperty" statement.
     *
     * @param inverseFunctional true if should be inverse functional
     */
    void setInverseFunctional(boolean inverseFunctional);

    /**
     * @return true iff it is inverse functional property
     */
    default boolean isInverseFunctional() {
        return hasType(OWL.InverseFunctionalProperty);
    }

    /**
     * @return true iff it is transitive property
     */
    default boolean isTransitive() {
        return hasType(OWL.TransitiveProperty);
    }

    /**
     * @return true iff it is functional property
     * @see OntNDP#isFunctional()
     */
    default boolean isFunctional() {
        return hasType(OWL.FunctionalProperty);
    }

    /**
     * @return true iff it is symmetric property
     */
    default boolean isSymmetric() {
        return hasType(OWL.SymmetricProperty);
    }

    /**
     * @return true iff it is asymmetric property
     */
    default boolean isAsymmetric() {
        return hasType(OWL.AsymmetricProperty);
    }

    /**
     * @return true iff it is reflexive property
     */
    default boolean isReflexive() {
        return hasType(OWL.ReflexiveProperty);
    }

    /**
     * @return true iff it is irreflexive property
     */
    default boolean isIrreflexive() {
        return hasType(OWL.IrreflexiveProperty);
    }
}
