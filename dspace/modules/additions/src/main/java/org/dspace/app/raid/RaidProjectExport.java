package org.dspace.app.raid;

import org.apache.commons.cli.ParseException;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.scripts.DSpaceRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Export OpenAIRE Project items to a RAiD-ish JSON payload.
 */
public class RaidProjectExport extends DSpaceRunnable<RaidProjectExportConfiguration> {

    private static final Logger log = LoggerFactory.getLogger(RaidProjectExport.class);

    private final ConfigurationService cfg =
            DSpaceServicesFactory.getInstance().getConfigurationService();
    private final ItemService itemService =
            ContentServiceFactory.getInstance().getItemService();

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
                exportItem(item);
                exported++;
            }

            log.info("Exported {} OpenAIRE Project items", exported);
        } catch (Exception e) {
            log.error("Error during RAiD export", e);
        }

        log.info("RaidProjectExport done");
    }

    private void exportItem(Item item) throws IOException {
        log.info("Exporting item {}", item.getHandle());

        Map<String, Object> raid = new LinkedHashMap<>();
        raid.put("entityType", "Project");
        raid.put("source", "OpenAIRE");
        raid.put("dspaceHandle", item.getHandle());

        // Core fields
        raid.put("title", readMapped(item, "raid.openaire.map.title", "dc.title"));
        raid.put("description", readMapped(item, "raid.openaire.map.description", "dcterms.abstract"));
        raid.put("acronym", readMapped(item, "raid.openaire.map.acronym", "openaire.project.acronym"));
        raid.put("code", readMapped(item, "raid.openaire.map.code", "openaire.project.code"));

        // Dates
        raid.put("startDate", readMapped(item, "raid.openaire.map.startDate", "openaire.project.startDate"));
        raid.put("endDate", readMapped(item, "raid.openaire.map.endDate", "openaire.project.endDate"));

        // Funding
        Map<String, Object> funding = new LinkedHashMap<>();
        funding.put("grantId", readMapped(item, "raid.openaire.map.grantId", "openaire.project.grant"));
        funding.put("funderName", readMapped(item, "raid.openaire.map.funderName", "openaire.project.funder"));
        funding.put("funderId", readMapped(item, "raid.openaire.map.funderId", "openaire.project.funderId"));
        raid.put("funding", funding);

        // IDs
        Map<String, Object> ids = new LinkedHashMap<>();
        ids.put("piOrcid", readMapped(item, "raid.openaire.map.piOrcid", "openaire.project.pi.orcid"));
        ids.put("orgRor", readMapped(item, "raid.openaire.map.orgRor", "openaire.project.org.ror"));
        raid.put("ids", ids);

        // Serialize to JSON
        String json = Json.minimal(raid);

        // Write to file
        Path exportDir = Path.of(prop("raid.export.dir", "/dspace/var/exports/raid"));
        Files.createDirectories(exportDir);
        String fn = (item.getHandle() == null ? "item-" + item.getID() : item.getHandle().replace('/', '_')) + ".raid.json";
        Path out = exportDir.resolve(fn);
        try (OutputStream os = Files.newOutputStream(out)) {
            os.write(json.getBytes());
        }

        log.info("Exported OpenAIRE Project to file: {}", out);
    }

    // ---- helpers ----

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
        return (vals != null && !vals.isEmpty()) ? vals.get(0).getValue() : null;
    }

    /** tiny JSON helper */
    static class Json {
        static String minimal(Map<String, Object> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(esc(e.getKey())).append('"').append(':');
                Object v = e.getValue();
                if (v == null) sb.append("null");
                else if (v instanceof Number || v instanceof Boolean) sb.append(v.toString());
                else if (v instanceof Map) sb.append(minimal((Map<String, Object>) v));
                else sb.append('"').append(esc(String.valueOf(v))).append('"');
            }
            sb.append('}');
            return sb.toString();
        }
        private static String esc(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}