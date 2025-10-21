package org.dspace.app.raid;

import org.apache.commons.cli.ParseException;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.Relationship;
import org.dspace.content.RelationshipType;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.RelationshipService;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.scripts.DSpaceRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Export OpenAIRE Project items to a RAiD JSON payload.
 */
public class RaidProjectExport extends DSpaceRunnable<RaidProjectExportConfiguration> {

    private static final Logger log = LoggerFactory.getLogger(RaidProjectExport.class);

    private static final ObjectWriter JSON = new ObjectMapper()
            .configure(SerializationFeature.INDENT_OUTPUT, true)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .writerWithDefaultPrettyPrinter();

    private final ConfigurationService cfg =
            DSpaceServicesFactory.getInstance().getConfigurationService();
    private final ItemService itemService =
            ContentServiceFactory.getInstance().getItemService();
    private final RelationshipService relationshipService = ContentServiceFactory.getInstance().getRelationshipService();

    @Override
    public RaidProjectExportConfiguration getScriptConfiguration() {
        return new RaidProjectExportConfiguration();
    }

    @Override
    public void setup() throws ParseException {
        log.info("RaidProjectExport.setup()");
    }

    @Override
    public void internalRun() throws Exception {
        log.info("RaidProjectExport.internalRun()");

        try (Context context = new Context()) {
            context.turnOffAuthorisationSystem();

            // Retrieve all OpenAIRE Project items
            String openaireType = prop("raid.openaire.entity.type", "Project");
            Iterator<Item> items = itemService.findAll(context);

            int exported = 0;
            while (items.hasNext()) {
                Item item = items.next();
                String entityType = itemService.getMetadataFirstValue(item, "dspace", "entity", "type", Item.ANY);
                if (entityType == null || !entityType.equalsIgnoreCase(openaireType)) {
                    continue;
                }
                exportItem(context, item);
                exported++;
            }

            context.complete();
            log.info("Exported {} OpenAIRE Project items", exported);
        } catch (Exception e) {
            log.error("Error during RAiD export", e);
        }

        log.info("RaidProjectExport done");
    }

    private void exportItem(Context context, Item item) throws Exception {
        log.info("Exporting project {}", item.getHandle());

        Map<String, Object> raid = new LinkedHashMap<>();
        raid.put("entityType", "Project");
        raid.put("source", "OpenAIRE");
        raid.put("dspaceHandle", item.getHandle());

        // Core fields
        raid.put("title", readMapped(item, "raid.openaire.map.title", "project.title"));
        raid.put("description", readMapped(item, "raid.openaire.map.description", "project.abstract"));
        raid.put("acronym", readMapped(item, "raid.openaire.map.acronym", "project.acronym"));
        raid.put("code", readMapped(item, "raid.openaire.map.code", "project.identifier.project-reference"));
        raid.put("startDate", readMapped(item, "raid.openaire.map.startDate", "project.startDate"));
        raid.put("endDate", readMapped(item, "raid.openaire.map.endDate", "project.endDate"));

        // Funding info
        Map<String, Object> funding = new LinkedHashMap<>();
        funding.put("grantId", readMapped(item, "raid.openaire.map.grantId", "project.identifier.grant-doi"));
        funding.put("funderName", readMapped(item, "raid.openaire.map.funderName", "project.funder"));
        raid.put("funding", funding);

        // Related entities
        raid.put("people", exportRelatedPeople(context, item));
        raid.put("products", exportRelatedProducts(context, item));

        // Write to file
        Path exportDir = Path.of(prop("raid.export.dir", "./"));
        Files.createDirectories(exportDir);
        String fn = (item.getHandle() == null ? "item-" + item.getID() : item.getHandle().replace('/', '_')) + ".raid.json";
        Path out = exportDir.resolve(fn);
        try (OutputStream os = Files.newOutputStream(out)) {
            JSON.writeValue(os, raid);
        }

        log.info("Exported project {} to {}", item.getHandle(), out);
    }

    private List<Map<String, Object>> exportRelatedPeople(Context context, Item project) throws Exception {
        List<Map<String, Object>> people = new LinkedList<>();
        List<Relationship> rels = relationshipService.findByItem(context, project);
        for (Relationship rel : rels) {
            RelationshipType type = rel.getRelationshipType();
            if (type == null) continue;

            Item related = (rel.getLeftItem().equals(project))
                    ? rel.getRightItem()
                    : rel.getLeftItem();

            String relatedType = itemService.getMetadataFirstValue(related, "dspace", "entity", "type", Item.ANY);
            if (relatedType == null || !relatedType.equalsIgnoreCase("Person")) {
                continue;
            }

            Map<String, Object> person = new LinkedHashMap<>();
            person.put("givenName", getMetadata(related, "person.givenName"));
            person.put("familyName", getMetadata(related, "person.familyName"));
            people.add(person);
        }
        return people;
    }

    private List<Map<String, Object>> exportRelatedProducts(Context context, Item project) throws Exception {
        List<Map<String, Object>> products = new LinkedList<>();
        List<Relationship> rels = relationshipService.findByItem(context, project);
        for (Relationship rel : rels) {
            RelationshipType type = rel.getRelationshipType();
            if (type == null) continue;

            Item related = (rel.getLeftItem().equals(project))
                    ? rel.getRightItem()
                    : rel.getLeftItem();

            String relatedType = itemService.getMetadataFirstValue(related, "dspace", "entity", "type", Item.ANY);
            if (relatedType == null || !(relatedType.equalsIgnoreCase("Product"))) {
                continue;
            }

            Map<String, Object> product = new LinkedHashMap<>();
            product.put("title", getMetadata(related, "dc.title"));
            product.put("type", getMetadata(related, "dc.type"));
            products.add(product);
        }
        return products;
    }

    private String getMetadata(Item item, String field) {
        String[] bits = field.split("\\.");
        String schema = bits[0];
        String element = bits.length > 1 ? bits[1] : null;
        String qualifier = bits.length > 2 ? bits[2] : null;
        List<MetadataValue> vals = itemService.getMetadata(item, schema, element, qualifier, Item.ANY);
        return (vals != null && !vals.isEmpty()) ? vals.get(0).getValue() : null;
    }


    private String prop(String key, String deflt) {
        String v = cfg.getProperty(key);
        return (v == null || v.isBlank()) ? deflt : v;
    }

    private String readMapped(Item item, String cfgKey, String defaultMd) {
        String mapping = cfg.getProperty(cfgKey);
        if (mapping == null || mapping.isBlank()) mapping = defaultMd;
        String[] bits = mapping.split("\\.");
        String schema = bits.length > 0 ? bits[0] : "dc";
        String element = bits.length > 1 ? bits[1] : "title";
        String qualifier = bits.length > 2 ? bits[2] : null;

        List<MetadataValue> vals = itemService.getMetadata(item, schema, element, qualifier, Item.ANY);
        if (vals == null || vals.isEmpty()) {
            log.debug("Missing metadata {} for item {}", mapping, item.getHandle());
            return null;
        }
        return vals.get(0).getValue();
    }
}