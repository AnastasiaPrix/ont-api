package ru.avicomp.ontapi.parsers;

import org.semanticweb.owlapi.model.OWLDisjointDataPropertiesAxiom;

/**
 * see {@link AbstractNaryParser}, {@link AbstractDisjointPropertiesParser}
 * examples:
 * :objProperty1 owl:propertyDisjointWith :objProperty2
 * [ rdf:type owl:AllDisjointProperties; owl:members ( :objProperty1 :objProperty2 :objProperty3 ) ]
 * <p>
 * Created by szuev on 12.10.2016.
 */
class DisjointDataPropertiesParser extends AbstractDisjointPropertiesParser<OWLDisjointDataPropertiesAxiom> {
}
