package ru.avicomp.ontapi;

import javax.annotation.Nonnull;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.stream.Stream;

import org.apache.commons.io.input.ReaderInputStream;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RiotException;
import org.apache.log4j.Logger;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.io.OWLOntologyDocumentSource;
import org.semanticweb.owlapi.model.*;

import ru.avicomp.ontapi.jena.GraphConverter;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import uk.ac.manchester.cs.owl.owlapi.OWLOntologyFactoryImpl;

/**
 * Ontology building factory.
 * See base class {@link OWLOntologyFactory}.
 * <p>
 * Created by szuev on 24.10.2016.
 */
public class OntBuildingFactoryImpl extends OWLOntologyFactoryImpl implements OWLOntologyFactory {
    private static final Logger LOGGER = Logger.getLogger(OntBuildingFactoryImpl.class);

    public OntBuildingFactoryImpl() {
        super(new Builder());
    }

    @Override
    public OntologyModel createOWLOntology(@Nonnull OWLOntologyManager manager,
                                           @Nonnull OWLOntologyID ontologyID,
                                           @Nonnull IRI documentIRI,
                                           @Nonnull OWLOntologyCreationHandler handler) {
        OntologyManagerImpl m = (OntologyManagerImpl) manager;
        OntologyModelImpl ont = new OntologyModelImpl(m, ontologyID);
        OntologyModel res = m.isConcurrent() ? ont.toConcurrentModel() : ont;
        handler.ontologyCreated(res);
        handler.setOntologyFormat(res, new RDFXMLDocumentFormat());
        return res;
    }

    @Override
    public OntologyModel loadOWLOntology(@Nonnull OWLOntologyManager manager,
                                         @Nonnull OWLOntologyDocumentSource source,
                                         @Nonnull OWLOntologyCreationHandler handler,
                                         @Nonnull OWLOntologyLoaderConfiguration configuration) throws OWLOntologyCreationException {
        OntologyManagerImpl m = (OntologyManagerImpl) manager;
        Graph graph = m.getGraphFactory().create();
        OntFormat format;
        try {
            Lang lang = loadGraph(graph, source);
            format = OntApiException.notNull(OntFormat.get(lang), "Can't determine language.");
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Format (" + source.getClass().getSimpleName() + "): " + format);
            }
        } catch (OntApiException e) { // maybe it is not jena format; try origin OWL-API method:
            LOGGER.warn("Can't load from " + source + " ::: " + e);
            OntologyModel res = (OntologyModel) super.loadOWLOntology(manager, source, handler, configuration);
            // clear cache to be sure that list of axioms is always the same and corresponds to the graph.
            res.clearCache();
            return res;
        }
        // todo: resolve possible cycle
        UnionGraph union = new UnionGraph(graph);
        graph.find(Node.ANY, OWL.imports.asNode(), Node.ANY)
                .mapWith(Triple::getObject)
                .filterKeep(Node::isURI)
                .mapWith(Node::getURI)
                .mapWith(IRI::create)
                .mapWith(iri -> fetchOntology(m, iri))
                .filterKeep(Objects::nonNull)
                .mapWith(OntologyModel::asGraphModel)
                .mapWith(OntGraphModel::getGraph)
                .forEachRemaining(union::addGraph);

        return create(union, m, format);
    }

    private OntologyModel create(UnionGraph graph, OntologyManagerImpl manager, OntFormat format) {
        OntInternalModel base = new OntInternalModel(GraphConverter.convert(graph));
        OntologyModelImpl ont = new OntologyModelImpl(manager, base);
        OntologyModel res = manager.isConcurrent() ? ont.toConcurrentModel() : ont;
        manager.ontologyCreated(res);
        OWLDocumentFormat owlFormat = format.createOwlFormat();
        if (owlFormat == null) return res;
        if (PrefixManager.class.isInstance(owlFormat)) {
            PrefixManager pm = (PrefixManager) owlFormat;
            graph.getPrefixMapping().getNsPrefixMap().entrySet().forEach(e -> pm.setPrefix(e.getKey(), e.getValue()));
            OntologyManagerImpl.setDefaultPrefix(pm, ont);
        }
        manager.setOntologyFormat(res, owlFormat);
        return res;
    }

    private OntologyModel fetchOntology(OntologyManagerImpl manager, IRI iri) {
        if (manager.contains(iri)) return manager.getOntology(iri);
        try {
            return manager.loadOntology(iri);
        } catch (OWLOntologyCreationException e) {
            LOGGER.warn(e);
        }
        return null;
    }

    public static Lang loadGraph(Graph g, OWLOntologyDocumentSource source) throws OntApiException {
        IRI iri = OntApiException.notNull(source, "Null OWLOntologyDocumentSource.").getDocumentIRI();
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("Load ONT Model from " + iri);
        if (source.getInputStream().isPresent()) {
            return readFromStream(g, source);
        }
        if (source.getReader().isPresent()) {
            return readFromReader(g, source);
        }
        return readFromDocument(g, source);
    }

    private static Lang readFromDocument(Graph graph, OWLOntologyDocumentSource source) {
        Lang lang = source.getFormat().map(OntFormat::get).map(OntFormat::getLang).orElse(guessLang(source));
        String uri = source.getDocumentIRI().getIRIString();
        try {
            RDFDataMgr.read(graph, uri, lang);
            return lang;
        } catch (RiotException e) {
            throw new OntApiException("Can't read lang=" + lang + " from iri <" + uri + ">", e);
        }
    }

    private static Lang readFromStream(Graph graph, OWLOntologyDocumentSource source) {
        if (!source.getInputStream().isPresent()) {
            throw new OntApiException("No input stream inside " + source);
        }
        return formats(source)
                .filter(OntFormat::isJena)
                .map(OntFormat::getLang)
                .filter(lang -> read(source.getInputStream().get(), graph, lang))
                .findFirst().orElseThrow(() -> new OntApiException("Can't read from stream (source=" + source + ")."));
    }

    private static Lang readFromReader(Graph graph, OWLOntologyDocumentSource source) {
        if (!source.getReader().isPresent()) {
            throw new OntApiException("No reader inside " + source);
        }
        final Charset en = StandardCharsets.UTF_8;
        return formats(source)
                .filter(OntFormat::isJena)
                .map(OntFormat::getLang)
                .filter(lang -> read(new ReaderInputStream(source.getReader().get(), en), graph, lang))
                .findFirst().orElseThrow(() -> new OntApiException("Can't read from reader (source=" + source + ")."));
    }

    private static Stream<OntFormat> formats(OWLOntologyDocumentSource source) {
        return source.getFormat().map(OntFormat::get).map(Stream::of).orElse(OntFormat.all());
    }

    private static boolean read(InputStream is, Graph graph, Lang lang) {
        try {
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("try <<" + lang + ">>");
            RDFDataMgr.read(graph, is, lang);
            return true;
        } catch (Exception e) {
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("Can't read <" + lang + ">::" + e.getMessage());
            return false;
        }
    }

    public static Lang guessLang(OWLOntologyDocumentSource source) {
        if (OntApiException.notNull(source, "Null OWLOntologyDocumentSource.").getMIMEType().isPresent()) {
            return RDFLanguages.contentTypeToLang(source.getMIMEType().get());
        }
        return RDFLanguages.filenameToLang(source.getDocumentIRI().getIRIString());
    }

    public static class Builder implements OWLOntologyBuilder {
        @Override
        public OWLOntology createOWLOntology(@Nonnull OWLOntologyManager manager, @Nonnull OWLOntologyID ontologyID) {
            return new OntologyModelImpl((OntologyManager) manager, ontologyID);
        }
    }

}
