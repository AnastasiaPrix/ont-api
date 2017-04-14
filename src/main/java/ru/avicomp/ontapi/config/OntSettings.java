package ru.avicomp.ontapi.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.semanticweb.owlapi.model.MissingImportHandlingStrategy;
import org.semanticweb.owlapi.model.MissingOntologyHeaderStrategy;
import org.semanticweb.owlapi.model.PriorityCollectionSorting;
import org.semanticweb.owlapi.model.parameters.ConfigurationOptions;

import ru.avicomp.ontapi.OntApiException;
import ru.avicomp.ontapi.jena.impl.configuration.Configurable;
import ru.avicomp.ontapi.transforms.DeclarationTransform;
import ru.avicomp.ontapi.transforms.OWLTransform;
import ru.avicomp.ontapi.transforms.RDFSTransform;

/**
 * Enum of all ONT-API settings (20 origin OWL-API options + 9 new ONT-API options + ignored imports)
 * Created by @szuev on 14.04.2017.
 *
 * @see ConfigurationOptions
 */
public enum OntSettings implements OntConfig.OptionSetting {
    OWL_API_LOAD_CONF_IGNORED_IMPORTS(new ArrayList<String>()),

    ONT_API_LOAD_CONF_TRANSFORMERS(Stream.of(RDFSTransform.class, OWLTransform.class, DeclarationTransform.class)
            .collect(Collectors.toCollection(ArrayList::new))),
    ONT_API_LOAD_CONF_SUPPORTED_SCHEMES(Stream.of(OntConfig.DefaultScheme.values())
            .collect(Collectors.toCollection(ArrayList::new))),
    ONT_API_LOAD_CONF_PERSONALITY_MODE(Configurable.Mode.MEDIUM),
    ONT_API_LOAD_CONF_PERFORM_TRANSFORMATIONS(true),
    ONT_API_LOAD_CONF_ALLOW_BULK_ANNOTATION_ASSERTIONS(true),
    ONT_API_LOAD_CONF_ALLOW_READ_DECLARATIONS(true),
    ONT_API_LOAD_CONF_IGNORE_ANNOTATION_AXIOM_OVERLAPS(true),
    ONT_API_LOAD_CONF_USE_OWL_PARSERS_TO_LOAD(false),

    OWL_API_LOAD_CONF_ACCEPT_HTTP_COMPRESSION(true),
    OWL_API_LOAD_CONF_CONNECTION_TIMEOUT(20000),
    OWL_API_LOAD_CONF_FOLLOW_REDIRECTS(true),
    OWL_API_LOAD_CONF_LOAD_ANNOTATIONS(true),
    OWL_API_LOAD_CONF_MISSING_IMPORT_HANDLING_STRATEGY(MissingImportHandlingStrategy.THROW_EXCEPTION),
    OWL_API_LOAD_CONF_MISSING_ONTOLOGY_HEADER_STRATEGY(MissingOntologyHeaderStrategy.INCLUDE_GRAPH),
    OWL_API_LOAD_CONF_REPORT_STACK_TRACES(true),
    OWL_API_LOAD_CONF_RETRIES_TO_ATTEMPT(5),
    OWL_API_LOAD_CONF_PARSE_WITH_STRICT_CONFIGURATION(false),
    OWL_API_LOAD_CONF_TREAT_DUBLINCORE_AS_BUILTIN(true),
    OWL_API_LOAD_CONF_PRIORITY_COLLECTION_SORTING(PriorityCollectionSorting.ON_SET_INJECTION_ONLY),
    OWL_API_LOAD_CONF_BANNED_PARSERS(""),
    OWL_API_LOAD_CONF_ENTITY_EXPANSION_LIMIT("100000000"),

    OWL_API_WRITE_CONF_SAVE_IDS(false),
    OWL_API_WRITE_CONF_REMAP_IDS(true),
    OWL_API_WRITE_CONF_USE_NAMESPACE_ENTITIES(false),
    OWL_API_WRITE_CONF_INDENTING(true),
    OWL_API_WRITE_CONF_LABEL_AS_BANNER(false),
    OWL_API_WRITE_CONF_BANNERS_ENABLED(true),
    OWL_API_WRITE_CONF_INDENT_SIZE(4),;

    protected final Serializable secondary;
    protected static final ExtendedProperties PROPERTIES = loadProperties();

    OntSettings(Serializable value) {
        this.secondary = value;
    }

    @Override
    public Serializable getDefaultValue() {
        Serializable primary;
        String k = key();
        if (secondary instanceof Enum) {
            primary = PROPERTIES.getEnumProperty(k);
        } else if (secondary instanceof List) {
            List<?> list = PROPERTIES.getListProperty(k);
            primary = list == null ? new ArrayList<>() : list instanceof Serializable ? (Serializable) list : new ArrayList<>(list);
        } else if (secondary instanceof Boolean) {
            primary = PROPERTIES.getBooleanProperty(k);
        } else if (secondary instanceof Integer) {
            primary = PROPERTIES.getIntegerProperty(k);
        } else if (secondary instanceof Long) {
            primary = PROPERTIES.getLongProperty(k);
        } else if (secondary instanceof Double) {
            primary = PROPERTIES.getDoubleProperty(k);
        } else if (secondary instanceof String) {
            primary = PROPERTIES.getProperty(k);
        } else {
            throw new OntApiException("Unsupported value " + secondary.getClass());
        }
        return primary == null ? secondary : primary;
    }

    public String key() {
        return name().toLowerCase().replace("_", ".");
    }

    protected static ExtendedProperties loadProperties() {
        ExtendedProperties res = new ExtendedProperties();
        try (InputStream io = OntApiException.notNull(OntSettings.class.getResourceAsStream("/ontapi.properties"), "Null properties")) {
            res.load(io);
        } catch (IOException e) {
            throw new OntApiException("No properties", e);
        }
        return res;
    }
}
