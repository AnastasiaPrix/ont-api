package ru.avicomp.ontapi.jena.impl.configuration;

import org.apache.jena.enhanced.Personality;
import org.apache.jena.rdf.model.*;
import org.apache.jena.rdf.model.impl.*;

import ru.avicomp.ontapi.jena.impl.*;
import ru.avicomp.ontapi.jena.model.*;

/**
 * personalities here.
 * <p>
 * Created by @szuev on 04.11.2016.
 */
public class OntModelConfig {

    // standard resources:
    public static final Personality<RDFNode> STANDARD_PERSONALITY = new Personality<RDFNode>()
            .add(Resource.class, ResourceImpl.factory)
            .add(Property.class, PropertyImpl.factory)
            .add(Literal.class, LiteralImpl.factory)
            .add(Container.class, ResourceImpl.factory)
            .add(Alt.class, AltImpl.factory)
            .add(Bag.class, BagImpl.factory)
            .add(Seq.class, SeqImpl.factory)
            .add(ReifiedStatement.class, ReifiedStatementImpl.reifiedStatementFactory)
            .add(RDFList.class, RDFListImpl.factory)
            .add(RDFNode.class, ResourceImpl.rdfNodeFactory);

    // ont-resources:
    public static final OntPersonality ONT_PERSONALITY = new OntPersonality(STANDARD_PERSONALITY)
            // ont-id
            .register(OntID.class, OntIDImpl.idFactory)

            // entities:
            .register(OntObject.class, OntObjectImpl.objectFactory)
            .register(OntClass.class, OntEntityImpl.classFactory)
            .register(OntNAP.class, OntEntityImpl.annotationPropertyFactory)
            .register(OntNDP.class, OntEntityImpl.dataPropertyFactory)
            .register(OntNOP.class, OntEntityImpl.objectPropertyFactory)
            .register(OntDT.class, OntEntityImpl.datatypeFactory)
            .register(OntIndividual.Named.class, OntEntityImpl.individualFactory)
            .register(OntEntity.class, OntEntityImpl.abstractEntityFactory)

            // class expressions:
            .register(OntCE.ObjectSomeValuesFrom.class, OntCEImpl.objectSomeValuesOfCEFactory)
            .register(OntCE.DataSomeValuesFrom.class, OntCEImpl.dataSomeValuesOfCEFactory)
            .register(OntCE.ObjectAllValuesFrom.class, OntCEImpl.objectAllValuesOfCEFactory)
            .register(OntCE.DataAllValuesFrom.class, OntCEImpl.dataAllValuesOfCEFactory)
            .register(OntCE.ObjectHasValue.class, OntCEImpl.objectHasValueCEFactory)
            .register(OntCE.DataHasValue.class, OntCEImpl.dataHasValueCEFactory)
            .register(OntCE.ObjectMinCardinality.class, OntCEImpl.objectMinCardinalityCEFactory)
            .register(OntCE.DataMinCardinality.class, OntCEImpl.dataMinCardinalityCEFactory)
            .register(OntCE.ObjectMaxCardinality.class, OntCEImpl.objectMaxCardinalityCEFactory)
            .register(OntCE.DataMaxCardinality.class, OntCEImpl.dataMaxCardinalityCEFactory)
            .register(OntCE.ObjectCardinality.class, OntCEImpl.objectCardinalityCEFactory)
            .register(OntCE.DataCardinality.class, OntCEImpl.dataCardinalityCEFactory)
            .register(OntCE.HasSelf.class, OntCEImpl.hasSelfCEFactory)
            .register(OntCE.UnionOf.class, OntCEImpl.unionOfCEFactory)
            .register(OntCE.OneOf.class, OntCEImpl.oneOfCEFactory)
            .register(OntCE.IntersectionOf.class, OntCEImpl.intersectionOfCEFactory)
            .register(OntCE.ComplementOf.class, OntCEImpl.complementOfCEFactory)
            .register(OntCE.class, OntCEImpl.abstractCEFactory)
            .register(OntCE.ComponentsCE.class, OntCEImpl.abstractComponentsCEFactory)
            .register(OntCE.CardinalityRestrictionCE.class, OntCEImpl.abstractCardinalityRestrictionCEFactory)
            .register(OntCE.ComponentRestrictionCE.class, OntCEImpl.abstractComponentRestrictionCEFactory)
            .register(OntCE.RestrictionCE.class, OntCEImpl.abstractRestrictionCEFactory) //todo: add nary CEs

            // property expressions:
            .register(OntOPE.Inverse.class, OntPEImpl.inversePropertyFactory)
            .register(OntOPE.class, OntPEImpl.abstractOPEFactory)
            .register(OntPE.class, OntPEImpl.abstractPEFactory)

            // individuals
            .register(OntIndividual.Anonymous.class, OntIndividualImpl.anonymousIndividualFactory)
            .register(OntIndividual.class, OntIndividualImpl.abstractIndividualFactory);

}
