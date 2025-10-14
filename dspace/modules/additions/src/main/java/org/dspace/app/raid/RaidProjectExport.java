package org.dspace.app.raid;

import org.apache.commons.cli.ParseException;
import org.dspace.scripts.DSpaceRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RAID project export script.
 */
public class RaidProjectExport
        extends DSpaceRunnable<RaidProjectExportConfiguration> {

    private static final Logger log = LoggerFactory.getLogger(RaidProjectExport.class);

    @Override
    public RaidProjectExportConfiguration getScriptConfiguration() {
        return new RaidProjectExportConfiguration();
    }

    @Override
    public void setup() throws ParseException {
        log.info("RaidProjectExport.setup()");
        // you may parse args from configuration, if you want:
        // getScriptConfiguration().getOptions() etc.
    }

    @Override
    public void internalRun() throws Exception {
        log.info("RaidProjectExport.internalRun()");
        // Put your logic here
        for (int i = 0; i < 3; i++) {
            log.info("Iteration: {}", i);
        }
        log.info("RaidProjectExport done");
    }
}