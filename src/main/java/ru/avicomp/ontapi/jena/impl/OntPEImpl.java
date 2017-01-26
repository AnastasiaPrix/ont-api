package ru.avicomp.ontapi.jena.impl;

import org.apache.jena.enhanced.EnhGraph;
import org.apache.jena.graph.Node;

import ru.avicomp.ontapi.jena.impl.configuration.*;
import ru.avicomp.ontapi.jena.vocabulary.OWL;

/**
 * Property Expression base class.
 * <p>
 * Created by @szuev on 08.11.2016.
 */
public abstract class OntPEImpl extends OntObjectImpl {

    public static Configurable<MultiOntObjectFactory> abstractNamedPropertyFactory = Configurable.create(OntFinder.TYPED,
            OntEntityImpl.objectPropertyFactory, OntEntityImpl.dataPropertyFactory, OntEntityImpl.annotationPropertyFactory);
    public static Configurable<OntObjectFactory> inversePropertyFactory = m -> new CommonOntObjectFactory(
            new OntMaker.Default(OntOPEImpl.InversePropertyImpl.class),
            new OntFinder.ByPredicate(OWL.inverseOf),
            OntOPEImpl.InversePropertyImpl.FILTER.get(m));

    public static Configurable<MultiOntObjectFactory> abstractOPEFactory = Configurable.create(OntFinder.TYPED,
            OntEntityImpl.objectPropertyFactory, inversePropertyFactory);
    public static Configurable<MultiOntObjectFactory> abstractPEFactory =
            Configurable.create(OntFinder.TYPED, abstractNamedPropertyFactory, inversePropertyFactory);

    public OntPEImpl(Node n, EnhGraph m) {
        super(n, m);
    }
}
