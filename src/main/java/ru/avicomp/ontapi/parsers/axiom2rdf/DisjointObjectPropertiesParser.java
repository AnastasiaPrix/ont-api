package ru.avicomp.ontapi.parsers.axiom2rdf;

import org.semanticweb.owlapi.model.OWLDisjointObjectPropertiesAxiom;

/**
 * see {@link AbstractTwoWayNaryParser}, {@link AbstractDisjointPropertiesParser}
 * examples:
 * :dataProperty1 owl:propertyDisjointWith :dataProperty2
 * [ rdf:type owl:AllDisjointProperties; owl:members ( :dataProperty1 :dataProperty2 :dataProperty3 ) ]
 * <p>
 * Created by szuev on 12.10.2016.
 */
class DisjointObjectPropertiesParser extends AbstractDisjointPropertiesParser<OWLDisjointObjectPropertiesAxiom> {
}
